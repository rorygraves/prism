# Prism Cross-Language Implementation Guide

This guide is for teams implementing the Prism protocol in languages other than Python/TypeScript (e.g., Scala, Java, Go, Rust, C#).

## Table of Contents

1. [Quick Reference](#quick-reference)
2. [Core Algorithms](#core-algorithms)
3. [Critical Implementation Details](#critical-implementation-details)
4. [Testing Your Implementation](#testing-your-implementation)
5. [Language-Specific Guidance](#language-specific-guidance)
6. [Common Pitfalls](#common-pitfalls)
7. [Interoperability Testing](#interoperability-testing)

---

## Quick Reference

### Implementation Phases

**Phase 1 - Core Types** (1-2 days)
- [x] Define all message types
- [x] Implement `PrismObject` with id/version/data
- [x] Implement `ObjectReference`
- [x] Implement JSON Patch delta structures

**Phase 2 - Storage Layer** (2-3 days)
- [x] Define `StorageAdapter` interface
- [x] Implement PostgreSQL adapter
- [x] Add atomic version saves
- [x] Add concurrent access tests

**Phase 3 - Delta System** (2-3 days)
- [x] Implement JSON Patch computation
- [x] Implement delta application
- [x] Add efficiency check (delta < 70% of full object)
- [x] Add delta caching

**Phase 4 - Object Manager** (3-4 days)
- [x] Implement subscription tracking
- [x] Add forward and reverse indices
- [x] Implement filter system
- [x] Add filter caching
- [x] Implement notification system

**Phase 5 - Request Router** (2-3 days)
- [x] Implement reference extraction
- [x] Add smart hydration logic
- [x] Implement auto-subscription
- [x] Add depth limiting

**Phase 6 - WebSocket Transport** (2-3 days)
- [x] Implement WebSocket server
- [x] Add message routing
- [x] Add client tracking
- [x] Add connection lifecycle management

**Phase 7 - Client Library** (3-4 days)
- [x] Implement WebSocket client
- [x] Add client-side cache
- [x] Add delta application
- [x] Implement automatic reconnection
- [x] Add subscription management

**Phase 8 - Testing** (3-5 days)
- [x] Unit tests (delta, filters, caching)
- [x] Integration tests (client vs server)
- [x] Multi-client E2E tests
- [x] Performance tests
- [x] Interoperability tests vs Python implementation

---

## Core Algorithms

### 1. JSON Patch Delta Computation

**Input**: Two versions of the same object
**Output**: List of JSON Patch operations

**Algorithm**:
```python
def compute_delta(from_obj: PrismObject, to_obj: PrismObject) -> Delta:
    assert from_obj.id == to_obj.id
    assert from_obj.version < to_obj.version

    patches = json_patch.make_patch(from_obj.data, to_obj.data)

    return Delta(
        object_id=from_obj.id,
        from_version=from_obj.version,
        to_version=to_obj.version,
        patches=patches.to_list()
    )
```

**JSON Patch Library Requirements**:
- Must support RFC 6902
- Must generate minimal patches
- Common operations: `add`, `remove`, `replace`, `move`, `copy`

**Recommended Libraries**:
- **Python**: `jsonpatch`
- **TypeScript**: `fast-json-patch`
- **Scala**: `gnieh/diffson`
- **Java**: `java-json-tools/json-patch`
- **Go**: `evanphx/json-patch`
- **Rust**: `tricking/json-patch`
- **C#**: `Marvin.JsonPatch`

### 2. Delta Efficiency Check

**Critical**: Only use delta if significantly smaller than full object

```python
def is_delta_efficient(delta: Delta, full_object: PrismObject, threshold: float = 0.7) -> bool:
    """Check if delta is more efficient than full object."""

    delta_size = estimate_size(delta.patches)
    object_size = estimate_size(full_object.data)

    return delta_size < (object_size * threshold)

def estimate_size(data: Any) -> int:
    """Estimate serialized size in bytes."""
    return len(json.dumps(data, separators=(',', ':')))
```

**Why 70%?** Testing shows deltas are typically beneficial at this threshold. Configurable per deployment.

### 3. Reference Extraction (Recursive)

**Critical**: Must handle deeply nested structures without stack overflow

```python
def extract_references(
    data: Any,
    max_depth: int = 5,
    current_depth: int = 0
) -> list[ObjectReference]:
    """Recursively extract ObjectReferences from data."""

    if current_depth >= max_depth:
        return []  # Prevent infinite recursion

    references = []

    if isinstance(data, ObjectReference):
        # Already an ObjectReference instance
        references.append(data)

    elif is_dict_object_reference(data):
        # Dictionary with ObjectReference shape
        try:
            ref = ObjectReference.from_dict(data)
            references.append(ref)
        except:
            pass  # Not a valid reference

    elif isinstance(data, dict):
        # Recursively process dict values
        for value in data.values():
            refs = extract_references(value, max_depth, current_depth + 1)
            references.extend(refs)

    elif isinstance(data, list):
        # Recursively process list items
        for item in data:
            refs = extract_references(item, max_depth, current_depth + 1)
            references.extend(refs)

    return references

def is_dict_object_reference(data: Any) -> bool:
    """Check if dict has ObjectReference shape."""
    if not isinstance(data, dict):
        return False

    # Must have id and version
    return 'id' in data and 'version' in data
```

**Testing**: Include test case with 10-level nesting to verify depth limiting.

### 4. Hydration Decision Algorithm

**Core logic that makes Prism efficient**:

```python
async def hydrate_reference(
    ref: ObjectReference,
    client_version: int | None,
    storage: StorageAdapter
) -> HydratedObject:
    """Decide how to hydrate based on client state."""

    # CASE 1: Client doesn't have object
    if client_version is None:
        obj = await storage.get_version(ref.id, ref.version)
        return HydratedObject(
            id=ref.id,
            version=ref.version,
            data=obj.data,
            cached=False
        )

    # CASE 2: Client has outdated version
    elif client_version < ref.version:
        # Try delta first
        from_obj = await storage.get_version(ref.id, client_version)
        to_obj = await storage.get_version(ref.id, ref.version)
        delta = compute_delta(from_obj, to_obj)

        if is_delta_efficient(delta, to_obj):
            # Send delta
            return HydratedObject(
                id=ref.id,
                from_version=client_version,
                to_version=ref.version,
                patches=delta.patches,
                cached=False
            )
        else:
            # Delta not efficient, send full
            return HydratedObject(
                id=ref.id,
                version=ref.version,
                data=to_obj.data,
                cached=False
            )

    # CASE 3: Client has current version
    else:
        # Just mark as cached, no data needed
        return HydratedObject(
            id=ref.id,
            version=ref.version,
            cached=True
        )
```

**Performance Note**: Cache delta computations with key: `{object_id}:{from}:{to}`

### 5. Filter Application with Caching

```python
async def apply_filter_cached(
    obj: PrismObject,
    filter_type: str,
    filter_params: dict | None,
    filter_cache: LRUCache
) -> PrismObject:
    """Apply filter with caching."""

    # Build cache key including params
    cache_key = f"{obj.id}:{obj.version}:{filter_type}"
    if filter_params:
        # Deterministic params serialization
        params_str = json.dumps(filter_params, sort_keys=True)
        cache_key = f"{cache_key}:{params_str}"

    # Check cache
    cached = filter_cache.get(cache_key)
    if cached:
        return cached

    # Apply filter
    filtered = filter_registry.apply(obj, filter_type, filter_params)

    # Cache result (immutable objects = safe to cache forever)
    filter_cache.put(cache_key, filtered)

    return filtered
```

**Critical**: Include `filter_params` in cache key! Common bug to forget this.

---

## Critical Implementation Details

### 1. Object Immutability

**CRITICAL**: Objects MUST be immutable once created

```scala
// GOOD - immutable case class
case class PrismObject(
  id: String,
  version: Int,
  data: JsObject
) {
  // No mutation methods!
}

// BAD - mutable object
class PrismObject {
  var version: Int = _  // DON'T DO THIS
}
```

**Why?** Immutability enables:
- Aggressive caching (no invalidation needed)
- Thread-safe concurrent access
- Predictable behavior

### 2. Atomic Version Saves

**CRITICAL**: Prevent race conditions when saving new versions

```sql
-- Database constraint ensures atomicity
CREATE TABLE prism_objects (
    id VARCHAR(255) NOT NULL,
    version INTEGER NOT NULL,
    data JSONB NOT NULL,
    PRIMARY KEY (id, version)  -- Composite key prevents duplicate versions
);
```

```python
# Application code
try:
    await storage.save(new_object)
except IntegrityError:
    # Another process saved this version first
    # Handle conflict (retry, error, etc.)
    pass
```

**Testing**: Run concurrent saves of same object from multiple threads/processes. Should never save duplicate versions.

### 3. Subscription Indices

**CRITICAL**: Maintain both forward and reverse indices

```python
class SubscriptionManager:
    # Forward index: client_id -> {object_id -> SubscriptionInfo}
    subscriptions: dict[str, dict[str, SubscriptionInfo]]

    # Reverse index: object_id -> {client_id, client_id, ...}
    object_to_clients: dict[str, set[str]]

    def subscribe(self, client_id: str, object_id: str, info: SubscriptionInfo):
        # Update forward index
        if client_id not in self.subscriptions:
            self.subscriptions[client_id] = {}
        self.subscriptions[client_id][object_id] = info

        # Update reverse index
        if object_id not in self.object_to_clients:
            self.object_to_clients[object_id] = set()
        self.object_to_clients[object_id].add(client_id)

    def get_subscribers(self, object_id: str) -> set[str]:
        """Get all clients subscribed to object - O(1) with reverse index!"""
        return self.object_to_clients.get(object_id, set())
```

**Why?** Without reverse index, finding subscribers is O(n*m) where n=clients, m=subscriptions per client. With reverse index: O(1).

### 4. Client State Tracking

**CRITICAL**: Track what version each client has

```python
class ClientState:
    # client_id -> {object_id -> version}
    versions: dict[str, dict[str, int]]

    def update_version(self, client_id: str, object_id: str, version: int):
        if client_id not in self.versions:
            self.versions[client_id] = {}
        self.versions[client_id][object_id] = version

    def get_version(self, client_id: str, object_id: str) -> int | None:
        return self.versions.get(client_id, {}).get(object_id)
```

**When to Update**:
- After sending `FullObjectMessage` → update to sent version
- After sending `DeltaMessage` → update to `to_version`
- After receiving `SyncMessage` → update from client's reported state

### 5. Message Type Discrimination

**CRITICAL**: Handle all message types with type discrimination

```scala
// GOOD - sealed trait with pattern matching
sealed trait ClientMessage
case class SubscribeMessage(objectId: String, filterType: String) extends ClientMessage
case class RequestMessage(requestId: String, requestType: String, payload: JsObject) extends ClientMessage

def handleMessage(msg: ClientMessage): Unit = msg match {
  case SubscribeMessage(id, filter) => handleSubscribe(id, filter)
  case RequestMessage(id, reqType, payload) => handleRequest(id, reqType, payload)
}
```

**Testing**: Send malformed/unknown message types, verify graceful error handling.

### 6. WebSocket Connection Lifecycle

**CRITICAL**: Clean up resources on disconnect

```python
async def handle_disconnect(client_id: str):
    # Clean up subscriptions
    subscription_manager.cleanup_client(client_id)

    # Clean up client state
    client_state.cleanup(client_id)

    # Clean up pending requests
    pending_requests.cleanup(client_id)

    # Log disconnect
    logger.info(f"Client {client_id} disconnected, resources cleaned up")
```

**Testing**: Connect multiple clients, disconnect randomly, verify no memory leaks.

### 7. Error Handling

**CRITICAL**: Return structured errors, never crash

```python
try:
    obj = await storage.get_current(object_id)
    if obj is None:
        await send_error(
            client_id,
            code="OBJECT_NOT_FOUND",
            message=f"Object {object_id} not found",
            object_id=object_id
        )
        return

    # Process...

except Exception as e:
    logger.exception(f"Unexpected error: {e}")
    await send_error(
        client_id,
        code="INTERNAL_ERROR",
        message="Internal server error"
    )
```

**Never**: Let exceptions crash the WebSocket handler.

---

## Testing Your Implementation

### Unit Tests (Minimum Required)

#### 1. Delta Computation
```python
def test_compute_delta_basic():
    from_obj = PrismObject(id="obj1", version=1, data={"a": 1, "b": 2})
    to_obj = PrismObject(id="obj1", version=2, data={"a": 1, "b": 3, "c": 4})

    delta = compute_delta(from_obj, to_obj)

    assert delta.object_id == "obj1"
    assert delta.from_version == 1
    assert delta.to_version == 2
    assert len(delta.patches) >= 2  # At least replace 'b' and add 'c'
```

#### 2. Delta Application
```python
def test_apply_delta():
    obj = PrismObject(id="obj1", version=1, data={"a": 1, "b": 2})
    delta = Delta(
        object_id="obj1",
        from_version=1,
        to_version=2,
        patches=[
            {"op": "replace", "path": "/b", "value": 3},
            {"op": "add", "path": "/c", "value": 4}
        ]
    )

    result = apply_delta(obj, delta)

    assert result.version == 2
    assert result.data == {"a": 1, "b": 3, "c": 4}
```

#### 3. Reference Extraction
```python
def test_extract_nested_references():
    data = {
        "user": {"id": "user-1", "version": 2},
        "messages": [
            {"id": "msg-1", "version": 1},
            {"id": "msg-2", "version": 1}
        ]
    }

    refs = extract_references(data)

    assert len(refs) == 3
    assert {r.id for r in refs} == {"user-1", "msg-1", "msg-2"}
```

#### 4. Subscription Management
```python
def test_subscription_tracking():
    manager = SubscriptionManager()

    manager.subscribe("client1", "obj1", SubscriptionInfo(...))
    manager.subscribe("client2", "obj1", SubscriptionInfo(...))

    subscribers = manager.get_subscribers("obj1")

    assert subscribers == {"client1", "client2"}
```

#### 5. Filter Caching
```python
def test_filter_cache_with_params():
    cache = LRUCache(max_size=100)
    obj = PrismObject(id="obj1", version=1, data={"a": 1, "b": 2, "c": 3})

    # Apply with different params
    result1 = apply_filter_cached(obj, "fields", {"fields": ["a", "b"]}, cache)
    result2 = apply_filter_cached(obj, "fields", {"fields": ["a", "c"]}, cache)

    assert result1.data == {"a": 1, "b": 2}
    assert result2.data == {"a": 1, "c": 3}

    # Apply same params - should hit cache
    result3 = apply_filter_cached(obj, "fields", {"fields": ["a", "b"]}, cache)
    assert result3.data == {"a": 1, "b": 2}
    # Verify cache hit (implementation-specific)
```

### Integration Tests

**Test client vs real server** - no mocks!

```python
async def test_subscribe_and_receive_update():
    # Start real server
    server = PrismServer(storage=PostgresStorage(...))
    await server.start()

    # Connect real client
    client = PrismClient("ws://localhost:8000")
    await client.connect()

    updates = []
    client.watch("obj-1", lambda data: updates.append(data))

    # Subscribe
    await client.subscribe("obj-1")

    # Trigger update on server
    new_obj = PrismObject(id="obj-1", version=2, data={"value": "updated"})
    await server.storage.save(new_obj)
    await server.object_manager.notify_object_updated(new_obj)

    # Wait for update
    await wait_for(lambda: len(updates) > 0, timeout=5.0)

    assert updates[0]["value"] == "updated"
```

### Multi-Client E2E Tests

**Test real-time synchronization**:

```python
async def test_multi_client_sync():
    server = PrismServer(...)
    await server.start()

    # Connect two clients
    client1 = PrismClient("ws://localhost:8000")
    client2 = PrismClient("ws://localhost:8000")
    await client1.connect()
    await client2.connect()

    # Both subscribe to same object
    updates1 = []
    updates2 = []
    client1.watch("room-1", lambda data: updates1.append(data))
    client2.watch("room-1", lambda data: updates2.append(data))

    await client1.subscribe("room-1")
    await client2.subscribe("room-1")

    # Client 1 makes a request that updates the room
    await client1.request("createMessage", {"room_id": "room-1", "content": "Hi!"})

    # Both clients should receive update
    await wait_for(lambda: len(updates1) > 0 and len(updates2) > 0, timeout=5.0)

    assert updates1[-1] == updates2[-1]  # Same data
```

---

## Language-Specific Guidance

### Scala Implementation

**Recommended Stack**:
- **Server**: Akka HTTP or Play Framework
- **JSON**: circe or play-json
- **JSON Patch**: gnieh/diffson
- **Database**: Slick
- **Concurrency**: Akka actors or FS2

**Example Core Types**:
```scala
case class PrismObject(
  id: String,
  version: Int,
  data: JsObject
)

case class ObjectReference(
  id: String,
  version: Int,
  filterType: Option[String] = None,
  subscribe: Boolean = false
)

sealed trait ClientMessage
case class SubscribeMessage(
  objectId: String,
  filterType: String = "default",
  temporary: Boolean = false
) extends ClientMessage
```

**Akka Actor Pattern**:
```scala
class SubscriptionActor extends Actor {
  // Forward index
  var subscriptions: Map[String, Map[String, SubscriptionInfo]] = Map.empty

  // Reverse index
  var objectToClients: Map[String, Set[String]] = Map.empty

  def receive = {
    case Subscribe(clientId, objectId, info) =>
      subscriptions = subscriptions.updated(
        clientId,
        subscriptions.getOrElse(clientId, Map.empty) + (objectId -> info)
      )
      objectToClients = objectToClients.updated(
        objectId,
        objectToClients.getOrElse(objectId, Set.empty) + clientId
      )

    case ObjectUpdated(obj) =>
      val clients = objectToClients.getOrElse(obj.id, Set.empty)
      clients.foreach { clientId =>
        // Send update to client
      }
  }
}
```

### Java Implementation

**Recommended Stack**:
- **Server**: Spring Boot with WebSocket support
- **JSON**: Jackson
- **JSON Patch**: java-json-tools/json-patch
- **Database**: JPA/Hibernate or jOOQ
- **Concurrency**: CompletableFuture, Virtual Threads (Java 21+)

**Example**:
```java
@Data
public class PrismObject {
    private final String id;
    private final int version;
    private final Map<String, Object> data;
}

@Service
public class ObjectManager {
    private final Map<String, Map<String, SubscriptionInfo>> subscriptions =
        new ConcurrentHashMap<>();

    private final Map<String, Set<String>> objectToClients =
        new ConcurrentHashMap<>();

    public CompletableFuture<Void> subscribe(
        String clientId,
        String objectId,
        SubscriptionInfo info
    ) {
        subscriptions
            .computeIfAbsent(clientId, k -> new ConcurrentHashMap<>())
            .put(objectId, info);

        objectToClients
            .computeIfAbsent(objectId, k -> ConcurrentHashMap.newKeySet())
            .add(clientId);

        return fetchAndSendObject(clientId, objectId);
    }
}
```

### Go Implementation

**Recommended Stack**:
- **Server**: Gorilla WebSocket or net/http
- **JSON**: encoding/json
- **JSON Patch**: evanphx/json-patch
- **Database**: pgx (PostgreSQL) or GORM
- **Concurrency**: Goroutines and channels

**Example**:
```go
type PrismObject struct {
    ID      string                 `json:"id"`
    Version int                    `json:"version"`
    Data    map[string]interface{} `json:"data"`
}

type SubscriptionManager struct {
    mu               sync.RWMutex
    subscriptions    map[string]map[string]*SubscriptionInfo
    objectToClients  map[string]map[string]bool
}

func (sm *SubscriptionManager) Subscribe(
    clientID string,
    objectID string,
    info *SubscriptionInfo,
) error {
    sm.mu.Lock()
    defer sm.mu.Unlock()

    if _, ok := sm.subscriptions[clientID]; !ok {
        sm.subscriptions[clientID] = make(map[string]*SubscriptionInfo)
    }
    sm.subscriptions[clientID][objectID] = info

    if _, ok := sm.objectToClients[objectID]; !ok {
        sm.objectToClients[objectID] = make(map[string]bool)
    }
    sm.objectToClients[objectID][clientID] = true

    return nil
}
```

---

## Common Pitfalls

### 1. Forgetting Filter Params in Cache Key

**Bug**:
```python
cache_key = f"{obj.id}:{obj.version}:{filter_type}"  # Missing params!
```

**Result**: Same object with different filter params returns wrong cached result

**Fix**:
```python
if filter_params:
    params_str = json.dumps(filter_params, sort_keys=True)
    cache_key = f"{obj.id}:{obj.version}:{filter_type}:{params_str}"
```

### 2. Not Limiting Reference Resolution Depth

**Bug**:
```python
def extract_references(data):
    if isinstance(data, dict):
        for value in data.values():
            extract_references(value)  # No depth limit!
```

**Result**: Stack overflow on circular references or deep nesting

**Fix**:
```python
def extract_references(data, max_depth=5):
    if max_depth <= 0:
        return []
    # Process with max_depth - 1
```

### 3. Race Condition on Version Save

**Bug**:
```python
current = await storage.get_current(obj_id)
new_version = current.version + 1
await storage.save(PrismObject(id=obj_id, version=new_version, ...))
```

**Result**: Two concurrent requests can save same version number

**Fix**: Use database constraints (PRIMARY KEY on (id, version))

### 4. Not Cleaning Up on Disconnect

**Bug**:
```python
async def handle_disconnect(client_id):
    # Close WebSocket
    await ws.close()
    # That's it? NO! Forgot cleanup!
```

**Result**: Memory leak - subscriptions and state never cleaned

**Fix**:
```python
async def handle_disconnect(client_id):
    subscription_manager.cleanup_client(client_id)
    client_state.cleanup(client_id)
    await ws.close()
```

### 5. Sending Delta for Object Not in Client Cache

**Bug**:
```python
# Always send delta if client has any version
if client_version is not None:
    send_delta(client_id, delta)
```

**Result**: Client can't apply delta if it doesn't have the base version

**Fix**:
```python
if client_version == delta.from_version:
    send_delta(client_id, delta)
else:
    send_full_object(client_id, obj)
```

### 6. Not Updating Client State After Sending Objects

**Bug**:
```python
await send_full_object(client_id, obj)
# Forgot to update client state!
```

**Result**: Server thinks client doesn't have object, keeps sending full objects

**Fix**:
```python
await send_full_object(client_id, obj)
client_state.update_version(client_id, obj.id, obj.version)
```

---

## Interoperability Testing

### Testing Against Python Reference Implementation

**Setup**:
1. Run Python server: `cd backend && poetry run python -m chat_demo.main`
2. Connect your client implementation
3. Verify all protocol features work

**Test Scenarios**:

```python
# 1. Basic Subscribe
your_client.connect("ws://localhost:8000")
your_client.subscribe("global-room-list")
# Verify you receive FullObjectMessage

# 2. Request with Hydration
result = your_client.request("createUser", {
    "username": "testuser",
    "display_name": "Test User"
})
# Verify response has hydrated user object

# 3. Delta Updates
your_client.subscribe("room-123")
# Server updates room (add member)
# Verify you receive DeltaMessage
# Verify you can apply delta correctly

# 4. UpdateFilter
your_client.subscribe("room-123", "default")
your_client.updateFilter("room-123", "fields", {"fields": ["name"]})
# Verify you receive filtered object

# 5. Multi-Client Sync
client1 = your_client.connect()
client2 = your_client.connect()
client1.subscribe("room-123")
client2.subscribe("room-123")
client1.request("createMessage", {...})
# Verify both clients receive update
```

### Testing Your Server Against TypeScript Client

**Setup**:
1. Run your server implementation
2. Use TypeScript client: `frontend/packages/prism-client`
3. Run integration tests

```bash
cd frontend/integration-tests
# Point tests to your server
export PRISM_SERVER_URL="ws://localhost:YOUR_PORT"
npm test
```

**Expected**: All 17 integration tests should pass

---

## Appendix: Reference Implementations

### Python (Reference)
- **Location**: `/backend`
- **Key Files**:
  - `prism/core/delta.py` - Delta computation
  - `prism/server/object_manager.py` - Subscription management
  - `prism/server/request_router.py` - Smart hydration
  - `prism/storage/postgres.py` - Storage implementation

### TypeScript Client (Reference)
- **Location**: `/frontend/packages/prism-client`
- **Key Files**:
  - `src/client.ts` - WebSocket client
  - `src/types.ts` - Type definitions

### Scala (Outline)
- **Location**: `/scala`
- **Status**: Type definitions and examples only
- **Next Steps**: Full implementation following this guide

---

## Getting Help

1. **Review Architecture Doc**: See `docs/architecture.md` for detailed flow diagrams
2. **Check Python Reference**: `backend/prism/` for working implementation
3. **Review Tests**: `backend/tests/` for expected behavior
4. **Run Integration Tests**: Verify compatibility with reference implementation

---

## Checklist for Completion

Server Implementation:
- [ ] All message types defined
- [ ] Storage adapter with atomic saves
- [ ] Delta computation and application
- [ ] Subscription manager with indices
- [ ] Filter system with caching
- [ ] Request router with smart hydration
- [ ] WebSocket transport
- [ ] Error handling
- [ ] Connection lifecycle management

Client Implementation:
- [ ] WebSocket client
- [ ] Client-side cache
- [ ] Delta application
- [ ] Automatic reconnection
- [ ] Subscription management
- [ ] Request/response handling

Testing:
- [ ] Unit tests for delta (8+)
- [ ] Unit tests for filters (5+)
- [ ] Unit tests for subscriptions (5+)
- [ ] Integration tests vs real server (10+)
- [ ] Multi-client E2E tests (3+)
- [ ] Interoperability tests vs Python (all passing)

Documentation:
- [ ] API documentation
- [ ] Setup guide
- [ ] Architecture overview
- [ ] Examples

Performance:
- [ ] LRU caches implemented
- [ ] Delta efficiency check
- [ ] Filter caching
- [ ] Subscription indices
- [ ] Load tested (1000+ concurrent clients)

---

**Good luck with your implementation! The Prism community is here to help.**
