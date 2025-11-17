/**
 * Prism client manager for WebSocket connections and object management.
 */

import { applyPatch } from 'fast-json-patch';
import type {
  CachedObject,
  ClientMessage,
  DeltaMessage,
  FullObjectMessage,
  HydratedReference,
  ObjectCallback,
  RequestMessage,
  RequestOptions,
  ResponseMessage,
  ServerMessage,
  SubscribeMessage,
  Subscription,
  UnsubscribeMessage,
  UpdateFilterMessage,
} from './types.js';

/**
 * Request callback for handling responses.
 */
interface PendingRequest<T = any> {
  resolve: (value: T) => void;
  reject: (error: Error) => void;
}

/**
 * Main Prism client for managing real-time object synchronization.
 */
export class PrismClient {
  private ws: WebSocket | null = null;
  private url: string;
  private objects = new Map<string, CachedObject>();
  private subscriptions = new Map<string, Subscription>();
  private listeners = new Map<string, Set<ObjectCallback>>();
  private pendingRequests = new Map<string, PendingRequest>();
  private requestIdCounter = 0;
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 5;
  private reconnectDelay = 1000;
  private reconnecting = false;
  private connected = false;
  private onConnectedCallbacks: Array<() => void> = [];
  private onDisconnectedCallbacks: Array<() => void> = [];

  constructor(url: string) {
    this.url = url;
  }

  /**
   * Connect to the Prism server.
   */
  async connect(): Promise<void> {
    return new Promise((resolve, reject) => {
      this.ws = new WebSocket(this.url);

      this.ws.onopen = () => {
        this.connected = true;
        this.reconnectAttempts = 0;
        this.reconnecting = false;
        console.log('Prism client connected');

        // Trigger callbacks
        this.onConnectedCallbacks.forEach((cb) => cb());

        resolve();
      };

      this.ws.onerror = (error) => {
        console.error('WebSocket error:', error);
        reject(new Error('WebSocket connection failed'));
      };

      this.ws.onclose = () => {
        this.connected = false;
        console.log('Prism client disconnected');

        // Trigger callbacks
        this.onDisconnectedCallbacks.forEach((cb) => cb());

        // Attempt reconnection
        this.handleReconnect();
      };

      this.ws.onmessage = (event) => {
        this.handleMessage(JSON.parse(event.data));
      };
    });
  }

  /**
   * Disconnect from the server.
   */
  disconnect(): void {
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
  }

  /**
   * Check if client is connected.
   */
  isConnected(): boolean {
    return this.connected;
  }

  /**
   * Register callback for connection events.
   */
  onConnected(callback: () => void): void {
    this.onConnectedCallbacks.push(callback);
  }

  /**
   * Register callback for disconnection events.
   */
  onDisconnected(callback: () => void): void {
    this.onDisconnectedCallbacks.push(callback);
  }

  /**
   * Subscribe to an object.
   */
  async subscribe(
    objectId: string,
    filterType: string = 'default',
    temporary: boolean = false
  ): Promise<void> {
    console.log(`[PrismClient] Subscribing to ${objectId} with filter ${filterType}, temporary: ${temporary}`);

    const message: SubscribeMessage = {
      type: 'subscribe',
      object_id: objectId,
      filter_type: filterType,
      temporary,
    };

    try {
      await this.send(message);
      console.log(`[PrismClient] Subscribe message sent for ${objectId}`);
    } catch (error) {
      console.error(`[PrismClient] Failed to send subscribe message for ${objectId}:`, error);
      throw error;
    }

    if (!temporary) {
      this.subscriptions.set(objectId, {
        objectId,
        filterType,
        currentVersion: 0,
        temporary: false,
      });
    }
  }

  /**
   * Unsubscribe from an object.
   */
  async unsubscribe(objectId: string): Promise<void> {
    const message: UnsubscribeMessage = {
      type: 'unsubscribe',
      object_id: objectId,
    };

    await this.send(message);
    this.subscriptions.delete(objectId);
  }

