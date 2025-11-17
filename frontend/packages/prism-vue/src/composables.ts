/**
 * Vue 3 composables for Prism protocol.
 */

import { ref, onUnmounted, Ref, inject, provide, InjectionKey } from 'vue';
import { PrismClient, RequestOptions } from '@prism/client';

/**
 * Injection key for Prism client.
 */
export const PrismClientKey: InjectionKey<PrismClient> = Symbol('PrismClient');

/**
 * Provide Prism client to child components.
 */
export function providePrismClient(client: PrismClient): void {
  provide(PrismClientKey, client);
}

/**
 * Inject Prism client from parent components.
 */
export function usePrismClient(): PrismClient {
  const client = inject(PrismClientKey);
  if (!client) {
    throw new Error('Prism client not provided. Use providePrismClient in a parent component.');
  }
  return client;
}

/**
 * Subscribe to and watch a Prism object.
 */
export function usePrismObject<T = any>(
  objectId: Ref<string> | string,
  filterType: string = 'default'
): {
  data: Ref<T | null>;
  loading: Ref<boolean>;
  error: Ref<Error | null>;
} {
  const client = usePrismClient();
  const data = ref<T | null>(null) as Ref<T | null>;
  const loading = ref(true);
  const error = ref<Error | null>(null);

  const id = typeof objectId === 'string' ? objectId : objectId.value;

  // Check cache first
  const cached = client.getCached(id);
  if (cached) {
    data.value = cached as T;
    loading.value = false;
  }

  // Subscribe to object
  client
    .subscribe(id, filterType)
    .then(() => {
      loading.value = false;
    })
    .catch((err) => {
      error.value = err;
      loading.value = false;
    });

  // Watch for updates
  const unwatch = client.watch(id, (updated) => {
    data.value = updated as T;
  });

  // Cleanup on unmount
  onUnmounted(() => {
    unwatch();
    client.unsubscribe(id).catch(console.error);
  });

  return { data, loading, error };
}

/**
 * Make a Prism request with smart hydration.
 */
export function usePrismRequest<T = any>(options?: RequestOptions): {
  data: Ref<T | null>;
  loading: Ref<boolean>;
  error: Ref<Error | null>;
  execute: (requestType: string, payload: Record<string, any>) => Promise<void>;
} {
  const client = usePrismClient();
  const data = ref<T | null>(null) as Ref<T | null>;
  const loading = ref(false);
  const error = ref<Error | null>(null);

  const execute = async (
    requestType: string,
    payload: Record<string, any>
  ): Promise<void> => {
    loading.value = true;
    error.value = null;

    try {
      const result = await client.request<T>(requestType, payload, options);
      data.value = result;
    } catch (err) {
      error.value = err as Error;
    } finally {
      loading.value = false;
    }
  };

  return { data, loading, error, execute };
}

/**
 * Watch multiple Prism objects.
 */
export function usePrismObjects<T = any>(
  objectIds: Ref<string[]> | string[],
  filterType: string = 'default'
): {
  objects: Ref<Map<string, T>>;
  loading: Ref<boolean>;
  error: Ref<Error | null>;
} {
  const client = usePrismClient();
  const objects = ref(new Map<string, T>()) as Ref<Map<string, T>>;
  const loading = ref(true);
  const error = ref<Error | null>(null);

  const ids = Array.isArray(objectIds) ? objectIds : objectIds.value;
  const unwatchFns: Array<() => void> = [];

  // Subscribe to all objects
  const subscribeAll = async () => {
    try {
      for (const id of ids) {
        // Check cache first
        const cached = client.getCached(id);
        if (cached) {
          objects.value.set(id, cached as T);
        }

        // Subscribe
        await client.subscribe(id, filterType);

        // Watch for updates
        const unwatch = client.watch(id, (updated) => {
          objects.value.set(id, updated as T);
        });
        unwatchFns.push(unwatch);
      }
      loading.value = false;
    } catch (err) {
      error.value = err as Error;
      loading.value = false;
    }
  };

  subscribeAll();

  // Cleanup on unmount
  onUnmounted(() => {
    unwatchFns.forEach((fn) => fn());
    ids.forEach((id) => {
      client.unsubscribe(id).catch(console.error);
    });
  });

  return { objects, loading, error };
}

/**
 * Get connection state.
 */
export function usePrismConnection(): {
  connected: Ref<boolean>;
} {
  const client = usePrismClient();
  const connected = ref(client.isConnected());

  client.onConnected(() => {
    connected.value = true;
  });

  client.onDisconnected(() => {
    connected.value = false;
  });

  return { connected };
}
