/**
 * Core Prism types for TypeScript client.
 */

import type { Operation } from 'fast-json-patch';

/**
 * Versioned object with immutable data.
 */
export interface PrismObject {
  id: string;
  version: number;
  data: Record<string, any>;
}

/**
 * Delta between two versions using JSON Patch.
 */
export interface Delta {
  objectId: string;
  fromVersion: number;
  toVersion: number;
  patches: Operation[];
}

/**
 * Reference to an object that may need hydration.
 */
export interface ObjectReference {
  id: string;
  version: number;
  filterType?: string;
  subscribe?: boolean;
}

/**
 * Hydrated reference result from server.
 */
export interface HydratedReference {
  id: string;
  version: number;
  data?: Record<string, any>;
  delta?: Delta;
  cached?: boolean;
}

/**
 * Options for requests.
 */
export interface RequestOptions {
  hydrateRefs?: boolean;
  subscribeToRefs?: boolean;
  filterType?: string;
}

/**
 * Subscription to an object.
 */
export interface Subscription {
  objectId: string;
  filterType: string;
  filterParams?: Record<string, any>;
  currentVersion: number;
  temporary?: boolean;
}

// Client -> Server Messages

export interface SubscribeMessage {
  type: 'subscribe';
  object_id: string;
  filter_type?: string;
  filter_params?: Record<string, any>;
  temporary?: boolean;
}

export interface UnsubscribeMessage {
  type: 'unsubscribe';
  object_id: string;
}

export interface SyncStateItem {
  id: string;
  version: number;
  filter_type: string;
}

export interface SyncMessage {
  type: 'sync';
  states: SyncStateItem[];
}

export interface RequestMessage {
  type: 'request';
  request_id: string;
  request_type: string;
  payload: Record<string, any>;
  options?: RequestOptions;
}

export interface UpdateFilterMessage {
  type: 'updateFilter';
  object_id: string;
  filter_type: string;
  filter_params?: Record<string, any>;
}

export type ClientMessage =
  | SubscribeMessage
  | UnsubscribeMessage
  | SyncMessage
  | RequestMessage
  | UpdateFilterMessage;

// Server -> Client Messages

export interface FullObjectMessage {
  type: 'fullObject';
  id: string;
  version: number;
  data: Record<string, any>;
  filtered?: boolean;
  filter_type?: string;
}

export interface DeltaMessage {
  type: 'delta';
  id: string;
  from_version: number;
  to_version: number;
  patches: Operation[];
  filter_type?: string;
}

export interface ResponseMessage {
  type: 'response';
  request_id: string;
  success: boolean;
  data?: any;
  hydrated?: HydratedReference[];
  error?: string;
}

export interface ErrorMessage {
  type: 'error';
  code: string;
  message: string;
  object_id?: string;
  request_id?: string;
}

export type ServerMessage =
  | FullObjectMessage
  | DeltaMessage
  | ResponseMessage
  | ErrorMessage;

/**
 * Callback for object updates.
 */
export type ObjectCallback = (obj: Record<string, any>) => void;

/**
 * Cached object state.
 */
export interface CachedObject {
  version: number;
  data: Record<string, any>;
}
