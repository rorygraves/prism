package prism.server

import cats.effect.IO
import cats.syntax.all._
import prism.core.DeltaComputer
import prism.core.Types._

/** Routes requests and performs smart hydration of object references.
  *
  * The RequestRouter analyzes client state to determine the most efficient way to send object data:
  *   - Full object: Client doesn't have the object or version is too old
  *   - Delta: Client has an older version and delta is more efficient than full object
  *   - Cached: Client already has the current version
  *
  * Supports recursive hydration of nested object references up to a configurable depth.
  *
  * @param objectManager
  *   ObjectManager for accessing objects and client state
  */
class RequestRouter(val objectManager: ObjectManager) {

  /** Hydrate a single object reference based on client state.
    *
    * Analyzes what the client knows and returns the most efficient representation:
    *   - If client has current version: mark as cached
    *   - If client has older version and delta is efficient: return delta
    *   - Otherwise: return full object data
    *
    * @param clientId
    *   Client identifier
    * @param ref
    *   Object reference to hydrate
    * @return
    *   Hydrated reference with appropriate data/delta/cached marker
    */
  def hydrateReference(clientId: String, ref: ObjectReference): IO[HydratedReference] = {
    for {
      clientState <- objectManager.getClientState(clientId)
      currentObj <- objectManager.getObject(ref.id)

      result <- currentObj match {
        case None =>
          // Object doesn't exist
          IO.pure(HydratedReference(id = ref.id, version = 0, cached = false))

        case Some(obj) =>
          val subscription = clientState.getSubscription(ref.id)
          // Use filter from reference, or from subscription, or default
          val filterType = ref.filterType.orElse(subscription.flatMap(s => Some(s.filterType).filter(_ != "default")))
          val filterParams = subscription.flatMap(_.filterParams)

          for {
            filtered <- objectManager.applyFilter(obj, filterType, filterParams)
            clientVersion = clientState.getVersion(ref.id)

            hydrated <- clientVersion match {
              case Some(cv) if cv == filtered.version =>
                // Client has current version - cached
                IO.pure(HydratedReference(
                  id = filtered.id,
                  version = filtered.version,
                  cached = true
                ))

              case Some(cv) if cv < filtered.version =>
                // Client has older version - try delta
                for {
                  delta <- objectManager.getDelta(ref.id, cv, filtered.version, filterType)
                  result <- delta match {
                    case Some(d) if DeltaComputer.isDeltaEfficient(d, filtered) =>
                      // Delta is efficient
                      IO.pure(HydratedReference(
                        id = filtered.id,
                        version = filtered.version,
                        delta = Some(d)
                      ))
                    case _ =>
                      // Delta not efficient or not available - send full object
                      IO.pure(HydratedReference(
                        id = filtered.id,
                        version = filtered.version,
                        data = Some(filtered.data)
                      ))
                  }
                } yield result

              case _ =>
                // Client doesn't have this object - send full
                IO.pure(HydratedReference(
                  id = filtered.id,
                  version = filtered.version,
                  data = Some(filtered.data)
                ))
            }
          } yield hydrated
      }
    } yield result
  }

  /** Hydrate multiple object references.
    *
    * @param clientId
    *   Client identifier
    * @param refs
    *   List of object references to hydrate
    * @return
    *   List of hydrated references
    */
  def hydrateReferences(clientId: String, refs: List[ObjectReference]): IO[List[HydratedReference]] = {
    refs.traverse(ref => hydrateReference(clientId, ref))
  }

