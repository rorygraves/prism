# Prism Protocol Specification

## Overview

Prism is a protocol for efficient, real-time synchronization of versioned objects between distributed clients and servers. It provides automatic delta computation, intelligent caching, and smart object reference resolution.

## Core Concepts

### Versioned Objects

Every object in Prism is immutable and contains:

- **id**: Unique string identifier
- **version**: Monotonically increasing integer version number
- **data**: JSON-serializable object data

```typescript
interface PrismObject {
  id: string;
  version: number;
  data: Record<string, any>;
}
```

Objects reference other objects by ID only, never by direct embedding.

### Delta Protocol

Changes between object versions are represented as JSON Patch (RFC 6902) operations:

```typescript
interface Delta {
  objectId: string;
  fromVersion: number;
  toVersion: number;
  patches: JsonPatchOperation[];
}
```

The server automatically computes deltas and decides whether to send a delta or full object based on efficiency (default threshold: delta must be <70% of full object size).

### Object Filters

Filters transform objects before transmission:

- **Security filters**: Server-enforced, hide sensitive data
- **Optimization filters**: Reduce payload size
- **Custom filters**: Application-specific transformations

```typescript
interface FilterDefinition {
  name: string;
  transform: (obj: any, params?: any) => any;
  cacheable: boolean;
  clientMutable: boolean; // Can clients request this filter?
}
```

### Object References

Object references enable smart hydration:

```typescript
interface ObjectReference {
  id: string;
  version: number;
  filterType?: string;
  subscribe?: boolean; // Auto-subscribe client?
}
```

When the server encounters an ObjectReference in a response, it:

1. Checks what version the client has
2. Sends full object if client doesn't have it
3. Sends delta if client has older version
4. Marks as cached if client has current version

## Message Types

### Client → Server

#### Subscribe

Subscribe to real-time updates for an object:

```json
{
  "type": "subscribe",
  "object_id": "room-123",
  "filter_type": "default",
  "temporary": false
}
```

#### Unsubscribe

Stop receiving updates:

```json
{
  "type": "unsubscribe",
  "object_id": "room-123"
}
```

#### Sync

Synchronize state after reconnection:

```json
{
  "type": "sync",
  "states": [
    {"id": "room-123", "version": 5, "filter_type": "default"},
    {"id": "user-456", "version": 2, "filter_type": "default"}
  ]
}
```

#### Request

Execute business logic with smart hydration:

```json
{
  "type": "request",
  "request_id": "req-001",
  "request_type": "sendMessage",
  "payload": {
    "room_id": "room-123",
    "content": "Hello!"
  },
  "options": {
    "hydrate_refs": true,
    "subscribe_to_refs": true
  }
}
```

#### Update Filter

Change filter for existing subscription:

```json
{
  "type": "updateFilter",
  "object_id": "room-123",
  "filter_type": "detailed",
  "filter_params": {"include_history": true}
}
```

### Server → Client

#### Full Object

Send complete object:

```json
{
  "type": "fullObject",
  "id": "msg-789",
  "version": 1,
  "data": {
    "content": "Hello!",
    "user_id": "user-456",
    "timestamp": "2024-01-15T10:30:00Z"
  },
  "filtered": true,
  "filter_type": "default"
}
```

#### Delta

Send incremental update:

```json
{
  "type": "delta",
  "id": "room-123",
  "from_version": 5,
  "to_version": 6,
  "patches": [
    {"op": "add", "path": "/member_ids/-", "value": "user-789"}
  ]
}
```

#### Response

Return request result with hydrated references:

```json
{
  "type": "response",
  "request_id": "req-001",
  "success": true,
  "data": {
    "message": {"id": "msg-789", "version": 1}
  },
  "hydrated": [
    {
      "id": "msg-789",
      "version": 1,
      "data": {"content": "Hello!", "user_id": "user-456"}
    },
    {
      "id": "user-456",
      "version": 3,
      "cached": true
    }
  ]
}
```

#### Error

Report errors:

```json
{
  "type": "error",
  "code": "OBJECT_NOT_FOUND",
  "message": "Object room-999 not found",
  "object_id": "room-999"
}
```

## Request/Response Flow

### 1. Client Makes Request

```typescript
await client.request('sendMessage', {
  room_id: 'room-123',
  content: 'Hello!'
}, {
  hydrateRefs: true,
  subscribeToRefs: true
});
```

