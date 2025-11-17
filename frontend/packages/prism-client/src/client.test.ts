/**
 * Unit tests for PrismClient
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { PrismClient } from './client.js';
import type {
  FullObjectMessage,
  DeltaMessage,
  ResponseMessage,
  ErrorMessage,
} from './types.js';

/**
 * Mock WebSocket for testing
 */
class MockWebSocket {
  public onopen: ((event: Event) => void) | null = null;
  public onclose: ((event: CloseEvent) => void) | null = null;
  public onmessage: ((event: MessageEvent) => void) | null = null;
  public onerror: ((event: Event) => void) | null = null;
  public readyState: number = WebSocket.CONNECTING;
  public url: string;

  private messageQueue: any[] = [];

  constructor(url: string) {
    this.url = url;
    // Simulate async connection
    setTimeout(() => {
      this.readyState = WebSocket.OPEN;
      if (this.onopen) {
        this.onopen(new Event('open'));
      }
    }, 0);
  }

  send(data: string): void {
    // Store sent messages for verification
    this.messageQueue.push(JSON.parse(data));
  }

  close(): void {
    this.readyState = WebSocket.CLOSED;
    if (this.onclose) {
      // Use plain Event instead of CloseEvent for Node compatibility
      this.onclose(new Event('close') as any);
    }
  }

  // Test helper to simulate receiving a message
  simulateMessage(message: any): void {
    if (this.onmessage) {
      this.onmessage(new MessageEvent('message', {
        data: JSON.stringify(message),
      }));
    }
  }

  // Test helper to get sent messages
  getSentMessages(): any[] {
    return this.messageQueue;
  }

  // Test helper to get last sent message
  getLastSentMessage(): any {
    return this.messageQueue[this.messageQueue.length - 1];
  }

  clearMessages(): void {
    this.messageQueue = [];
  }
}