  /**
   * Update the filter for an existing subscription.
   * The server will re-send the object with the new filter applied.
   */
  async updateFilter(
    objectId: string,
    filterType: string,
    filterParams?: Record<string, any>
  ): Promise<void> {
    // Check if subscribed
    const subscription = this.subscriptions.get(objectId);
    if (!subscription) {
      throw new Error(`Not subscribed to object ${objectId}`);
    }

    const message: UpdateFilterMessage = {
      type: 'updateFilter',
      object_id: objectId,
      filter_type: filterType,
      filter_params: filterParams,
    };

    await this.send(message);

    // Update local subscription info
    subscription.filterType = filterType;
    subscription.filterParams = filterParams;
  }

  /**
   * Make a request with automatic reference hydration.
   */
  async request<T = any>(
    requestType: string,
    payload: Record<string, any>,
    options?: RequestOptions
  ): Promise<T> {
    const requestId = `req-${++this.requestIdCounter}`;

    const message: RequestMessage = {
      type: 'request',
      request_id: requestId,
      request_type: requestType,
      payload,
      options: options || { hydrateRefs: true },
    };

    const promise = new Promise<T>((resolve, reject) => {
      this.pendingRequests.set(requestId, { resolve, reject });
    });

    try {
      await this.send(message);
    } catch (error) {
      // Clean up pending request if send fails
      this.pendingRequests.delete(requestId);
      throw error;
    }

    return promise;
  }

  /**
   * Watch an object for changes.
   */
  watch(objectId: string, callback: ObjectCallback): () => void {
    if (!this.listeners.has(objectId)) {
      this.listeners.set(objectId, new Set());
    }

    this.listeners.get(objectId)!.add(callback);

    // Return unwatch function
    return () => {
      const listeners = this.listeners.get(objectId);
      if (listeners) {
        listeners.delete(callback);
        if (listeners.size === 0) {
          this.listeners.delete(objectId);
        }
      }
    };
  }

  /**
   * Get cached object if available.
   */
  getCached(objectId: string): Record<string, any> | null {
    const cached = this.objects.get(objectId);
    return cached ? cached.data : null;
  }

  /**
   * Send a message to the server.
   */
  private async send(message: ClientMessage): Promise<void> {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
      throw new Error('WebSocket not connected');
    }

