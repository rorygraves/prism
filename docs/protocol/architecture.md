# Prism Architecture: Object Flow and Component Interactions

This document provides a detailed view of how objects flow through the Prism system from storage to client, designed to help teams implementing Prism in different languages (Python, Scala, Java, etc.) understand the complete architecture.

## Table of Contents

1. [System Overview](#system-overview)
2. [Component Architecture](#component-architecture)
3. [Object Flow Diagrams](#object-flow-diagrams)
4. [Storage Layer](#storage-layer)
5. [Object Manager](#object-manager)
6. [Request Router](#request-router)
7. [Client State Management](#client-state-management)
8. [Message Flow Sequences](#message-flow-sequences)
9. [Caching Strategy](#caching-strategy)
10. [Error Handling](#error-handling)

---

## System Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        Prism System                              │
│                                                                  │
│  Client Layer          Transport         Server Layer           │
│  ┌──────────┐         ┌────────┐        ┌──────────────┐       │
│  │ PrismClient│◄──────►│WebSocket│◄──────►│ObjectManager │       │
│  │  (Cache)  │         │        │        │  (Subscriptions)│     │
│  └──────────┘         └────────┘        └──────────────┘       │
│                                                  │               │
│                                                  ▼               │
│                                          ┌──────────────┐       │
│                                          │RequestRouter │       │
│                                          │(Hydration)   │       │
│                                          └──────────────┘       │
│                                                  │               │
│                                                  ▼               │
│                                          ┌──────────────┐       │
│                                          │BusinessHandler│      │
│                                          └──────────────┘       │
│                                                  │               │
│                                                  ▼               │
│                                          ┌──────────────┐       │
│                                          │Storage Adapter│      │
│                                          │(PostgreSQL)  │       │
│                                          └──────────────┘       │
└─────────────────────────────────────────────────────────────────┘
```

---

## Component Architecture

### Server-Side Components

#### 1. **Storage Adapter** (`storage/`)
**Responsibility**: Persist and retrieve versioned objects

**Interface** (language-agnostic):
```python
class StorageAdapter:
    async def get_current(object_id: str) -> PrismObject | None
    async def get_version(object_id: str, version: int) -> PrismObject | None
    async def save(obj: PrismObject) -> None
    async def list_versions(object_id: str) -> list[int]
```

**Implementation Notes**:
- Must support concurrent access
- Must guarantee version monotonicity
- Should support transactions for atomic updates
- Can use any backing store: PostgreSQL, MongoDB, Redis, etc.

#### 2. **Business Handler** (`handler.py`)
**Responsibility**: Process application-specific requests

**Interface**:
```python
class BusinessHandler:
    async def process(request_type: str, payload: dict) -> Any
```

**Returns**: Plain data structure with `ObjectReference` instances for objects

**Example Return**:
```python
{
    "message": ObjectReference(id="msg-123", version=1, subscribe=True),
    "user": ObjectReference(id="user-456", version=3)
}
```

#### 3. **Request Router** (`server/request_router.py`)
**Responsibility**: Smart reference hydration based on client state

**Key Features**:
- Extracts ObjectReferences from business handler response
- Checks client's known version for each reference
- Determines whether to send: full object, delta, or mark as cached
- Handles auto-subscription when `subscribe=True`
- Limits recursive reference resolution depth (default: 5)

**Algorithm**:
```
FOR each ObjectReference in response:
    client_version = client_state.get_version(ref.id)

    IF client_version is None:
        # Client doesn't have this object
        hydrated_objects.append(fetch_full_object(ref))

    ELSE IF client_version < ref.version:
        # Client has outdated version
        delta = compute_delta(client_version, ref.version)
        IF delta is efficient:
            hydrated_objects.append(delta)
        ELSE:
            hydrated_objects.append(fetch_full_object(ref))

    ELSE:
        # Client has current version
        hydrated_objects.append({id, version, cached: true})
```

#### 4. **Object Manager** (`server/object_manager.py`)
**Responsibility**: Manage subscriptions and object state

**Key Data Structures**:
```python
# Subscription tracking
subscriptions: dict[str, dict[str, SubscriptionInfo]]
# Format: {client_id: {object_id: SubscriptionInfo}}

class SubscriptionInfo:
    object_id: str
    filter_type: str
    filter_params: dict | None
    temporary: bool  # Auto-unsubscribe after first update
```

**Key Methods**:
1. `handle_subscribe(client_id, message)` - Register subscription
2. `handle_unsubscribe(client_id, object_id)` - Remove subscription
3. `handle_update_filter(client_id, message)` - Change filter on active subscription
4. `notify_object_updated(obj)` - Push update to all subscribers
5. `cleanup_client(client_id)` - Remove all subscriptions for disconnected client

**Three-Tier Caching**:
```python
version_cache: LRUCache[tuple[str, int], PrismObject]  # (id, version) -> object
delta_cache: LRUCache[str, Delta]  # "id:from:to" -> delta
filter_cache: LRUCache[str, PrismObject]  # "id:ver:filter:params" -> filtered_obj
```

#### 5. **Filter System** (`filters/`)
**Responsibility**: Transform objects before transmission

**Base Interface**:
```python
class Filter:
    def apply(obj: PrismObject, params: dict | None) -> PrismObject
    cacheable: bool  # Can results be cached?
    client_mutable: bool  # Can clients request this filter?
```

**Common Filters**:
- `FieldsFilter` - Include only specified fields
- `ExcludeFieldsFilter` - Exclude specified fields
- `SecurityFilter` - Remove sensitive data (server-enforced)

**Filter Application Order**:
1. Security filter (if configured) - always applied first
2. Client-requested filter (if specified)
3. Results cached with key: `{object_id}:{version}:{filter_type}:{params_hash}`

### Client-Side Components

#### 1. **PrismClient** (`client.ts`)
**Responsibility**: Manage WebSocket connection and client state

**Key State**:
```typescript
private cache: Map<string, {version: number, data: any}>
private subscriptions: Map<string, SubscriptionInfo>
private pendingRequests: Map<string, PendingRequest>
private watchers: Map<string, Array<(data: any) => void>>
```

**Core Methods**:
- `connect()` / `disconnect()` - WebSocket lifecycle
- `subscribe(objectId, filterType)` - Subscribe to object updates
- `unsubscribe(objectId)` - Cancel subscription
- `updateFilter(objectId, filterType, params)` - Change filter
- `request(type, payload, options)` - Execute business logic
- `watch(objectId, callback)` - Register update callback

**Automatic Reconnection**:
```typescript
// Exponential backoff: 1s, 2s, 4s, 8s, up to 30s
backoff = Math.min(1000 * Math.pow(2, attempt), 30000)
```

After reconnect, client automatically sends `sync` message with current state.

#### 2. **Vue Composables** (`prism-vue`)
**Responsibility**: Reactive Vue integration

**Composables**:
- `usePrismObject(objectId)` - Subscribe to single object
- `usePrismObjects(objectIds)` - Subscribe to multiple objects
- `usePrismRequest()` - Make requests
- `usePrismFilter(objectId)` - Update filters
- `usePrismConnection()` - Connection status

**Example**:
```typescript
const { data, loading, error } = usePrismObject('room-123')
// data is reactive ref that updates when object changes
```

---

## Object Flow Diagrams

### 1. Subscribe Flow

```
Client                  Transport       ObjectManager       Storage
  │                        │                  │                │
  │ subscribe(room-123)    │                  │                │
  ├───────────────────────►│                  │                │
  │                        │ handle_subscribe │                │
  │                        ├─────────────────►│                │
  │                        │                  │ get_current    │
  │                        │                  ├───────────────►│
  │                        │                  │ PrismObject    │
  │                        │                  │◄───────────────┤
  │                        │                  │                │
  │                        │                  │ apply_filter   │
  │                        │                  │ (cache check)  │
  │                        │                  │                │
  │                        │ FullObjectMsg    │                │
  │    FullObjectMsg       │◄─────────────────┤                │
  │◄───────────────────────┤                  │                │
  │                        │                  │                │
  │ [store in cache]       │                  │ [record sub]   │
  │                        │                  │                │
```

### 2. Object Update Flow (Delta)

```
Storage          ObjectManager       Subscribed Clients
  │                    │                     │
  │ save(new_version)  │                     │
  ├───────────────────►│                     │
  │                    │ notify_object_      │
  │                    │ updated()           │
  │                    │                     │
  │                    │ [For each subscriber:]
  │                    │                     │
  │                    │ Check client state  │
  │                    │ Compute delta       │
  │                    │ (or full if >70%)   │
  │                    │                     │
  │                    │   DeltaMsg          │
  │                    ├────────────────────►│
  │                    │                     │
  │                    │                     │ [apply delta]
  │                    │                     │ [notify watchers]
```

### 3. Request with Smart Hydration

```
Client         Transport    RequestRouter   BusinessHandler   Storage
  │                │              │                │             │
  │ request(       │              │                │             │
  │  createRoom)   │              │                │             │
  ├───────────────►│              │                │             │
  │                │ process()    │                │             │
  │                ├─────────────►│                │             │
  │                │              │ process()      │             │
  │                │              ├───────────────►│             │
  │                │              │                │ save()      │
  │                │              │                ├────────────►│
  │                │              │                │             │
  │                │              │ {room: Ref,    │             │
  │                │              │  creator: Ref} │             │
  │                │              │◄───────────────┤             │
  │                │              │                │             │
  │                │              │ [extract_refs] │             │
  │                │              │ [check client  │             │
  │                │              │  state]        │             │
  │                │              │                │             │
  │                │              │ fetch objects  │             │
  │                │              ├───────────────────────────  ►│
  │                │              │◄───────────────────────────  ┤
  │                │              │                │             │
  │                │ ResponseMsg  │                │             │
  │                │ + hydrated[] │                │             │
  │                │◄─────────────┤                │             │
  │  Response +    │              │                │             │
  │  hydrated[]    │              │                │             │
  │◄───────────────┤              │                │             │
  │                │              │                │             │
  │ [update cache] │              │                │             │
  │ [auto-subscribe│              │                │             │
  │  if requested] │              │                │             │
```

### 4. UpdateFilter Flow

```
Client              ObjectManager        FilterCache
  │                       │                    │
  │ updateFilter(         │                    │
  │  obj, filter, params) │                    │
  ├──────────────────────►│                    │
  │                       │ Check subscription │
  │                       │ exists             │
  │                       │                    │
  │                       │ Get current object │
  │                       │                    │
  │                       │ apply_filter()     │
  │                       ├───────────────────►│
  │                       │ [cache miss]       │
  │                       │                    │
  │                       │ [compute filtered] │
  │                       │ [cache result]     │
  │                       │                    │
  │                       │ filtered object    │
  │                       │◄───────────────────┤
  │                       │                    │
  │ FullObjectMsg         │                    │
  │ (filtered)            │                    │
  │◄──────────────────────┤                    │
  │                       │                    │
  │ [update cache]        │ [update sub info]  │
```

---

## Storage Layer

### Storage Adapter Interface

All storage implementations must implement this interface:

```python
class StorageAdapter(ABC):
    """Abstract base class for storage implementations."""

    @abstractmethod
    async def get_current(self, object_id: str) -> PrismObject | None:
        """Get the current (latest) version of an object."""
        pass

    @abstractmethod
    async def get_version(self, object_id: str, version: int) -> PrismObject | None:
        """Get a specific version of an object."""
        pass

    @abstractmethod
    async def save(self, obj: PrismObject) -> None:
        """Save a new version of an object."""
        pass

    @abstractmethod
    async def list_versions(self, object_id: str) -> list[int]:
        """List all version numbers for an object."""
        pass
```

### PostgreSQL Schema (Reference Implementation)

```sql
CREATE TABLE prism_objects (
    id VARCHAR(255) NOT NULL,
    version INTEGER NOT NULL,
    data JSONB NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id, version)
);

CREATE INDEX idx_prism_objects_id ON prism_objects(id);
CREATE INDEX idx_prism_objects_created_at ON prism_objects(created_at);
```

### Atomicity Requirements

**Critical**: Object saves must be atomic to prevent race conditions:

```python
# BAD - Race condition possible
current = await storage.get_current(object_id)
new_version = current.version + 1
new_obj = PrismObject(id=object_id, version=new_version, data=...)
await storage.save(new_obj)  # Another process might have saved version+1!

# GOOD - Use database constraints
new_obj = PrismObject(id=object_id, version=new_version, data=...)
try:
    await storage.save(new_obj)  # Primary key constraint ensures uniqueness
except IntegrityError:
    # Handle version conflict
    pass
```

---

## Object Manager

### Subscription Management

#### Data Structures

```python
class SubscriptionInfo:
    """Information about a client subscription."""
    object_id: str
    filter_type: str
    filter_params: dict[str, Any] | None
    temporary: bool  # Auto-unsubscribe after first notification
```

```python
# Primary subscription registry
subscriptions: dict[str, dict[str, SubscriptionInfo]]
# Format: {client_id: {object_id: SubscriptionInfo}}

# Reverse index for efficient object update notification
object_to_clients: dict[str, set[str]]
# Format: {object_id: {client_id1, client_id2, ...}}
```

#### Subscription Lifecycle

1. **Subscribe**:
   ```python
   # Add to primary registry
   subscriptions[client_id][object_id] = SubscriptionInfo(...)

   # Add to reverse index
   object_to_clients[object_id].add(client_id)

   # Fetch and send current object
   obj = await storage.get_current(object_id)
   filtered = await apply_filter(obj, filter_type, filter_params)
   await send_to_client(client_id, filtered)
   ```

2. **Unsubscribe**:
   ```python
   # Remove from primary registry
   del subscriptions[client_id][object_id]

   # Remove from reverse index
   object_to_clients[object_id].discard(client_id)
   ```

3. **Client Disconnect**:
   ```python
   # Clean up all subscriptions for client
   for object_id in subscriptions[client_id]:
       object_to_clients[object_id].discard(client_id)
   del subscriptions[client_id]
   ```

#### Update Notification Algorithm

```python
async def notify_object_updated(obj: PrismObject):
    """Notify all subscribers about object update."""

    # Get all subscribed clients
    client_ids = object_to_clients.get(obj.id, set())

    for client_id in client_ids:
        sub_info = subscriptions[client_id][obj.id]

        # Apply filter if specified
        if sub_info.filter_type:
            obj_to_send = await apply_filter(
                obj,
                sub_info.filter_type,
                sub_info.filter_params
            )
        else:
            obj_to_send = obj

        # Get client's current version
        client_version = client_state.get_version(client_id, obj.id)

        if client_version is None:
            # Client doesn't have object, send full
            await send_full_object(client_id, obj_to_send)

        elif client_version < obj.version:
            # Client has older version, try delta
            delta = compute_delta(client_version, obj.version)

            if is_delta_efficient(delta, obj_to_send):
                await send_delta(client_id, delta)
            else:
                await send_full_object(client_id, obj_to_send)

        # If temporary subscription, remove it
        if sub_info.temporary:
            await unsubscribe(client_id, obj.id)
```

### Filter Caching

```python
async def apply_filter_cached(
    obj: PrismObject,
    filter_type: str,
    filter_params: dict[str, Any] | None = None
) -> PrismObject:
    """Apply filter with caching."""

    # Build cache key
    cache_key = f"{obj.id}:{obj.version}:{filter_type}"
    if filter_params:
        params_str = json.dumps(filter_params, sort_keys=True)
        cache_key = f"{cache_key}:{params_str}"

    # Check cache
    cached = filter_cache.get(cache_key)
    if cached:
        return cached

    # Apply filter
    filtered = filters.apply(obj, filter_type, filter_params)

    # Cache result
    filter_cache.put(cache_key, filtered)

    return filtered
```

---

## Request Router

### Smart Reference Hydration

The Request Router is responsible for automatically resolving `ObjectReference` instances returned by business handlers.

#### Reference Extraction

```python
def extract_references(data: Any, max_depth: int = 5) -> list[ObjectReference]:
    """Recursively extract ObjectReferences from data structure."""

    if max_depth <= 0:
        return []

    references = []

    # Check if data is ObjectReference instance
    if isinstance(data, ObjectReference):
        references.append(data)
        return references

    # Check if data is dict with ObjectReference shape
    if isinstance(data, dict):
        if is_object_reference(data):
            try:
                ref = ObjectReference(**data)
                references.append(ref)
            except Exception:
                pass
        else:
            # Recursively process dict values
            for value in data.values():
                references.extend(
                    extract_references(value, max_depth - 1)
                )

    # Process lists
    elif isinstance(data, list):
        for item in data:
            references.extend(
                extract_references(item, max_depth - 1)
            )

    return references
```

#### Hydration Decision Logic

```python
async def hydrate_reference(
    ref: ObjectReference,
    client_state: ClientState
) -> HydratedObject:
    """Determine how to hydrate a reference based on client state."""

    client_version = client_state.get_version(ref.id)

    if client_version is None:
        # Client doesn't have object - send full
        obj = await storage.get_version(ref.id, ref.version)

        # Apply filter if specified
        if ref.filter_type:
            obj = await apply_filter(obj, ref.filter_type)

        return HydratedObject(
            id=ref.id,
            version=ref.version,
            data=obj.data,
            cached=False
        )

    elif client_version < ref.version:
        # Client has older version - try delta
        delta = await compute_delta(ref.id, client_version, ref.version)

        obj = await storage.get_version(ref.id, ref.version)

        if is_delta_efficient(delta, obj):
            return HydratedObject(
                id=ref.id,
                from_version=client_version,
                to_version=ref.version,
                patches=delta.patches,
                cached=False
            )
        else:
            # Delta not efficient, send full
            if ref.filter_type:
                obj = await apply_filter(obj, ref.filter_type)

            return HydratedObject(
                id=ref.id,
                version=ref.version,
                data=obj.data,
                cached=False
            )

    else:
        # Client has current version - just mark as cached
        return HydratedObject(
            id=ref.id,
            version=ref.version,
            cached=True
        )
```

#### Auto-Subscription

```python
async def process_auto_subscription(
    ref: ObjectReference,
    client_id: str,
    options: RequestOptions
):
    """Handle auto-subscription for references."""

    if ref.subscribe and options.subscribe_to_refs:
        # Automatically subscribe client to this object
        await object_manager.handle_subscribe(
            client_id,
            SubscribeMessage(
                object_id=ref.id,
                filter_type=ref.filter_type or "default",
                temporary=False
            )
        )
```

---

## Client State Management

### Client-Side Cache

```typescript
class PrismClient {
  private cache: Map<string, CachedObject> = new Map()

  interface CachedObject {
    version: number
    data: any
  }

  updateCache(id: string, version: number, data: any) {
    this.cache.set(id, { version, data })
  }

  getCached(id: string): CachedObject | undefined {
    return this.cache.get(id)
  }

  applyDelta(id: string, delta: Delta) {
    const cached = this.cache.get(id)
    if (!cached) {
      console.error(`Cannot apply delta: ${id} not in cache`)
      return
    }

    // Apply JSON Patch
    const patched = applyPatch(cached.data, delta.patches)
    this.updateCache(id, delta.to_version, patched)
  }
}
```

### Server-Side Client State Tracking

```python
class ClientState:
    """Track what each client knows."""

    def __init__(self):
        # client_id -> {object_id -> version}
        self.versions: dict[str, dict[str, int]] = {}

    def update_version(self, client_id: str, object_id: str, version: int):
        """Record that client has a specific version."""
        if client_id not in self.versions:
            self.versions[client_id] = {}
        self.versions[client_id][object_id] = version

    def get_version(self, client_id: str, object_id: str) -> int | None:
        """Get the version client has for an object."""
        return self.versions.get(client_id, {}).get(object_id)

    def cleanup(self, client_id: str):
        """Remove client state on disconnect."""
        self.versions.pop(client_id, None)
```

---

## Message Flow Sequences

### Complete Request Lifecycle

```
1. Client sends Request message
   ↓
2. Transport layer receives message
   ↓
3. RequestRouter.handle_request()
   - Creates ClientState snapshot
   - Calls BusinessHandler.process()
     ↓
4. BusinessHandler executes business logic
   - Validates input
   - Updates storage
   - Returns response with ObjectReferences
     ↓
5. RequestRouter.enhance_response()
   - Extracts all ObjectReferences (recursively)
   - For each reference:
     * Checks client's known version
     * Decides: full object | delta | cached
     * Fetches from storage if needed
     * Applies filters
     * Handles auto-subscription
   - Builds hydrated response
     ↓
6. Transport sends Response message
   - response.data (original response with refs)
   - response.hydrated[] (hydrated objects)
     ↓
7. Client receives Response
   - Updates cache with hydrated objects
   - Applies deltas to existing cached objects
   - Resolves references in response.data
   - Notifies watchers
   - Resolves promise
```

### Subscription Update Lifecycle

```
1. Object updated in storage
   ↓
2. Application calls object_manager.notify_object_updated(obj)
   ↓
3. ObjectManager looks up subscribers
   - Uses object_to_clients index
   ↓
4. For each subscribed client:
   - Gets subscription info (filter, params)
   - Applies filter (with caching)
   - Gets client's known version
   - Decides: full object | delta
   - Sends FullObjectMsg or DeltaMsg
     ↓
5. Client receives message
   - If FullObjectMsg: updates cache
   - If DeltaMsg: applies patches to cache
   - Notifies watchers (reactive updates)
```

### Reconnection and Sync

```
1. Client detects WebSocket disconnect
   ↓
2. Client waits with exponential backoff
   ↓
3. Client reconnects WebSocket
   ↓
4. Client sends Sync message
   - Lists all cached objects with versions
   ↓
5. Server processes Sync
   - For each object client has:
     * Gets current version from storage
     * If newer version exists:
       - Computes delta or sends full object
       - Sends update to client
   ↓
6. Server re-establishes subscriptions
   - Client includes subscription list in Sync
   - Server registers subscriptions
   ↓
7. Client receives updates
   - Applies all updates to cache
   - Notifies watchers
   - Client is now in sync
```

---

## Caching Strategy

### Server-Side Caches

#### 1. Version Cache
```python
version_cache: LRUCache[tuple[str, int], PrismObject]
max_size = 10000  # Configurable
```

**Purpose**: Avoid repeated database queries for same object version

**Key**: `(object_id, version)`

**Usage**: When fetching specific version for delta computation or hydration

#### 2. Delta Cache
```python
delta_cache: LRUCache[str, Delta]
max_size = 5000  # Configurable
```

**Purpose**: Avoid recomputing deltas between same versions

**Key**: `"{object_id}:{from_version}:{to_version}"`

**Usage**: Before computing delta, check if already computed

**Invalidation**: Never (deltas are immutable)

#### 3. Filter Cache
```python
filter_cache: LRUCache[str, PrismObject]
max_size = 5000  # Configurable
```

**Purpose**: Avoid reapplying filters to same object

**Key**: `"{object_id}:{version}:{filter_type}:{params_hash}"`

**Usage**: Before applying filter, check cache

**Invalidation**: LRU eviction only (filtered objects are immutable)

### Cache Coherency

**Critical**: Caches store immutable objects only

```python
# Objects are immutable - versions never change
PrismObject(id="room-123", version=5, data={...})

# New version = new object
PrismObject(id="room-123", version=6, data={...})

# Version 5 remains in cache - still valid!
```

This immutability means:
- No cache invalidation needed (except LRU eviction)
- No consistency issues
- Thread-safe reads
- Can cache indefinitely

---

## Error Handling

### Error Code Taxonomy

```python
class ErrorCode(str, Enum):
    # Client Errors (4xx equivalent)
    OBJECT_NOT_FOUND = "OBJECT_NOT_FOUND"  # Object doesn't exist
    NOT_SUBSCRIBED = "NOT_SUBSCRIBED"      # UpdateFilter on non-subscribed
    INVALID_MESSAGE = "INVALID_MESSAGE"    # Malformed protocol message
    INVALID_JSON = "INVALID_JSON"          # Not valid JSON
    VERSION_MISMATCH = "VERSION_MISMATCH"  # Client version conflict

    # Server Errors (5xx equivalent)
    INTERNAL_ERROR = "INTERNAL_ERROR"      # Server-side error
    STORAGE_ERROR = "STORAGE_ERROR"        # Database error
    FILTER_ERROR = "FILTER_ERROR"          # Filter application failed
```

### Error Message Format

```json
{
  "type": "error",
  "code": "OBJECT_NOT_FOUND",
  "message": "Object room-999 not found",
  "object_id": "room-999",
  "request_id": "req-123"  // If in response to request
}
```

### Error Handling Patterns

#### Server-Side

```python
async def handle_subscribe(client_id: str, msg: SubscribeMessage):
    try:
        obj = await storage.get_current(msg.object_id)
        if obj is None:
            await send_error(
                client_id,
                ErrorCode.OBJECT_NOT_FOUND,
                f"Object {msg.object_id} not found",
                object_id=msg.object_id
            )
            return

        # Process subscription...

    except Exception as e:
        logger.exception(f"Error in handle_subscribe: {e}")
        await send_error(
            client_id,
            ErrorCode.INTERNAL_ERROR,
            "Internal server error",
            object_id=msg.object_id
        )
```

#### Client-Side

```typescript
client.on('error', (error: ErrorMessage) => {
  switch (error.code) {
    case 'OBJECT_NOT_FOUND':
      // Object doesn't exist - may have been deleted
      console.warn(`Object ${error.object_id} not found`)
      break

    case 'NOT_SUBSCRIBED':
      // Tried to updateFilter on non-subscribed object
      console.error(`Not subscribed to ${error.object_id}`)
      break

    case 'INTERNAL_ERROR':
      // Server error - may need retry
      console.error('Server error:', error.message)
      break
  }
})
```

---

## Implementation Checklist for New Languages

When implementing Prism in a new language (Scala, Java, Go, etc.), ensure:

### Core Types
- [ ] `PrismObject` with id, version, data
- [ ] All message types (Subscribe, Request, Response, etc.)
- [ ] `ObjectReference` with subscribe flag
- [ ] `Delta` with JSON Patch operations

### Storage Layer
- [ ] `StorageAdapter` interface
- [ ] At least one implementation (PostgreSQL recommended)
- [ ] Atomic version saves
- [ ] Concurrent access support

### Server Components
- [ ] `ObjectManager` with subscription tracking
- [ ] `RequestRouter` with smart hydration
- [ ] `BusinessHandler` interface
- [ ] Filter system
- [ ] WebSocket transport

### Caching
- [ ] LRU cache implementation
- [ ] Version cache
- [ ] Delta cache
- [ ] Filter cache

### Client Library
- [ ] WebSocket client
- [ ] Client-side cache
- [ ] Delta application (JSON Patch)
- [ ] Automatic reconnection
- [ ] Subscription management

### Testing
- [ ] Unit tests for delta computation
- [ ] Unit tests for filters
- [ ] Unit tests for object manager
- [ ] Integration tests (client vs real server)
- [ ] E2E tests (multi-client scenarios)

### Documentation
- [ ] API documentation
- [ ] Architecture overview
- [ ] Examples
- [ ] Migration guide

---

## Performance Considerations

### 1. Database Queries

**Problem**: N+1 query problem when hydrating multiple references

**Solution**: Batch fetch objects

```python
# BAD
for ref in references:
    obj = await storage.get_version(ref.id, ref.version)  # N queries

# GOOD
object_ids = [ref.id for ref in references]
objects = await storage.batch_get(object_ids)  # 1 query
```

### 2. Delta Computation

**Problem**: Computing deltas on every notification is expensive

**Solution**: Use delta cache

```python
cache_key = f"{obj.id}:{from_version}:{to_version}"
cached_delta = delta_cache.get(cache_key)
if cached_delta:
    return cached_delta

delta = compute_delta(from_obj, to_obj)
delta_cache.put(cache_key, delta)
```

### 3. Filter Application

**Problem**: Applying filters repeatedly

**Solution**: Use filter cache with proper key

```python
# Include params in cache key
params_str = json.dumps(filter_params, sort_keys=True)
cache_key = f"{obj.id}:{obj.version}:{filter_type}:{params_str}"
```

### 4. Subscription Lookups

**Problem**: Finding all subscribers for an object is slow with just client→objects map

**Solution**: Maintain reverse index

```python
# Primary: client_id -> {object_id -> subscription}
subscriptions: dict[str, dict[str, SubscriptionInfo]]

# Reverse: object_id -> {client_id, client_id, ...}
object_to_clients: dict[str, set[str]]
```

### 5. WebSocket Message Batching

**Problem**: Sending many small messages is inefficient

**Solution**: Batch multiple updates

```python
# Instead of sending each update individually:
async def notify_objects_updated(objects: list[PrismObject]):
    updates_by_client: dict[str, list[Message]] = {}

    for obj in objects:
        for client_id in object_to_clients[obj.id]:
            msg = create_update_message(client_id, obj)
            updates_by_client[client_id].append(msg)

    # Send batched messages
    for client_id, messages in updates_by_client.items():
        await send_batch(client_id, messages)
```

---

## Security Considerations

### 1. Access Control

**Critical**: Verify permissions before operations

```python
async def handle_subscribe(client_id: str, msg: SubscribeMessage):
    # Check permission FIRST
    if not await permissions.can_access(client_id, msg.object_id):
        await send_error(
            client_id,
            ErrorCode.ACCESS_DENIED,
            f"Access denied to {msg.object_id}"
        )
        return

    # Then proceed with subscription
    ...
```

### 2. Filter Enforcement

**Critical**: Apply security filters before client filters

```python
async def apply_filter(obj: PrismObject, client_filter: str):
    # Security filter ALWAYS applied first
    obj = security_filter.apply(obj)

    # Then optional client filter
    if client_filter:
        obj = client_filters[client_filter].apply(obj)

    return obj
```

### 3. Rate Limiting

**Recommended**: Limit operations per client

```python
# Per-client rate limiters
request_limiter = RateLimiter(max_per_second=10)
subscription_limiter = RateLimiter(max_total=100)

async def handle_request(client_id: str, msg: RequestMessage):
    if not request_limiter.check(client_id):
        await send_error(client_id, ErrorCode.RATE_LIMITED, "Too many requests")
        return

    # Process request...
```

### 4. Reference Resolution Depth

**Critical**: Limit recursion to prevent DoS

```python
MAX_REFERENCE_DEPTH = 5

def extract_references(data: Any, max_depth: int = MAX_REFERENCE_DEPTH):
    if max_depth <= 0:
        return []  # Stop recursion

    # Process with max_depth - 1
    ...
```

---

## Appendix: Complete Type Definitions

### Python

```python
from pydantic import BaseModel
from typing import Any

class PrismObject(BaseModel):
    id: str
    version: int
    data: dict[str, Any]

class ObjectReference(BaseModel):
    id: str
    version: int
    filter_type: str | None = None
    subscribe: bool = False

class Delta(BaseModel):
    object_id: str
    from_version: int
    to_version: int
    patches: list[dict[str, Any]]  # JSON Patch operations

# Messages
class SubscribeMessage(BaseModel):
    type: str = "subscribe"
    object_id: str
    filter_type: str = "default"
    temporary: bool = False

class UnsubscribeMessage(BaseModel):
    type: str = "unsubscribe"
    object_id: str

class UpdateFilterMessage(BaseModel):
    type: str = "updateFilter"
    object_id: str
    filter_type: str
    filter_params: dict[str, Any] | None = None

class RequestMessage(BaseModel):
    type: str = "request"
    request_id: str
    request_type: str
    payload: dict[str, Any]
    options: dict[str, Any] | None = None

class FullObjectMessage(BaseModel):
    type: str = "fullObject"
    id: str
    version: int
    data: dict[str, Any]
    filtered: bool = False
    filter_type: str | None = None

class DeltaMessage(BaseModel):
    type: str = "delta"
    id: str
    from_version: int
    to_version: int
    patches: list[dict[str, Any]]

class ResponseMessage(BaseModel):
    type: str = "response"
    request_id: str
    success: bool
    data: Any = None
    hydrated: list[dict[str, Any]] = []

class ErrorMessage(BaseModel):
    type: str = "error"
    code: str
    message: str
    object_id: str | None = None
    request_id: str | None = None
```

### TypeScript

```typescript
interface PrismObject {
  id: string
  version: number
  data: Record<string, any>
}

interface ObjectReference {
  id: string
  version: number
  filterType?: string
  subscribe?: boolean
}

interface Delta {
  objectId: string
  fromVersion: number
  toVersion: number
  patches: JsonPatchOperation[]
}

// Messages
interface SubscribeMessage {
  type: 'subscribe'
  object_id: string
  filter_type?: string
  temporary?: boolean
}

interface UnsubscribeMessage {
  type: 'unsubscribe'
  object_id: string
}

interface UpdateFilterMessage {
  type: 'updateFilter'
  object_id: string
  filter_type: string
  filter_params?: Record<string, any>
}

interface RequestMessage {
  type: 'request'
  request_id: string
  request_type: string
  payload: Record<string, any>
  options?: RequestOptions
}

interface FullObjectMessage {
  type: 'fullObject'
  id: string
  version: number
  data: Record<string, any>
  filtered?: boolean
  filter_type?: string
}

interface DeltaMessage {
  type: 'delta'
  id: string
  from_version: number
  to_version: number
  patches: JsonPatchOperation[]
}

interface ResponseMessage {
  type: 'response'
  request_id: string
  success: boolean
  data?: any
  hydrated?: HydratedObject[]
}

interface ErrorMessage {
  type: 'error'
  code: string
  message: string
  object_id?: string
  request_id?: string
}
```

---

## Conclusion

This architecture document provides a complete view of how Prism works internally, from storage to client. Key takeaways for implementation teams:

1. **Immutability is fundamental** - versions never change, enabling aggressive caching
2. **Three-tier caching** - version cache, delta cache, filter cache
3. **Smart hydration** - server decides full object vs delta based on client state
4. **Subscription management** - maintain both forward and reverse indices
5. **Error handling** - clear error codes and graceful degradation
6. **Security first** - permissions checks and filter enforcement

For Scala implementers: Focus on immutable case classes, Akka/Pekko actors for concurrency, and PostgreSQL Slick for storage. The core algorithms translate directly to functional patterns.