describe('PrismClient', () => {
  let client: PrismClient;
  let mockWs: MockWebSocket;

  beforeEach(async () => {
    // Replace global WebSocket with mock
    global.WebSocket = MockWebSocket as any;

    client = new PrismClient('ws://localhost:8000');
    await client.connect();

    // Get reference to mock WebSocket
    mockWs = (client as any).ws as MockWebSocket;
    mockWs.clearMessages();
  });

  describe('Connection', () => {
    it('should connect successfully', () => {
      expect(client.isConnected()).toBe(true);
    });

    it('should disconnect', () => {
      client.disconnect();
      expect(client.isConnected()).toBe(false);
    });

    it('should call onConnected callback', async () => {
      const callback = vi.fn();
      const newClient = new PrismClient('ws://localhost:8000');
      newClient.onConnected(callback);
      await newClient.connect();
      expect(callback).toHaveBeenCalled();
    });

    it('should call onDisconnected callback', async () => {
      const callback = vi.fn();
      client.onDisconnected(callback);
      client.disconnect();
      expect(callback).toHaveBeenCalled();
    });
  });

  describe('Subscribe', () => {
    it('should send subscribe message', async () => {
      await client.subscribe('test-obj-1', 'default', false);

      const lastMsg = mockWs.getLastSentMessage();
      expect(lastMsg).toMatchObject({
        type: 'subscribe',
        object_id: 'test-obj-1',
        filter_type: 'default',
        temporary: false,
      });
    });

    it('should handle full object response', async () => {
      await client.subscribe('test-obj-1');

      const fullObjectMsg: FullObjectMessage = {
        type: 'fullObject',
        id: 'test-obj-1',
        version: 1,
        data: { name: 'Test' },
      };

      mockWs.simulateMessage(fullObjectMsg);

      // Check object is cached
      const cached = (client as any).objects.get('test-obj-1');
      expect(cached).toEqual({
        version: 1,
        data: { name: 'Test' },
      });
    });

    it('should handle delta updates', async () => {
      // First, add an object to cache
      await client.subscribe('test-obj-1');
      mockWs.simulateMessage({
        type: 'fullObject',
        id: 'test-obj-1',
        version: 1,
        data: { name: 'Test', count: 1 },
      });

      // Then receive a delta
      const deltaMsg: DeltaMessage = {
        type: 'delta',
        id: 'test-obj-1',
        from_version: 1,
        to_version: 2,
        patches: [
          { op: 'replace', path: '/count', value: 2 },
        ],
      };

      mockWs.simulateMessage(deltaMsg);

      // Check delta was applied
      const cached = (client as any).objects.get('test-obj-1');
      expect(cached.version).toBe(2);
      expect(cached.data.count).toBe(2);
    });
  });

  describe('Unsubscribe', () => {
    it('should send unsubscribe message', async () => {
      await client.subscribe('test-obj-1');
      mockWs.clearMessages();

      await client.unsubscribe('test-obj-1');

      const lastMsg = mockWs.getLastSentMessage();
      expect(lastMsg).toMatchObject({
        type: 'unsubscribe',
        object_id: 'test-obj-1',
      });
    });
  });

  describe('UpdateFilter', () => {
    it('should send updateFilter message', async () => {
      // First subscribe
      await client.subscribe('test-obj-1', 'default');
      mockWs.clearMessages();

      // Then update filter
      await client.updateFilter('test-obj-1', 'fields', { fields: ['name'] });

      const lastMsg = mockWs.getLastSentMessage();
      expect(lastMsg).toMatchObject({
        type: 'updateFilter',
        object_id: 'test-obj-1',
        filter_type: 'fields',
        filter_params: { fields: ['name'] },
      });
    });

    it('should throw error if not subscribed', async () => {
      await expect(
        client.updateFilter('test-obj-1', 'fields')
      ).rejects.toThrow('Not subscribed to object test-obj-1');
    });

    it('should update local subscription info', async () => {
      await client.subscribe('test-obj-1', 'default');

      await client.updateFilter('test-obj-1', 'fields', { fields: ['name'] });

      const subscription = (client as any).subscriptions.get('test-obj-1');
      expect(subscription.filterType).toBe('fields');
      expect(subscription.filterParams).toEqual({ fields: ['name'] });
    });

    it('should handle filtered object response', async () => {
      await client.subscribe('test-obj-1', 'default');

      // Initial object
      mockWs.simulateMessage({
        type: 'fullObject',
        id: 'test-obj-1',
        version: 1,
        data: { name: 'Test', age: 30, email: 'test@example.com' },
      });

      // Update filter
      await client.updateFilter('test-obj-1', 'fields', { fields: ['name', 'email'] });

      // Receive filtered object
      mockWs.simulateMessage({
        type: 'fullObject',
        id: 'test-obj-1',
        version: 1,
        data: { name: 'Test', email: 'test@example.com' },
        filtered: true,
        filter_type: 'fields',
      });

      const cached = (client as any).objects.get('test-obj-1');
      expect(cached.data).toEqual({ name: 'Test', email: 'test@example.com' });
      expect(cached.data.age).toBeUndefined();
    });
  });

  describe('Request', () => {
    it('should send request message', async () => {
      const requestPromise = client.request('createUser', { name: 'Alice' });

      const lastMsg = mockWs.getLastSentMessage();
      expect(lastMsg.type).toBe('request');
      expect(lastMsg.request_type).toBe('createUser');
      expect(lastMsg.payload).toEqual({ name: 'Alice' });
      expect(lastMsg.request_id).toBeDefined();

      // Send response
      const responseMsg: ResponseMessage = {
        type: 'response',
        request_id: lastMsg.request_id,
        success: true,
        data: { user_id: 'user-1' },
      };
      mockWs.simulateMessage(responseMsg);

      const result = await requestPromise;
      expect(result).toEqual({ user_id: 'user-1' });
    });

    it('should handle request errors', async () => {
      const requestPromise = client.request('invalidRequest', {});

      const lastMsg = mockWs.getLastSentMessage();

      const errorMsg: ResponseMessage = {
        type: 'response',
        request_id: lastMsg.request_id,
        success: false,
        error: 'Invalid request type',
      };
      mockWs.simulateMessage(errorMsg);

      await expect(requestPromise).rejects.toThrow('Invalid request type');
    });

    it('should hydrate references in response', async () => {
      const requestPromise = client.request('createMessage', { content: 'Hello' });

      const lastMsg = mockWs.getLastSentMessage();

      const responseMsg: ResponseMessage = {
        type: 'response',
        request_id: lastMsg.request_id,
        success: true,
        data: { message_id: 'msg-1' },
        hydrated: [
          {
            id: 'msg-1',
            version: 1,
            data: { content: 'Hello', user_id: 'user-1' },
          },
        ],
      };
      mockWs.simulateMessage(responseMsg);

      await requestPromise;

      // Check hydrated object is cached
      const cached = (client as any).objects.get('msg-1');
      expect(cached).toEqual({
        version: 1,
        data: { content: 'Hello', user_id: 'user-1' },
      });
    });
  });

  describe('Watch', () => {
    it('should call callback on object updates', async () => {
      const callback = vi.fn();

      await client.subscribe('test-obj-1');
      client.watch('test-obj-1', callback);

      mockWs.simulateMessage({
        type: 'fullObject',
        id: 'test-obj-1',
        version: 1,
        data: { name: 'Test' },
      });

      expect(callback).toHaveBeenCalledWith({ name: 'Test' });
    });

    it('should allow multiple watchers', async () => {
      const callback1 = vi.fn();
      const callback2 = vi.fn();

      await client.subscribe('test-obj-1');
      client.watch('test-obj-1', callback1);
      client.watch('test-obj-1', callback2);

      mockWs.simulateMessage({
        type: 'fullObject',
        id: 'test-obj-1',
        version: 1,
        data: { name: 'Test' },
      });

      expect(callback1).toHaveBeenCalled();
      expect(callback2).toHaveBeenCalled();
    });

    it('should return unwatch function', async () => {
      const callback = vi.fn();

      await client.subscribe('test-obj-1');
      const unwatch = client.watch('test-obj-1', callback);

      mockWs.simulateMessage({
        type: 'fullObject',
        id: 'test-obj-1',
        version: 1,
        data: { name: 'Test' },
      });

      expect(callback).toHaveBeenCalledTimes(1);

      // Unwatch
      unwatch();

      mockWs.simulateMessage({
        type: 'fullObject',
        id: 'test-obj-1',
        version: 2,
        data: { name: 'Test2' },
      });

      // Callback should not be called again
      expect(callback).toHaveBeenCalledTimes(1);
    });
  });

  describe('Error Handling', () => {
    it('should handle error messages', async () => {
      const errorMsg: ErrorMessage = {
        type: 'error',
        code: 'OBJECT_NOT_FOUND',
        message: 'Object not found',
        object_id: 'test-obj-1',
      };

      // Spy on console.error
      const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

      mockWs.simulateMessage(errorMsg);

      expect(consoleErrorSpy).toHaveBeenCalledWith(
        'Prism error [OBJECT_NOT_FOUND]: Object not found'
      );

      consoleErrorSpy.mockRestore();
    });
  });
});
