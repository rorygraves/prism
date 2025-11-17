/**
 * End-to-end tests for multi-user chat using Prism protocol.
 *
 * These tests demonstrate:
 * - Multiple users interacting simultaneously
 * - Real-time message synchronization across clients
 * - Multiple chat rooms
 * - Object subscription and updates
 */

import { test, expect, Page } from '@playwright/test';

// Helper function to register a user
async function registerUser(page: Page, username: string, displayName: string) {
  await page.goto('/');

  // Wait for connection
  await expect(page.getByText('Connected')).toBeVisible({ timeout: 10000 });

  // Go to register tab
  await page.getByRole('tab', { name: 'Register' }).click();

  // Fill in registration form
  await page.getByLabel('Username').fill(username);
  await page.getByLabel('Display Name').fill(displayName);

  // Submit
  await page.getByRole('button', { name: 'Create User' }).click();

  // Wait for success
  await expect(page.getByText(`Logged in as: ${username}`)).toBeVisible();
}

// Helper function to create a room
async function createRoom(page: Page, roomName: string) {
  await page.getByRole('tab', { name: 'Rooms' }).click();
  await page.getByLabel('New Room Name').fill(roomName);
  await page.getByRole('button', { name: 'Create Room' }).click();
  await expect(page.getByText(roomName)).toBeVisible();
}

// Helper function to join a room
async function joinRoom(page: Page, roomName: string) {
  await page.getByRole('tab', { name: 'Rooms' }).click();
  await page.getByText(roomName).click();

  // Wait for chat view to load
  await expect(page.getByRole('button', { name: 'mdi-arrow-left' })).toBeVisible();
}

// Helper function to send a message
async function sendMessage(page: Page, message: string) {
  await page.getByLabel('Type a message...').fill(message);
  await page.getByLabel('Type a message...').press('Enter');
}