### 2. Server Processes Business Logic

Business handler returns data with ObjectReferences:

```python
return {
    "message": ObjectReference(
        id="msg-789",
        version=1,
        subscribe=True
    ),
    "user": ObjectReference(
        id="user-456",
        version=3
    )
}
```

### 3. Server Enhances Response

For each reference:
- Check client's known version
- Send full object, delta, or mark as cached
- Auto-subscribe if requested

### 4. Client Receives Enhanced Response

Client automatically:
- Updates local cache
- Resolves references in response data
- Subscribes to objects as needed
- Notifies watchers of changes

## Caching Strategy

### Server-Side Caches

- **Version Cache**: Recently accessed object versions (LRU, default size: 10000)
- **Delta Cache**: Pre-computed deltas (LRU, default size: 5000)
- **Filter Cache**: Filtered object results (LRU, default size: 5000)

### Client-Side Cache

- **Object Cache**: Map of object ID → {version, data}
- **Subscription Registry**: Active subscriptions
- **Pending Requests**: In-flight requests awaiting responses

## Reconnection Handling

When a client reconnects:

1. Client sends `sync` message with current state
2. Server compares with current versions
3. Server sends updates (full objects or deltas) as needed
4. Subscriptions are re-established

```typescript
// Automatic reconnection
client.onDisconnected(() => {
  // Exponential backoff
  setTimeout(() => {
    client.connect().then(() => {
      // Auto-sync subscriptions
      client.syncState();
    });
  }, delay);
});
```

## Performance Optimizations

### Delta Efficiency Check

Only send delta if it's significantly smaller:

```python
delta_size < object_size * 0.7  # 70% threshold
```

### Batch Updates

Multiple object updates can be batched in a single message.

### Filter Caching

Filtered views are cached to avoid recomputation:

```
Cache key: {object_id}:{version}:{filter_type}
```

### Reference Resolution Depth

Limit recursive reference resolution (default max depth: 5).

## Security Considerations

### Filter Enforcement

Server always applies security filters before hydration:

```python
# Security filter runs first
obj = apply_filter(obj, "security")

# Then optional user-requested filter
if client_filter:
    obj = apply_filter(obj, client_filter)
```

### Access Control

Verify client permissions before:
- Subscribing to objects
- Resolving references
- Processing requests

### Rate Limiting

Implement limits on:
- Requests per second
- Subscriptions per client
- Reference resolution depth

## Implementation Notes

### Python Backend

- Use `PrismObjectManager` for subscriptions and state
- Implement `BusinessHandler` for application logic
- Use `RequestRouter` for smart hydration
- Choose storage adapter (PostgreSQL, MongoDB, etc.)

### TypeScript Client

- Create `PrismClient` instance
- Use `.subscribe()` for objects
- Use `.request()` for business logic
- Use `.watch()` for reactive updates

### Vue Integration

```typescript
const { data, loading } = usePrismObject('room-123');
const { execute } = usePrismRequest();

await execute('sendMessage', { content: 'Hi!' });
```

## Error Codes

| Code | Description |
|------|-------------|
| `OBJECT_NOT_FOUND` | Referenced object doesn't exist |
| `INVALID_MESSAGE` | Malformed protocol message |
| `INVALID_JSON` | Message is not valid JSON |
| `NOT_SUBSCRIBED` | Trying to update filter for non-subscribed object |
| `INTERNAL_ERROR` | Server-side error during processing |
| `VERSION_MISMATCH` | Client version doesn't match server expectations |

## Best Practices

### Object Design

- Keep objects focused and cohesive
- Use references instead of embedding
- Version objects on every change
- Make data JSON-serializable

### Subscription Management

- Unsubscribe when objects no longer needed
- Use temporary subscriptions for one-time fetches
- Group related subscriptions

### Request Patterns

- Enable `hydrateRefs` for complex responses
- Use `subscribeToRefs` for ongoing updates
- Apply appropriate filters to reduce payload

### Error Handling

- Handle connection failures gracefully
- Implement retry logic with backoff
- Show user-friendly error messages
- Log errors for debugging

## Future Enhancements

- GraphQL compatibility layer
- Predictive pre-fetching
- Request pipelining
- Partial object updates
- Federation across multiple servers
- Offline request queue
