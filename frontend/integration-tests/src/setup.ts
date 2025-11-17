/**
 * Test setup utilities for integration tests.
 *
 * These tests connect to a REAL backend server.
 * Make sure the backend is running before executing tests.
 */

import { spawn, ChildProcess } from 'child_process';
import { setTimeout as sleep } from 'timers/promises';

const BACKEND_URL = process.env.BACKEND_URL || 'http://localhost:8000';
const WS_URL = process.env.WS_URL || 'ws://localhost:8000/ws';

let backendProcess: ChildProcess | null = null;

/**
 * Check if backend is running by hitting health endpoint.
 */
async function isBackendRunning(): Promise<boolean> {
  try {
    const response = await fetch(`${BACKEND_URL}/health`);
    return response.ok;
  } catch {
    return false;
  }
}

/**
 * Start the backend server if not already running.
 */
export async function startBackend(): Promise<void> {
  // Check if already running
  if (await isBackendRunning()) {
    console.log('Backend already running');
    return;
  }

  console.log('Starting backend server...');

  // Start backend using poetry
  backendProcess = spawn('poetry', ['run', 'python', '-m', 'chat_demo.main'], {
    cwd: '../../backend',
    stdio: 'pipe',
  });

  // Wait for backend to be ready
  let attempts = 0;
  while (attempts < 30) {
    await sleep(1000);
    if (await isBackendRunning()) {
      console.log('Backend server ready!');
      return;
    }
    attempts++;
  }

  throw new Error('Backend server failed to start');
}

/**
 * Stop the backend server if we started it.
 */
export async function stopBackend(): Promise<void> {
  if (backendProcess) {
    backendProcess.kill();
    backendProcess = null;
    await sleep(1000);
  }
}

/**
 * Get the WebSocket URL for connecting to backend.
 */
export function getWebSocketUrl(): string {
  return WS_URL;
}

/**
 * Get the HTTP URL for the backend.
 */
export function getHttpUrl(): string {
  return BACKEND_URL;
}

/**
 * Wait for a condition to become true.
 */
export async function waitFor(
  condition: () => boolean | Promise<boolean>,
  timeoutMs: number = 5000,
  intervalMs: number = 100
): Promise<void> {
  const startTime = Date.now();

  while (Date.now() - startTime < timeoutMs) {
    if (await condition()) {
      return;
    }
    await sleep(intervalMs);
  }

  throw new Error('Timeout waiting for condition');
}
