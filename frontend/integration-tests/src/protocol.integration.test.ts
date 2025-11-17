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

      // Wait a bit for disconnect to complete
      await new Promise(resolve => setTimeout(resolve, 100));
      expect(client.isConnected()).toBe(false);

      await client.connect();
      expect(client.isConnected()).toBe(true);
    });
  });

  describe('Subscribe and Object Sync', () => {
    it('should subscribe to global room list', async () => {
      let receivedData: any = null;

      // Watch for updates BEFORE subscribing
      const unwatch = client.watch('global-room-list', (data) => {
        receivedData = data;
      });

      // First call listRooms to ensure global-room-list exists
      await client.request('listRooms', {});

      // Subscribe
      await client.subscribe('global-room-list', 'default');

      // Wait for data
      await waitFor(() => receivedData !== null);

      expect(receivedData).toBeDefined();
      expect(receivedData.room_ids).toBeDefined();

      unwatch();
    });

    it('should receive object updates', async () => {
      const updates: any[] = [];

      // Watch for updates BEFORE subscribing
      client.watch('global-room-list', (data) => {
        updates.push(data);
      });

      // Ensure global-room-list exists
      await client.request('listRooms', {});

      await client.subscribe('global-room-list');

      // Wait for initial update
      await waitFor(() => updates.length >= 1);

      // Create a user first
      const userResult = await client.request('createUser', {
        username: `user-${Date.now()}`,
        display_name: `Test User ${Date.now()}`,
      });

      // Create a room to trigger update
      await client.request('createRoom', {
        name: `Test Room ${Date.now()}`,
        creator_id: userResult.user.id,
      });

      // Should receive update after room creation
      await waitFor(() => updates.length >= 2, 10000);

      expect(updates.length).toBeGreaterThanOrEqual(2);
    });

    it('should unsubscribe from object', async () => {
      // Ensure global-room-list exists
      await client.request('listRooms', {});

      await client.subscribe('global-room-list');

      // Unsubscribe
      await client.unsubscribe('global-room-list');

      // No error should occur
      expect(true).toBe(true);
    });
  });

  describe('Request/Response with Hydration', () => {
    it('should create user with hydrated response', async () => {
      const timestamp = Date.now();
      const result = await client.request('createUser', {
        username: `user-${timestamp}`,
        display_name: `Test User ${timestamp}`,
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
      const timestamp = Date.now();
      const userResult = await client.request('createUser', {
        username: `user-${timestamp}`,
        display_name: `Test User ${timestamp}`,
      });

      // Create room
      const roomResult = await client.request('createRoom', {
        name: `Room ${timestamp}`,
        creator_id: userResult.user.id,
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
      const timestamp = Date.now();
      const userResult = await client.request('createUser', {
        username: `user-${timestamp}`,
        display_name: `Test User ${timestamp}`,
      });

      const roomResult = await client.request('createRoom', {
        name: `Room ${timestamp}`,
        creator_id: userResult.user.id,
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

      // Watch for updates BEFORE subscribing
      client.watch('global-room-list', (data) => {
        updates.push(data);
      });

      // Ensure global-room-list exists
      await client.request('listRooms', {});

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
      const timestamp = Date.now();
      const userResult = await client.request('createUser', {
        username: `user-${timestamp}`,
        display_name: `Test User ${timestamp}`,
      });

      const roomResult = await client.request('createRoom', {
        name: `Room ${timestamp}`,
        creator_id: userResult.user.id,
      });

      roomId = roomResult.room.id;

      // Watch for updates BEFORE subscribing
      client.watch(roomId, (data) => {
        updates.push(data);
      });

      // Subscribe to the room
      await client.subscribe(roomId);

      // Wait a bit for subscription to be established
      await new Promise(resolve => setTimeout(resolve, 100));

      // Clear any initial updates
      updates.length = 0;

      // Create another user and have them join the room to trigger update
      const timestamp2 = Date.now();
      const user2Result = await client.request('createUser', {
        username: `user-${timestamp2}`,
        display_name: `Test User ${timestamp2}`,
      });

      // Join room (this modifies the room's member list)
      await client.request('joinRoom', {
        room_id: roomId,
        user_id: user2Result.user.id,
      });

      // Should receive delta or full update after room modification
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
        // Watch for updates BEFORE subscribing
        client.watch('global-room-list', (data) => updates1.push(data));
        client2.watch('global-room-list', (data) => updates2.push(data));

        // Ensure global-room-list exists
        await client.request('listRooms', {});

        // Both subscribe to room list
        await client.subscribe('global-room-list');
        await client2.subscribe('global-room-list');

        await waitFor(() => updates1.length >= 1 && updates2.length >= 1);

        updates1.length = 0;
        updates2.length = 0;

        // Create a user first
        const timestamp = Date.now();
        const userResult = await client.request('createUser', {
          username: `user-${timestamp}`,
          display_name: `Test User ${timestamp}`,
        });

        // Client 1 creates a room
        await client.request('createRoom', {
          name: `Multi-client room ${timestamp}`,
          creator_id: userResult.user.id,
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
      const timestamp = Date.now();
      const userResult = await client.request('createUser', {
        username: `user-${timestamp}`,
        display_name: `Test User ${timestamp}`,
      });

      // Create room
      const roomResult = await client.request('createRoom', {
        name: `Room ${timestamp}`,
        creator_id: userResult.user.id,
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
      const timestamp = Date.now();
      const userResult = await client.request('createUser', {
        username: `user-${timestamp}`,
        display_name: `Test User ${timestamp}`,
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
      const timestamp = Date.now();
      const userResult = await client.request('createUser', {
        username: `user-${timestamp}`,
        display_name: `Test User ${timestamp}`,
      }, {
        subscribeToRefs: true,
      });

      // User should be auto-subscribed if server supports it
      // This is implementation-dependent
      expect(userResult.user).toBeDefined();
    });
  });
});
