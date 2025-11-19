# @prism/client

TypeScript client library for the Prism protocol.

## Installation

```bash
npm install @prism/client
```

## Quick Start

```typescript
import { PrismClient } from '@prism/client';

// Create client
const client = new PrismClient('ws://localhost:8000/ws');

// Connect
await client.connect();

// Subscribe to an object
await client.subscribe('room-123');

// Watch for updates
client.watch('room-123', (data) => {
  console.log('Room updated:', data);
});

// Make a request with smart hydration
const result = await client.request('sendMessage', {
  room_id: 'room-123',
  content: 'Hello!'
}, {
  hydrateRefs: true,
  subscribeToRefs: true
});

// Unsubscribe when done
await client.unsubscribe('room-123');
```

## Features

- **WebSocket Transport**: Real-time bidirectional communication
- **Smart Caching**: Automatic client-side object cache
- **Delta Updates**: Efficient incremental updates using JSON Patch
- **Reference Resolution**: Automatic object reference hydration
- **Reconnection**: Automatic reconnection with state sync
- **TypeScript**: Full type safety with TypeScript

## API Reference

### PrismClient

#### Constructor

```typescript
new PrismClient(url: string)
```

Create a new Prism client.

#### connect()

```typescript
async connect(): Promise<void>
```

Connect to the Prism server.

#### disconnect()

```typescript
disconnect(): void
```

Disconnect from the server.

#### subscribe()

```typescript
async subscribe(
  objectId: string,
  filterType?: string,
  temporary?: boolean
): Promise<void>
```

Subscribe to an object for real-time updates.

#### unsubscribe()

```typescript
async unsubscribe(objectId: string): Promise<void>
```

Unsubscribe from an object.

#### request()

```typescript
async request<T>(
  requestType: string,
  payload: Record<string, any>,
  options?: RequestOptions
): Promise<T>
```

Make a request with automatic reference hydration.

#### watch()

```typescript
watch(
  objectId: string,
  callback: (data: Record<string, any>) => void
): () => void
```

Watch an object for changes. Returns an unwatch function.

#### getCached()

```typescript
getCached(objectId: string): Record<string, any> | null
```

Get a cached object if available.

### Types

```typescript
interface RequestOptions {
  hydrateRefs?: boolean;        // Auto-hydrate references
  subscribeToRefs?: boolean;    // Auto-subscribe to references
  filterType?: string;          // Default filter
}

interface PrismObject {
  id: string;
  version: number;
  data: Record<string, any>;
}

interface ObjectReference {
  id: string;
  version: number;
  filterType?: string;
  subscribe?: boolean;
}
```

## Examples

### Real-time Chat

```typescript
const client = new PrismClient('ws://localhost:8000/ws');
await client.connect();

// Subscribe to room
await client.subscribe('room-123');

// Watch for new messages
client.watch('room-123', (room) => {
  console.log('Room updated:', room);
});

// Send message
await client.request('sendMessage', {
  room_id: 'room-123',
  content: 'Hello world!'
});
```

### Dashboard with Live Data

```typescript
// Subscribe to multiple widgets
await client.subscribe('widget-1');
await client.subscribe('widget-2');
await client.subscribe('widget-3');

// Watch all widgets
const unwatchers = [];
for (const id of ['widget-1', 'widget-2', 'widget-3']) {
  const unwatch = client.watch(id, (data) => {
    updateUI(id, data);
  });
  unwatchers.push(unwatch);
}

// Cleanup
unwatchers.forEach(fn => fn());
```

### Handling Reconnections

```typescript
client.onDisconnected(() => {
  console.log('Disconnected, will retry...');
});

client.onConnected(() => {
  console.log('Connected!');
  // Subscriptions are automatically re-synced
});
```

## Advanced Usage

### Custom Reference Resolution

The client automatically resolves object references in responses:

```typescript
// Server returns:
// { user: { id: 'user-123', version: 1 } }

const result = await client.request('getUser', { userId: 'user-123' });

// result.user is automatically resolved to the full user object
console.log(result.user.name); // Works!
```

### Temporary Subscriptions

For one-time fetches without ongoing updates:

```typescript
await client.subscribe('user-123', 'default', true); // temporary=true
```

### Connection Status

```typescript
if (client.isConnected()) {
  console.log('Connected to server');
}
```

## Best Practices

1. **Unsubscribe when done**: Always unsubscribe from objects you no longer need
2. **Use watchers**: Use `.watch()` for reactive UI updates
3. **Enable hydration**: Set `hydrateRefs: true` for complex responses
4. **Handle errors**: Wrap requests in try-catch blocks
5. **Cleanup**: Store unwatch functions and call them on component unmount

## License

MIT