  /** Process a request with object references and optional auto-hydration.
    *
    * This is the main entry point for handling client requests. It:
    *   1. Hydrates the primary object references
    *   2. Optionally scans for nested ObjectReferences in the data
    *   3. Recursively hydrates nested references up to maxDepth
    *
    * @param clientId
    *   Client identifier
    * @param refs
    *   Primary object references from the request
    * @param options
    *   Request options (hydration settings)
    * @param currentDepth
    *   Current recursion depth (internal)
    * @return
    *   List of all hydrated references (primary + nested if enabled)
    */
  def processRequest(
      clientId: String,
      refs: List[ObjectReference],
      options: RequestOptions = RequestOptions.default,
      currentDepth: Int = 0
  ): IO[List[HydratedReference]] = {
    if (currentDepth > options.maxHydrationDepth) {
      // Exceeded max depth - return empty
      IO.pure(List.empty)
    } else {
      for {
        // Hydrate primary references
        hydrated <- hydrateReferences(clientId, refs)

        // If auto-hydration enabled, find nested references
        nested <- if (options.hydrateRefs && currentDepth < options.maxHydrationDepth) {
          val nestedRefs = extractNestedReferences(hydrated, options)
          if (nestedRefs.nonEmpty) {
            processRequest(clientId, nestedRefs, options, currentDepth + 1)
          } else {
            IO.pure(List.empty)
          }
        } else {
          IO.pure(List.empty)
        }

        // Optionally subscribe to nested references
        _ <- if (options.subscribeToRefs && nested.nonEmpty) {
          nested.traverse { hr =>
            objectManager.subscribe(
              clientId,
              hr.id,
              options.filterType.getOrElse("default"),
              None,
              ongoing = true
            )
          }.void
        } else {
          IO.unit
        }
      } yield hydrated ++ nested
    }
  }

  /** Extract nested ObjectReferences from hydrated data.
    *
    * Scans JSON data for objects that match the ObjectReference schema and extracts them for hydration.
    *
    * @param hydrated
    *   Hydrated references to scan
    * @param options
    *   Request options (default filter to apply)
    * @return
    *   List of nested object references found
    */
  private def extractNestedReferences(
      hydrated: List[HydratedReference],
      options: RequestOptions
  ): List[ObjectReference] = {
    hydrated.flatMap { hr =>
      hr.data match {
        case Some(data) => scanForReferences(data, options.filterType.getOrElse("default"))
        case None       => List.empty
      }
    }
  }

  /** Recursively scan JSON for ObjectReference-like objects.
    *
    * Looks for objects with "id" field and optionally "version", "filter", etc.
    *
    * @param value
    *   JSON value to scan
    * @param defaultFilter
    *   Default filter to apply to found references
    * @return
    *   List of object references found
    */
  private def scanForReferences(value: ujson.Value, defaultFilter: String): List[ObjectReference] = {
    value match {
      case obj: ujson.Obj =>
        // Check if this object is itself a reference
        val selfRef = if (obj.value.contains("id")) {
          val id = obj("id").strOpt.getOrElse("")
          if (id.nonEmpty) {
            val version = obj.value.get("version").flatMap(_.numOpt.map(_.toInt)).getOrElse(0)
            val filterType = obj.value.get("filterType").flatMap(_.strOpt).orElse(
              obj.value.get("filter").flatMap(_.strOpt)
            )
            val subscribe = obj.value.get("subscribe").flatMap(_.boolOpt).getOrElse(false)

            Some(ObjectReference(
              id = id,
              version = version,
              filterType = filterType,
              subscribe = subscribe
            ))
          } else {
            None
          }
        } else {
          None
        }

        // Recursively scan nested values
        val nestedRefs = obj.value.values.flatMap(v => scanForReferences(v, defaultFilter)).toList

        selfRef.toList ++ nestedRefs

      case arr: ujson.Arr =>
        // Scan array elements
        arr.value.flatMap(v => scanForReferences(v, defaultFilter)).toList

      case _ =>
        // Primitive value - no references
        List.empty
    }
  }

  /** Create temporary subscriptions for a one-time request.
    *
    * Subscribes to all referenced objects as temporary (will be cleared after response).
    *
    * @param clientId
    *   Client identifier
    * @param refs
    *   Object references to subscribe to
    * @param filterType
    *   Default filter to apply
    * @return
    *   Unit
    */
  def createTemporarySubscriptions(
      clientId: String,
      refs: List[ObjectReference],
      filterType: String = "default"
  ): IO[Unit] = {
    refs.traverse { ref =>
      objectManager.subscribe(
        clientId,
        ref.id,
        ref.filterType.getOrElse(filterType),
        None, // Filter params come from subscription
        ongoing = false // Temporary subscription
      )
    }.void
  }
}

object RequestRouter {

  /** Create a new RequestRouter.
    *
    * @param objectManager
    *   ObjectManager instance
    * @return
    *   A new RequestRouter
    */
  def create(objectManager: ObjectManager): RequestRouter = {
    new RequestRouter(objectManager)
  }
}
