/**
 * Setup test environment for integration tests.
 * Provides WebSocket polyfill for Node.js environment.
 */

import { WebSocket } from 'ws';

// Add WebSocket to global scope for Node.js environment
(global as any).WebSocket = WebSocket;
