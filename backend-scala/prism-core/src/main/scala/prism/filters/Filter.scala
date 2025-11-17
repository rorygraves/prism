package prism.filters

import prism.core.Types.PrismObject

/** Base trait for object filters.
  *
  * Filters transform objects before transmission, serving dual purposes:
  *   - Security: Server-enforced filters limiting data exposure
  *   - Optimization: Client-requested filters reducing payload size
  */
trait Filter {

  /** Unique filter name */
  def name: String

  /** Whether filtered results can be cached */
  def cacheable: Boolean

  /** Whether clients can request this filter */
  def clientMutable: Boolean

  /** Apply filter to an object.
    *
    * @param obj
    *   The object to filter
    * @param params
    *   Optional parameters for the filter
    * @return
    *   Filtered PrismObject (same ID and version, transformed data)
    */
  def apply(obj: PrismObject, params: Option[Map[String, ujson.Value]] = None): PrismObject
}

/** Filter that wraps a simple transformation function. */
class FunctionFilter(
    val name: String,
    transform: (ujson.Value, Option[Map[String, ujson.Value]]) => ujson.Value,
    val cacheable: Boolean = true,
    val clientMutable: Boolean = false
) extends Filter {

  override def apply(obj: PrismObject, params: Option[Map[String, ujson.Value]] = None): PrismObject = {
    val filteredData = transform(obj.data, params)
    PrismObject(id = obj.id, version = obj.version, data = filteredData)
  }
}

/** Registry for managing available filters. */
class FilterRegistry {
  private val filters = scala.collection.mutable.Map[String, Filter]()

  /** Register a filter.
    *
    * @param filter
    *   The filter to register
    * @throws IllegalArgumentException
    *   if a filter with this name already exists
    */
  def register(filter: Filter): Unit = {
    if (filters.contains(filter.name)) {
      throw new IllegalArgumentException(s"Filter '${filter.name}' already registered")
    }
    filters(filter.name) = filter
  }

  /** Register a simple function as a filter.
    *
    * @param name
    *   Unique filter name
    * @param transform
    *   Transformation function
    * @param cacheable
    *   Whether results can be cached
    * @param clientMutable
    *   Whether clients can request this filter
    */
  def registerFunction(
      name: String,
      transform: (ujson.Value, Option[Map[String, ujson.Value]]) => ujson.Value,
      cacheable: Boolean = true,
      clientMutable: Boolean = false
  ): Unit = {
    val filter = new FunctionFilter(name, transform, cacheable, clientMutable)
    register(filter)
  }

  /** Get a filter by name.
    *
    * @param name
    *   Filter name
    * @return
    *   The requested filter
    * @throws NoSuchElementException
    *   if filter not found
    */
  def get(name: String): Filter = {
    filters.getOrElse(name, throw new NoSuchElementException(s"Filter '$name' not found"))
  }

  /** Check if a filter exists. */
  def has(name: String): Boolean = filters.contains(name)

  /** Apply a filter to an object.
    *
    * @param obj
    *   The object to filter
    * @param filterName
    *   Name of the filter to apply
    * @param params
    *   Optional filter parameters
    * @return
    *   Filtered object
    */
  def apply(
      obj: PrismObject,
      filterName: String,
      params: Option[Map[String, ujson.Value]] = None
  ): PrismObject = {
    val filter = get(filterName)
    filter.apply(obj, params)
  }

  /** Get all registered filter names. */
  def listFilters: List[String] = filters.keys.toList.sorted
}