test.describe('Multi-user Chat E2E Tests', () => {
  test('should allow two users to chat in the same room', async ({ browser }) => {
    // Create two browser contexts (simulating two users)
    const context1 = await browser.newContext();
    const context2 = await browser.newContext();

    const page1 = await context1.newPage();
    const page2 = await context2.newPage();

    try {
      // User 1: Register and create a room
      await registerUser(page1, 'alice', 'Alice Smith');
      await createRoom(page1, 'General Discussion');

      // User 2: Register and join the room
      await registerUser(page2, 'bob', 'Bob Johnson');
      await joinRoom(page2, 'General Discussion');

      // User 1: Join the same room
      await joinRoom(page1, 'General Discussion');

      // User 1 sends a message
      await sendMessage(page1, 'Hello from Alice!');

      // User 2 should see the message (real-time sync)
      await expect(page2.getByText('Hello from Alice!')).toBeVisible({ timeout: 5000 });

      // User 2 sends a reply
      await sendMessage(page2, 'Hi Alice, this is Bob!');

      // User 1 should see the reply
      await expect(page1.getByText('Hi Alice, this is Bob!')).toBeVisible({ timeout: 5000 });

      // Verify both users see both messages
      await expect(page1.getByText('Hello from Alice!')).toBeVisible();
      await expect(page1.getByText('Hi Alice, this is Bob!')).toBeVisible();
      await expect(page2.getByText('Hello from Alice!')).toBeVisible();
      await expect(page2.getByText('Hi Alice, this is Bob!')).toBeVisible();
    } finally {
      await context1.close();
      await context2.close();
    }
  });

  test('should handle three users in different rooms', async ({ browser }) => {
    // Create three browser contexts
    const context1 = await browser.newContext();
    const context2 = await browser.newContext();
    const context3 = await browser.newContext();

    const page1 = await context1.newPage();
    const page2 = await context2.newPage();
    const page3 = await context3.newPage();

    try {
      // Register all users
      await registerUser(page1, 'charlie', 'Charlie Brown');
      await registerUser(page2, 'diana', 'Diana Prince');
      await registerUser(page3, 'eve', 'Eve Adams');

      // Charlie creates Room A
      await createRoom(page1, 'Room A');

      // Diana creates Room B
      await createRoom(page2, 'Room B');

      // Charlie and Diana join Room A
      await joinRoom(page1, 'Room A');
      await joinRoom(page2, 'Room A');

      // Eve joins Room B
      await joinRoom(page3, 'Room B');

      // Charlie sends message in Room A
      await sendMessage(page1, 'Message in Room A from Charlie');

      // Diana should see it
      await expect(page2.getByText('Message in Room A from Charlie')).toBeVisible({
        timeout: 5000,
      });

      // Eve should NOT see it (different room)
      await expect(page3.getByText('Message in Room A from Charlie')).not.toBeVisible({
        timeout: 2000,
      });

      // Eve sends message in Room B
      await sendMessage(page3, 'Message in Room B from Eve');

      // Neither Charlie nor Diana should see it (they're in Room A)
      await expect(page1.getByText('Message in Room B from Eve')).not.toBeVisible({
        timeout: 2000,
      });
      await expect(page2.getByText('Message in Room B from Eve')).not.toBeVisible({
        timeout: 2000,
      });
    } finally {
      await context1.close();
      await context2.close();
      await context3.close();
    }
  });

  test('should show real-time member count updates', async ({ browser }) => {
    const context1 = await browser.newContext();
    const context2 = await browser.newContext();
    const context3 = await browser.newContext();

    const page1 = await context1.newPage();
    const page2 = await context2.newPage();
    const page3 = await context3.newPage();

    try {
      // User 1 creates a room
      await registerUser(page1, 'user1', 'User One');
      await createRoom(page1, 'Test Room');
      await joinRoom(page1, 'Test Room');

      // Should show 1 member
      await expect(page1.getByText('1 members')).toBeVisible();

      // User 2 joins
      await registerUser(page2, 'user2', 'User Two');
      await joinRoom(page2, 'Test Room');

      // Both should see 2 members
      await expect(page1.getByText('2 members')).toBeVisible({ timeout: 5000 });
      await expect(page2.getByText('2 members')).toBeVisible();

      // User 3 joins
      await registerUser(page3, 'user3', 'User Three');
      await joinRoom(page3, 'Test Room');

      // All should see 3 members
      await expect(page1.getByText('3 members')).toBeVisible({ timeout: 5000 });
      await expect(page2.getByText('3 members')).toBeVisible({ timeout: 5000 });
      await expect(page3.getByText('3 members')).toBeVisible();
    } finally {
      await context1.close();
      await context2.close();
      await context3.close();
    }
  });

  test('should handle connection status', async ({ page }) => {
    await page.goto('/');

    // Should show connected status
    await expect(page.getByText('Connected')).toBeVisible({ timeout: 10000 });

    // The connection chip should be green
    const connectionChip = page.locator('.v-chip').filter({ hasText: 'Connected' });
    await expect(connectionChip).toBeVisible();
  });

  test('should support rapid message exchanges', async ({ browser }) => {
    const context1 = await browser.newContext();
    const context2 = await browser.newContext();

    const page1 = await context1.newPage();
    const page2 = await context2.newPage();

    try {
      // Set up two users in same room
      await registerUser(page1, 'fast1', 'Fast User 1');
      await registerUser(page2, 'fast2', 'Fast User 2');

      await createRoom(page1, 'Speed Test');
      await joinRoom(page1, 'Speed Test');
      await joinRoom(page2, 'Speed Test');

      // Send multiple messages rapidly
      const messages = ['Message 1', 'Message 2', 'Message 3', 'Message 4', 'Message 5'];

      for (const msg of messages) {
        await sendMessage(page1, msg);
        // Small delay to avoid overwhelming
        await page1.waitForTimeout(100);
      }

      // All messages should appear on both clients
      for (const msg of messages) {
        await expect(page1.getByText(msg)).toBeVisible({ timeout: 5000 });
        await expect(page2.getByText(msg)).toBeVisible({ timeout: 5000 });
      }
    } finally {
      await context1.close();
      await context2.close();
    }
  });
});
