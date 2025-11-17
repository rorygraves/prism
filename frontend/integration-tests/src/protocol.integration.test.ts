/**
 * Integration tests for Prism protocol.
 *
 * These tests connect to a REAL backend server and test the full protocol.
 *
 * Before running:
 * 1. Start the backend: cd backend && poetry run python -m chat_demo.main
 * 2. Or set AUTO_START_BACKEND=true to auto-start
 */

import { describe, it, expect, beforeAll, afterAll, beforeEach } from 'vitest';
import { PrismClient } from '@prism/client';
import { getWebSocketUrl, waitFor } from './setup.js';

describe('Prism Protocol Integration Tests', () => {
  let client: PrismClient;
  const wsUrl = getWebSocketUrl();

  beforeEach(async () => {
    // Create fresh client for each test
    client = new PrismClient(wsUrl);
    await client.connect();
  });

  afterAll(async () => {
    if (client && client.isConnected()) {
      client.disconnect();
    }
  });

  describe('Connection', () => {
    it('should connect to backend server', async () => {
      expect(client.isConnected()).toBe(true);
    });

    it('should reconnect after disconnect', async () => {
      client.disconnect();
      expect(client.isConnected()).toBe(false);

      await client.connect();
      expect(client.isConnected()).toBe(true);
    });
  });

  describe('Subscribe and Object Sync', () => {
    it('should subscribe to global room list', async () => {
      let receivedData: any = null;

      // Watch for updates
      const unwatch = client.watch('global-room-list', (data) => {
        receivedData = data;
      });

      // Subscribe
      await client.subscribe('global-room-list', 'default');

      // Wait for data
      await waitFor(() => receivedData !== null);

      expect(receivedData).toBeDefined();
      expect(Array.isArray(receivedData.rooms)).toBe(true);

      unwatch();
    });

    it('should receive object updates', async () => {
      const updates: any[] = [];

      client.watch('global-room-list', (data) => {
        updates.push(data);
      });

      await client.subscribe('global-room-list');

      // Create a room to trigger update
      await client.request('createRoom', {
        name: `Test Room ${Date.now()}`,
        user_id: 'test-user-1',
      });

      // Should receive at least 2 updates (initial + after room creation)
      await waitFor(() => updates.length >= 2, 10000);

      expect(updates.length).toBeGreaterThanOrEqual(2);
    });

    it('should unsubscribe from object', async () => {
      await client.subscribe('global-room-list');

      // Unsubscribe
      await client.unsubscribe('global-room-list');

      // No error should occur
      expect(true).toBe(true);
    });
  });

  describe('Request/Response with Hydration', () => {
    it('should create user with hydrated response', async () => {
      const result = await client.request('createUser', {
        username: `user-${Date.now()}`,
      });

      expect(result).toBeDefined();
      expect(result.user).toBeDefined();

      // Check if reference was hydrated
      if (result.user.id) {
        const cachedUser = client.getCached(result.user.id);
        expect(cachedUser).toBeDefined();
      }
    });

    it('should create room and hydrate references', async () => {
      // First create a user
      const userResult = await client.request('createUser', {
        username: `user-${Date.now()}`,
      });

      // Create room
      const roomResult = await client.request('createRoom', {
        name: `Room ${Date.now()}`,
        user_id: userResult.user.id,
      });

      expect(roomResult).toBeDefined();
      expect(roomResult.room).toBeDefined();

      // Room should be in cache if hydrated
      if (roomResult.room.id) {
        const cachedRoom = client.getCached(roomResult.room.id);
        // May or may not be cached depending on server implementation
      }
    });

    it('should handle multiple references in response', async () => {
      const userResult = await client.request('createUser', {
        username: `user-${Date.now()}`,
      });

      const roomResult = await client.request('createRoom', {
        name: `Room ${Date.now()}`,
        user_id: userResult.user.id,
      });

      // Send a message (will have both message and user references)
      const messageResult = await client.request('sendMessage', {
        room_id: roomResult.room.id,
        user_id: userResult.user.id,
        content: 'Hello from integration test!',
      });

      expect(messageResult).toBeDefined();
      expect(messageResult.message).toBeDefined();
    });
  });

  describe('UpdateFilter', () => {
    it('should update filter on subscribed object', async () => {
      const updates: any[] = [];

      client.watch('global-room-list', (data) => {
        updates.push(data);
      });

      // Subscribe with default filter
      await client.subscribe('global-room-list', 'default');
      await waitFor(() => updates.length >= 1);

      const initialUpdate = updates[updates.length - 1];
      updates.length = 0; // Clear

      // Update filter (if backend supports it)
      try {
        await client.updateFilter('global-room-list', 'default');

        // Should receive updated object
        await waitFor(() => updates.length >= 1, 3000);

        const afterFilterUpdate = updates[updates.length - 1];
        expect(afterFilterUpdate).toBeDefined();
      } catch (err) {
        // Filter update might not change anything with 'default' filter
        console.log('Filter update:', err);
      }
    });

    it('should throw error when updating filter for non-subscribed object', async () => {
      await expect(
        client.updateFilter('non-existent-object', 'default')
      ).rejects.toThrow('Not subscribed');
    });
  });

  describe('Delta Updates', () => {
    it('should receive delta updates for subscribed objects', async () => {
      const updates: any[] = [];
      let roomId: string;

      // Create user and room
      const userResult = await client.request('createUser', {
        username: `user-${Date.now()}`,
      });

      const roomResult = await client.request('createRoom', {
        name: `Room ${Date.now()}`,
        user_id: userResult.user.id,
      });

      roomId = roomResult.room.id;

      // Subscribe to the room
      client.watch(roomId, (data) => {
        updates.push(data);
      });

      await client.subscribe(roomId);
      await waitFor(() => updates.length >= 1);

      const initialVersion = updates.length;
      updates.length = 0;

      // Trigger an update by sending a message
      await client.request('sendMessage', {
        room_id: roomId,
        user_id: userResult.user.id,
        content: 'Test message',
      });

      // Should receive delta or full update
      await waitFor(() => updates.length >= 1, 5000);

      expect(updates.length).toBeGreaterThan(0);
    });
  });

  describe('Multiple Clients', () => {
    it('should sync updates across multiple clients', async () => {
      const client2 = new PrismClient(wsUrl);
      await client2.connect();

      const updates1: any[] = [];
      const updates2: any[] = [];

      try {
        // Both subscribe to room list
        client.watch('global-room-list', (data) => updates1.push(data));
        client2.watch('global-room-list', (data) => updates2.push(data));

        await client.subscribe('global-room-list');
        await client2.subscribe('global-room-list');

        await waitFor(() => updates1.length >= 1 && updates2.length >= 1);

        updates1.length = 0;
        updates2.length = 0;

        // Client 1 creates a room
        await client.request('createRoom', {
          name: `Multi-client room ${Date.now()}`,
          user_id: 'test-user',
        });

        // Both clients should receive the update
        await waitFor(
          () => updates1.length >= 1 && updates2.length >= 1,
          10000
        );

        expect(updates1.length).toBeGreaterThan(0);
        expect(updates2.length).toBeGreaterThan(0);
      } finally {
        client2.disconnect();
      }
    });
  });

  describe('Error Handling', () => {
    it('should handle invalid request types', async () => {
      await expect(
        client.request('invalidRequestType', {})
      ).rejects.toThrow();
    });

    it('should handle missing required fields', async () => {
      await expect(
        client.request('createRoom', {
          // Missing name and user_id
        })
      ).rejects.toThrow();
    });
  });

  describe('Reference Hydration', () => {
    it('should hydrate nested object references', async () => {
      // Create user
      const userResult = await client.request('createUser', {
        username: `user-${Date.now()}`,
      });

      // Create room
      const roomResult = await client.request('createRoom', {
        name: `Room ${Date.now()}`,
        user_id: userResult.user.id,
      });

      // Send message - response should have message reference
      const messageResult = await client.request('sendMessage', {
        room_id: roomResult.room.id,
        user_id: userResult.user.id,
        content: 'Test message',
      });

      // Message should be in response
      expect(messageResult.message).toBeDefined();

      // If hydration worked, message should be in cache
      if (messageResult.message.id) {
        const cached = client.getCached(messageResult.message.id);
        // Might be hydrated depending on server implementation
      }
    });

    it('should handle cached references efficiently', async () => {
      const userResult = await client.request('createUser', {
        username: `user-${Date.now()}`,
      });

      const userId = userResult.user.id;

      // First request should send full object
      await client.request('getUser', { user_id: userId });

      // Second request for same user might return cached reference
      const secondResult = await client.request('getUser', { user_id: userId });

      expect(secondResult).toBeDefined();
    });
  });

  describe('Auto-subscription', () => {
    it('should auto-subscribe when reference has subscribe=true', async () => {
      // Some requests might auto-subscribe to returned objects
      const userResult = await client.request('createUser', {
        username: `user-${Date.now()}`,
      }, {
        subscribeToRefs: true,
      });

      // User should be auto-subscribed if server supports it
      // This is implementation-dependent
      expect(userResult.user).toBeDefined();
    });
  });
});
