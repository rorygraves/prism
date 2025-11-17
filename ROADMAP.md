# Prism Development Roadmap

This document outlines the current status, planned features, and development priorities for the Prism protocol implementation.

**Last Updated**: 2025-11-17
**Current Version**: Python/TypeScript Reference Implementation Complete
**Scala Implementation**: In Progress (Separate Team)

---

## Table of Contents

1. [Current Status](#current-status)
2. [Immediate Priorities](#immediate-priorities)
3. [Short-Term Roadmap (1-3 months)](#short-term-roadmap-1-3-months)
4. [Medium-Term Roadmap (3-6 months)](#medium-term-roadmap-3-6-months)
5. [Long-Term Vision (6+ months)](#long-term-vision-6-months)
6. [Feature Requests & Ideas](#feature-requests--ideas)
7. [For Scala Implementation Team](#for-scala-implementation-team)
8. [Breaking Changes Policy](#breaking-changes-policy)

---

## Current Status

### ✅ Completed (Reference Implementation)

**Core Protocol** (v1.0)
- [x] Versioned objects with immutability
- [x] JSON Patch delta computation
- [x] Delta efficiency checking (70% threshold)
- [x] Object references
- [x] Smart reference hydration
- [x] Filter system with caching
- [x] UpdateFilter for dynamic filter changes
- [x] Subscription management
- [x] WebSocket transport
- [x] Automatic reconnection
- [x] Error handling with structured error codes

**Python Backend**
- [x] Core library (`prism/`)
- [x] Object Manager with three-tier caching
- [x] Request Router with smart hydration
- [x] PostgreSQL storage adapter
- [x] Filter system (fields, exclude_fields, custom)
- [x] Chat demo implementation
- [x] 47 unit tests (48% coverage, 82-100% on core modules)

**TypeScript/Vue Frontend**
- [x] Full-featured client library
- [x] Delta application
- [x] Client-side caching
- [x] Vue 3 composables (6 composables)
- [x] Chat demo application
- [x] 19 unit tests
- [x] 17 integration tests (client vs real server)

**Testing & Documentation**
- [x] Comprehensive unit tests (66 total)
- [x] Integration tests (17 tests)
- [x] E2E tests (5 Playwright scenarios)
- [x] Protocol specification
- [x] Architecture guide with diagrams
- [x] Cross-language implementation guide
- [x] Development scripts and automation

---

## Immediate Priorities

### 1. Vue Integration Enhancements (1-2 weeks)

**Pinia Store Integration**
- [ ] Create example Pinia store with Prism integration
- [ ] Show how multiple components can subscribe to same object
- [ ] Demonstrate reactive updates across component tree
- [ ] Add to documentation with working example

**Example**:
```typescript
// stores/rooms.ts
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { usePrismObject } from '@prism/vue'

export const useRoomsStore = defineStore('rooms', () => {
  const { data: roomList } = usePrismObject('global-room-list')

  const createRoom = async (name: string) => {
    // Create room logic
  }

  return { roomList, createRoom }
})

// In any component:
const rooms = useRoomsStore()
// rooms.roomList is reactive across all components
```

**Priority**: HIGH (requested by user)

### 2. Version Synchronization Helpers for Testing (1 week)

**Problem**: Frontend tests need to wait until specific versions are processed

**Solution**: Add version tracking and waiting utilities

```typescript
// New API
interface PrismClient {
  waitForVersion(objectId: string, version: number, timeout?: number): Promise<void>
  waitForVersions(refs: Array<{id: string, version: number}>, timeout?: number): Promise<void>
  getProcessedVersion(objectId: string): number | undefined
}

// Usage in tests
const response = await client.request('createRoom', {...})
await client.waitForVersion(response.room.id, response.room.version)
// Now we can reliably test that the room is in the store
```

**Implementation Details**:
- Track processed versions separately from cache versions
- Emit events when versions are fully processed (cache updated, watchers called)
- Provide Promise-based API for waiting
- Add timeout with clear error messages

**Priority**: HIGH (requested by user, critical for reliable testing)

### 3. npm package-lock.json (Completed ✅)

- [x] Added package-lock.json to e2e directory
- [x] Allows `npm ci` to work correctly

---

## Short-Term Roadmap (1-3 months)

### 1. Enhanced Vue Composables

**Multi-Object Subscribe with Filters**
```typescript
const { data: filteredRooms } = usePrismObjects(
  ['room-1', 'room-2', 'room-3'],
  { filterType: 'summary', filterParams: { fields: ['name', 'memberCount'] } }
)
```

**Optimistic Updates**
```typescript
const { execute, optimisticUpdate } = usePrismRequest()

async function createMessage(content: string) {
  // Immediately update UI
  optimisticUpdate('room-123', (data) => ({
    ...data,
    messages: [...data.messages, { id: 'temp', content, pending: true }]
  }))

  // Send request
  await execute('createMessage', { content })
  // Automatic revert if request fails
}
```

**Subscription Lifecycle Management**
```typescript
const { data, unsubscribe } = usePrismObject('room-123', {
  autoUnsubscribe: true  // Unsubscribe when component unmounts
})
```

### 2. Batch Operations API

**Problem**: Creating multiple objects requires multiple round trips

**Solution**: Batch API

```python
# Server
class BatchRequest(BaseModel):
    operations: list[Operation]

class Operation(BaseModel):
    operation_id: str  # Client-assigned ID
    request_type: str
    payload: dict[str, Any]

class BatchResponse(BaseModel):
    results: list[OperationResult]

class OperationResult(BaseModel):
    operation_id: str
    success: bool
    data: Any | None
    error: str | None
```

**Client**:
```typescript
const results = await client.batchRequest([
  { operation_id: 'create-user', request_type: 'createUser', payload: {...} },
  { operation_id: 'create-room', request_type: 'createRoom', payload: {...} }
])
```

**Benefits**:
- Reduced latency (1 round trip vs N)
- Atomic operations (all or nothing)
- Automatic reference resolution across operations

### 3. MongoDB Storage Adapter

**Why**: Many teams use MongoDB

**Implementation**:
```python
class MongoStorageAdapter(StorageAdapter):
    def __init__(self, mongo_uri: str, database: str):
        self.client = motor.motor_asyncio.AsyncIOMotorClient(mongo_uri)
        self.db = self.client[database]
        self.collection = self.db['prism_objects']

    async def get_current(self, object_id: str) -> PrismObject | None:
        doc = await self.collection.find_one(
            {'_id': object_id},
            sort=[('version', -1)]
        )
        return PrismObject(**doc) if doc else None
```

**Schema**:
```javascript
{
  _id: "room-123",  // object_id
  version: 5,
  data: { ... },
  created_at: ISODate("2024-01-15T10:30:00Z")
}

// Indexes
db.prism_objects.createIndex({ _id: 1, version: -1 })
db.prism_objects.createIndex({ created_at: -1 })
```

### 4. Redis Caching Layer (Optional)

**Why**: Shared cache for horizontal scaling

**Implementation**:
```python
class RedisCache:
    def __init__(self, redis_url: str):
        self.redis = redis.from_url(redis_url)

    async def get_version(self, object_id: str, version: int) -> PrismObject | None:
        key = f"prism:obj:{object_id}:{version}"
        data = await self.redis.get(key)
        return PrismObject.parse_raw(data) if data else None

    async def put_version(self, obj: PrismObject, ttl: int = 3600):
        key = f"prism:obj:{obj.id}:{obj.version}"
        await self.redis.setex(key, ttl, obj.json())
```

**Use Case**: Multiple backend servers share object cache

### 5. Client Pagination Support

**Paginated Subscriptions**:
```typescript
const { data, loadMore, hasMore } = usePrismObjectPaginated('room-messages', {
  pageSize: 50,
  filterType: 'recent'
})

// Load more messages
await loadMore()
```

**Server Support**:
```python
class PaginationFilter(Filter):
    def apply(self, obj: PrismObject, params: dict) -> PrismObject:
        page = params.get('page', 0)
        page_size = params.get('page_size', 50)

        messages = obj.data['messages']
        start = page * page_size
        end = start + page_size

        return PrismObject(
            id=obj.id,
            version=obj.version,
            data={
                'messages': messages[start:end],
                'has_more': end < len(messages),
                'total': len(messages)
            }
        )
```

---

## Medium-Term Roadmap (3-6 months)

### 1. GraphQL Compatibility Layer

**Why**: Some teams use GraphQL

**Approach**: Map GraphQL queries to Prism subscriptions

```graphql
type Room {
  id: ID!
  name: String!
  members: [User!]!  # Auto-hydrated references
}

type Subscription {
  room(id: ID!): Room!  # Maps to Prism subscribe
}
```

**Implementation**: GraphQL → Prism bridge layer

### 2. Predictive Pre-Fetching

**Concept**: Server predicts what client will request next

```python
class PredictiveManager:
    def __init__(self):
        self.access_patterns: dict[str, list[str]] = {}

    def record_access(self, object_id: str, next_accessed: str):
        """Record that after accessing object_id, client accessed next_accessed."""
        if object_id not in self.access_patterns:
            self.access_patterns[object_id] = []
        self.access_patterns[object_id].append(next_accessed)

    def predict_next(self, object_id: str) -> list[str]:
        """Predict what client will access next."""
        pattern = self.access_patterns.get(object_id, [])
        # Return most common next accesses
        return Counter(pattern).most_common(3)
```

**Auto-Prefetch**:
```python
# When client subscribes to room, prefetch likely members
await client.subscribe('room-123')
# Server automatically hydrates top 3 members based on pattern
```

### 3. Offline Support

**Challenge**: Handle requests when disconnected

**Solution**: Offline queue

```typescript
class OfflineQueue {
  private queue: PendingRequest[] = []

  async queueRequest(request: RequestMessage): Promise<void> {
    this.queue.push({
      request,
      timestamp: Date.now(),
      retries: 0
    })
    // Store in IndexedDB
    await this.persist()
  }

  async flushQueue(): Promise<void> {
    // When reconnected, replay queued requests
    for (const pending of this.queue) {
      try {
        await client.request(pending.request.request_type, pending.request.payload)
      } catch (error) {
        // Handle conflicts
      }
    }
  }
}
```

**Conflict Resolution**:
- Last-write-wins
- Merge strategies
- User intervention

### 4. Horizontal Scaling with Redis Pub/Sub

**Architecture**:
```
┌─────────┐     ┌─────────┐     ┌─────────┐
│ Server 1│────►│  Redis  │◄────│ Server 2│
└─────────┘     │ Pub/Sub │     └─────────┘
     │          └─────────┘          │
     │                               │
     ▼                               ▼
┌─────────┐                   ┌─────────┐
│Client 1 │                   │Client 2 │
└─────────┘                   └─────────┘
```

**Implementation**:
```python
class RedisNotifier:
    async def notify_object_updated(self, obj: PrismObject):
        # Publish to Redis
        await self.redis.publish(
            f'prism:updates:{obj.id}',
            obj.json()
        )

    async def subscribe_to_updates(self):
        # Subscribe to Redis channel
        async for message in self.pubsub.listen():
            obj = PrismObject.parse_raw(message['data'])
            # Notify local clients
            await self.object_manager.notify_object_updated(obj)
```

### 5. Partial Object Updates

**Current**: Full object replacement required

**Proposed**: Partial updates

```typescript
// Instead of:
const room = await storage.get_current('room-123')
room.data.member_ids.append(new_member)
await storage.save(room)

// Allow:
await client.request('updateObject', {
  object_id: 'room-123',
  patches: [
    { op: 'add', path: '/member_ids/-', value: new_member }
  ]
})
```

**Benefits**:
- Less data transfer
- Clearer intent
- Easier conflict resolution

---

## Long-Term Vision (6+ months)

### 1. Federation

**Goal**: Multiple Prism servers sync with each other

```
┌─────────┐         ┌─────────┐
│Server US│◄───────►│Server EU│
└─────────┘         └─────────┘
     │                   │
     ▼                   ▼
 US Clients          EU Clients
```

**Use Cases**:
- Geo-distributed systems
- Multi-tenant with data residency
- Cross-organization sync

### 2. Time-Travel Debugging

**Concept**: Replay state at any point in time

```typescript
const debugger = new PrismDebugger(client)

// Capture all state changes
debugger.record()

// Later, replay to specific point
await debugger.replayTo(timestamp)
```

**Implementation**: Store all object versions with timestamps

### 3. CRDT Integration

**Goal**: Conflict-free concurrent updates

**Approach**: Prism objects as CRDT wrappers

```python
from pycrdt import Doc, Map

class CRDTObject(PrismObject):
    def __init__(self, id: str, version: int):
        self.doc = Doc()
        self.map = self.doc.get_map('data')

    def merge(self, other: 'CRDTObject') -> 'CRDTObject':
        # CRDT automatic merge
        self.doc.apply_update(other.doc.get_update())
        return self
```

### 4. Query Language

**Beyond Simple Filters**:
```typescript
await client.subscribe('users', {
  query: {
    where: { age: { $gt: 18 } },
    sort: { name: 'asc' },
    limit: 100
  }
})
```

**Server**: Translate to database queries

### 5. Snapshot and Restore

**Use Case**: Clone object state across environments

```typescript
// Export state
const snapshot = await client.exportState(['room-*', 'user-*'])

// Import to another environment
await client2.importState(snapshot)
```

---

## Feature Requests & Ideas

### Community-Requested Features

**1. Streaming Large Objects**
- Current: Objects must fit in memory
- Proposed: Stream large objects in chunks
- Use Case: Video uploads, large documents

**2. Object Permissions (Access Control)**
```python
class ObjectACL:
    def can_read(self, user_id: str, object_id: str) -> bool
    def can_write(self, user_id: str, object_id: str) -> bool
    def can_subscribe(self, user_id: str, object_id: str) -> bool
```

**3. Metrics and Observability**
- Prometheus metrics
- OpenTelemetry integration
- Built-in dashboard

**4. Rate Limiting**
```python
@rate_limit(requests_per_second=10, burst=20)
async def handle_request(client_id: str, msg: RequestMessage):
    ...
```

**5. Compression**
- gzip/brotli for WebSocket messages
- Configurable per connection

**6. Binary Protocol Option**
- Alternative to JSON for performance
- Protocol Buffers or MessagePack

---

## For Scala Implementation Team

### Current Reference Implementation

**Python Backend** (`/backend`):
- Object Manager: `prism/server/object_manager.py`
- Request Router: `prism/server/request_router.py`
- Delta Computation: `prism/core/delta.py`
- Filters: `prism/filters/`
- Storage: `prism/storage/postgres.py`

**TypeScript Client** (`/frontend/packages/prism-client`):
- Client: `src/client.ts`
- Types: `src/types.ts`

**Tests**: `backend/tests/` and `frontend/integration-tests/`

### Key Differences to Note

**Type System**:
- Python uses Pydantic for validation
- Scala: Use case classes + Circe/Play JSON
- Critical: Maintain same JSON schema

**Concurrency**:
- Python: asyncio (single-threaded event loop)
- Scala: Akka/Pekko actors (multi-threaded)
- Both work, different patterns

**Storage**:
- Python: SQLAlchemy (async)
- Scala: Slick or Doobie
- Schema must be identical

### Scala-Specific Recommendations

**1. Use Immutable Case Classes**
```scala
case class PrismObject(
  id: String,
  version: Int,
  data: JsObject
) {
  // No var fields!
}
```

**2. Akka Actors for Subscription Management**
```scala
class SubscriptionActor extends Actor {
  var subscriptions: Map[String, Map[String, SubscriptionInfo]] = Map.empty

  def receive = {
    case Subscribe(clientId, objectId, info) =>
      // Update subscriptions
    case ObjectUpdated(obj) =>
      // Notify subscribers
  }
}
```

**3. FS2 Streams for WebSocket**
```scala
def webSocketFlow: Flow[Message, Message, NotUsed] = {
  Flow[Message]
    .collect { case TextMessage.Strict(text) => text }
    .via(processMessages)
    .map(response => TextMessage(response.toJson.toString))
}
```

### Critical Implementation Checklist

Must match Python implementation exactly:
- [ ] JSON message format (field names, types)
- [ ] Error codes (OBJECT_NOT_FOUND, etc.)
- [ ] Delta computation (JSON Patch RFC 6902)
- [ ] Filter behavior (fields, exclude_fields)
- [ ] Cache key format (`{id}:{version}:{filter}:{params}`)
- [ ] Delta efficiency threshold (70%)
- [ ] Reference resolution depth limit (5)

### Testing Against Python Implementation

**Interoperability Tests**:
1. Start Python server: `cd backend && poetry run python -m chat_demo.main`
2. Connect Scala client
3. Run all integration tests
4. **All tests must pass** (no "close enough")

**Test Data**: Use same test data as `frontend/integration-tests/`

### Documentation Needed

- [ ] Scala API documentation
- [ ] Migration guide from Python
- [ ] Performance comparison
- [ ] Deployment guide
- [ ] Examples (Play, Akka HTTP)

---

## Breaking Changes Policy

### Versioning

**Semantic Versioning**: `MAJOR.MINOR.PATCH`

- **MAJOR**: Breaking protocol changes
- **MINOR**: New features, backward compatible
- **PATCH**: Bug fixes

**Current**: v1.0 (reference implementation stable)

### Backward Compatibility

**Protocol**: JSON schema is versioned

```json
{
  "type": "subscribe",
  "protocol_version": "1.0",
  "object_id": "room-123"
}
```

**Policy**:
- v1.x clients work with v1.x servers
- v2.0 may break compatibility (with migration path)

### Deprecation Process

1. Feature marked deprecated (v1.3)
2. Warning in documentation and logs
3. Removed in next major version (v2.0)
4. Minimum 6 months notice

---

## Contributing

**How to Propose Features**:
1. Open GitHub issue with `[FEATURE]` tag
2. Describe use case and benefit
3. Discuss design with maintainers
4. Implement with tests and docs
5. Submit PR

**Priority Criteria**:
- User impact (how many users benefit?)
- Implementation complexity
- Alignment with core vision
- Community interest

---

## Timeline Summary

**Q1 2025** (Immediate):
- [x] Complete Python/TypeScript reference implementation
- [ ] Pinia store integration
- [ ] Version synchronization helpers
- [ ] Enhanced Vue composables

**Q2 2025** (Short-term):
- [ ] Batch operations API
- [ ] MongoDB storage adapter
- [ ] Client pagination support
- [ ] Scala implementation (separate team)

**Q3-Q4 2025** (Medium-term):
- [ ] GraphQL compatibility
- [ ] Predictive pre-fetching
- [ ] Offline support
- [ ] Horizontal scaling with Redis

**2026+** (Long-term):
- [ ] Federation
- [ ] CRDT integration
- [ ] Advanced query language
- [ ] Time-travel debugging

---

## Questions or Feedback?

- **GitHub Issues**: https://github.com/rorygraves/prism/issues
- **Documentation**: See `docs/` directory
- **Scala Team**: See `docs/implementation-guide.md`

**Last Updated**: 2025-11-17