    this.ws.send(JSON.stringify(message));
  }

  /**
   * Handle incoming message from server.
   */
  private handleMessage(message: ServerMessage): void {
    switch (message.type) {
      case 'fullObject':
        this.handleFullObject(message);
        break;
      case 'delta':
        this.handleDelta(message);
        break;
      case 'response':
        this.handleResponse(message);
        break;
      case 'error':
        this.handleError(message);
        break;
    }
  }

  /**
   * Handle full object message.
   */
  private handleFullObject(message: FullObjectMessage): void {
    this.objects.set(message.id, {
      version: message.version,
      data: message.data,
    });

    this.notifyListeners(message.id, message.data);
  }

  /**
   * Handle delta message.
   */
  private handleDelta(message: DeltaMessage): void {
    const cached = this.objects.get(message.id);

    if (!cached) {
      console.warn(`Received delta for unknown object: ${message.id}`);
      return;
    }

    if (cached.version !== message.from_version) {
      console.warn(
        `Version mismatch for ${message.id}: expected ${message.from_version}, got ${cached.version}`
      );
      return;
    }

    // Apply delta
    const updated = applyPatch(cached.data, message.patches, true, false).newDocument;

    this.objects.set(message.id, {
      version: message.to_version,
      data: updated,
    });

    this.notifyListeners(message.id, updated);
  }

  /**
   * Handle response message.
   */
  private handleResponse(message: ResponseMessage): void {
    // Process hydrated references first
    if (message.hydrated) {
      for (const ref of message.hydrated) {
        this.processHydratedReference(ref);
      }
    }

    // Resolve references in response data
    const resolved = this.resolveReferences(message.data);

    // Complete pending request
    const pending = this.pendingRequests.get(message.request_id);
    if (pending) {
      if (message.success) {
        pending.resolve(resolved);
      } else {
        pending.reject(new Error(message.error || 'Request failed'));
      }
      this.pendingRequests.delete(message.request_id);
    }
  }

  /**
   * Handle error message.
   */
  private handleError(message: { code: string; message: string; request_id?: string }): void {
    console.error(`Prism error [${message.code}]: ${message.message}`);

    if (message.request_id) {
      const pending = this.pendingRequests.get(message.request_id);
      if (pending) {
        pending.reject(new Error(message.message));
        this.pendingRequests.delete(message.request_id);
      }
    }
  }

  /**
   * Process a hydrated reference from the server.
   */
  private processHydratedReference(ref: HydratedReference): void {
    if (ref.data) {
      // Full object provided
      this.objects.set(ref.id, {
        version: ref.version,
        data: ref.data,
      });
      this.notifyListeners(ref.id, ref.data);
    } else if (ref.delta) {
      // Apply delta
      const cached = this.objects.get(ref.id);
      if (cached) {
        const updated = applyPatch(cached.data, ref.delta.patches, true, false).newDocument;
        this.objects.set(ref.id, {
          version: ref.version,
          data: updated,
        });
        this.notifyListeners(ref.id, updated);
      }
    }
    // If cached=true, we already have it
  }

  /**
   * Resolve object references in data structure.
   */
  private resolveReferences(data: any): any {
    if (data === null || data === undefined) {
      return data;
    }

    // Check if this is an object reference
    if (this.isObjectReference(data)) {
      const cached = this.objects.get(data.id);
      return cached ? cached.data : null;
    }

    // Recurse into arrays
    if (Array.isArray(data)) {
      return data.map((item) => this.resolveReferences(item));
    }

    // Recurse into objects
    if (typeof data === 'object') {
      const resolved: Record<string, any> = {};
      for (const [key, value] of Object.entries(data)) {
        resolved[key] = this.resolveReferences(value);
      }
      return resolved;
    }

    return data;
  }

  /**
   * Check if data looks like an ObjectReference.
   */
  private isObjectReference(data: any): data is { id: string; version: number } {
    return (
      typeof data === 'object' &&
      data !== null &&
      'id' in data &&
      'version' in data &&
      typeof data.version === 'number'
    );
  }

  /**
   * Notify listeners of object update.
   */
  private notifyListeners(objectId: string, data: Record<string, any>): void {
    const listeners = this.listeners.get(objectId);
    if (listeners) {
      listeners.forEach((callback) => callback(data));
    }
  }

  /**
   * Handle reconnection logic.
   */
  private handleReconnect(): void {
    if (this.reconnecting) return;

    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      console.error('Max reconnection attempts reached');
      return;
    }

    this.reconnecting = true;
    this.reconnectAttempts++;

    const delay = this.reconnectDelay * Math.pow(2, this.reconnectAttempts - 1);

    console.log(`Reconnecting in ${delay}ms (attempt ${this.reconnectAttempts})`);

    setTimeout(async () => {
      try {
        await this.connect();
        await this.syncState();
      } catch (error) {
        console.error('Reconnection failed:', error);
        this.reconnecting = false;
      }
    }, delay);
  }

  /**
   * Sync state after reconnection.
   */
  private async syncState(): Promise<void> {
    const states = Array.from(this.subscriptions.values()).map((sub) => ({
      id: sub.objectId,
      version: this.objects.get(sub.objectId)?.version || 0,
      filter_type: sub.filterType,
    }));

    if (states.length > 0) {
      await this.send({
        type: 'sync',
        states,
      });
    }
  }
}
