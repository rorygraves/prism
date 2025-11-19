<template>
  <v-container class="fill-height" fluid>
    <v-row justify="center" align="center">
      <v-col cols="12" sm="10" md="8" lg="6">
        <!-- Welcome Card -->
        <v-card v-if="!currentUserId" class="mb-4">
          <v-card-title class="text-h4 text-center py-6">
            Welcome to Prism Chat
          </v-card-title>
          <v-card-text>
            <v-form @submit.prevent="handleLogin">
              <v-text-field
                v-model="username"
                label="Enter your username"
                :rules="[rules.required]"
                prepend-icon="mdi-account"
                autofocus
                :loading="loading"
                :disabled="loading"
              />
              <v-btn
                type="submit"
                color="primary"
                block
                size="large"
                :loading="loading"
              >
                Start Chatting
              </v-btn>
            </v-form>
          </v-card-text>
        </v-card>

        <!-- Rooms Card -->
        <v-card v-else>
          <v-card-title class="d-flex align-center">
            <span>Chat Rooms</span>
            <v-spacer />
            <v-chip color="success" prepend-icon="mdi-account">
              {{ currentUsername }}
            </v-chip>
          </v-card-title>

          <v-card-text>
            <!-- Create Room -->
            <v-text-field
              v-model="newRoomName"
              label="Create a new room"
              prepend-icon="mdi-chat-plus"
              @keyup.enter="handleCreateRoom"
              :disabled="loading"
            >
              <template #append>
                <v-btn
                  color="primary"
                  @click="handleCreateRoom"
                  :loading="loading"
                  :disabled="!newRoomName"
                >
                  Create
                </v-btn>
              </template>
            </v-text-field>

            <v-divider class="my-4" />

            <!-- Room List -->
            <div v-if="availableRooms.length === 0">
              <v-alert type="info" variant="tonal">
                No rooms yet. Create one to get started!
              </v-alert>
            </div>
            <v-list v-else>
              <v-list-item
                v-for="room in availableRooms"
                :key="room.id"
                @click="handleJoinRoom(room.id)"
                class="room-item"
              >
                <template #prepend>
                  <v-icon>mdi-chat</v-icon>
                </template>
                <v-list-item-title>{{ room.name }}</v-list-item-title>
                <v-list-item-subtitle>
                  {{ room.member_ids?.length || 0 }} members
                </v-list-item-subtitle>
                <template #append>
                  <v-btn color="primary" variant="text">Join</v-btn>
                </template>
              </v-list-item>
            </v-list>
          </v-card-text>
        </v-card>

        <!-- Status Messages -->
        <v-snackbar v-model="showError" color="error" :timeout="3000">
          {{ errorMessage }}
        </v-snackbar>

        <v-snackbar v-model="showSuccess" color="success" :timeout="2000">
          {{ successMessage }}
        </v-snackbar>
      </v-col>
    </v-row>
  </v-container>
</template>

<script setup lang="ts">
import { ref, onMounted, watch, computed } from 'vue';
import { useRouter } from 'vue-router';
import { usePrismRequest, usePrismObject, usePrismObjects } from '@prism/vue';
import type { ChatRoom } from '../types';

const router = useRouter();
const loading = ref(false);
const showError = ref(false);
const showSuccess = ref(false);
const errorMessage = ref('');
const successMessage = ref('');

// User state
const currentUserId = ref<string | null>(localStorage.getItem('userId'));
const currentUsername = ref<string | null>(localStorage.getItem('username'));
const username = ref('');

// Room state
const newRoomName = ref('');
const roomIds = ref<string[]>([]);
const roomListId = ref<string>('global-room-list');

// Validation rules
const rules = {
  required: (v: string) => !!v || 'Username is required',
};

// Request composable
const { execute } = usePrismRequest();

// Subscribe to the room list object (contains room IDs)
const { data: roomListData } = usePrismObject<{ room_ids: string[] }>(roomListId, 'default');

// Watch room list for changes and subscribe to individual rooms
watch(roomListData, (newData) => {
  console.log('[ROOM_LIST] Room list updated:', newData);
  if (newData && newData.room_ids) {
    roomIds.value = newData.room_ids;
    console.log('[ROOM_LIST] Updated room IDs:', roomIds.value);
  }
}, { immediate: true });

// Subscribe to all individual room objects
const { objects: roomObjects } = usePrismObjects<ChatRoom>(roomIds, 'default');

// Convert room objects map to array for display
const availableRooms = computed(() => {
  const rooms = Array.from(roomObjects.value.values());
  console.log('[ROOM_LIST] Available rooms computed:', rooms);
  return rooms;
});

// Handle login - auto-create user
const handleLogin = async () => {
  console.log('[LOGIN] Starting login for username:', username.value);

  if (!username.value.trim()) {
    console.log('[LOGIN] Empty username, aborting');
    return;
  }

  loading.value = true;
  try {
    console.log('[LOGIN] Calling createUser with:', {
      username: username.value,
      display_name: username.value,
      avatar_url: null,
    });

    // Auto-create user in backend
    const result = await execute('createUser', {
      username: username.value,
      display_name: username.value,
      avatar_url: null,
    });

    console.log('[LOGIN] createUser result:', result);

    if (result && result.user) {
      currentUserId.value = result.user.id;
      currentUsername.value = username.value;
      localStorage.setItem('userId', result.user.id);
      localStorage.setItem('username', username.value);

      console.log('[LOGIN] User logged in successfully:', {
        userId: result.user.id,
        username: username.value,
      });

      successMessage.value = `Welcome, ${username.value}!`;
      showSuccess.value = true;

      // Rooms are automatically loaded via subscriptions
      console.log('[LOGIN] Rooms will load automatically via Prism subscriptions');
    } else {
      console.error('[LOGIN] No user in result:', result);
    }
  } catch (error: any) {
    console.error('[LOGIN] Login failed:', error);
    errorMessage.value = error.message || 'Failed to login. Please try again.';
    showError.value = true;
  } finally {
    loading.value = false;
  }
};

// Handle room creation
const handleCreateRoom = async () => {
  console.log('[CREATE_ROOM] Starting room creation');
  console.log('[CREATE_ROOM] Room name:', newRoomName.value);
  console.log('[CREATE_ROOM] Current user ID:', currentUserId.value);
  console.log('[CREATE_ROOM] Current username:', currentUsername.value);

  if (!newRoomName.value.trim()) {
    console.log('[CREATE_ROOM] Empty room name, aborting');
    errorMessage.value = 'Please enter a room name';
    showError.value = true;
    return;
  }

  if (!currentUserId.value) {
    console.log('[CREATE_ROOM] No user ID, aborting');
    errorMessage.value = 'You must be logged in to create a room';
    showError.value = true;
    return;
  }

  loading.value = true;
  try {
    console.log('[CREATE_ROOM] Calling createRoom with:', {
      name: newRoomName.value,
      description: null,
      creator_id: currentUserId.value,
    });

    const result = await execute('createRoom', {
      name: newRoomName.value,
      description: null,
      creator_id: currentUserId.value,
    });

    console.log('[CREATE_ROOM] createRoom result:', result);

    if (result && result.room) {
      console.log('[CREATE_ROOM] Room created successfully:', result.room);
      successMessage.value = `Room "${newRoomName.value}" created!`;
      showSuccess.value = true;
      newRoomName.value = '';

      // Room list will be automatically updated via subscription
      console.log('[CREATE_ROOM] Room list will auto-update via Prism subscription');
    } else {
      console.error('[CREATE_ROOM] No room in result:', result);
      errorMessage.value = 'Room creation returned unexpected result';
      showError.value = true;
    }
  } catch (error: any) {
    console.error('[CREATE_ROOM] Room creation failed:', error);
    console.error('[CREATE_ROOM] Error details:', {
      message: error.message,
      stack: error.stack,
      error: error,
    });
    errorMessage.value = error.message || 'Failed to create room. Please try again.';
    showError.value = true;
  } finally {
    loading.value = false;
    console.log('[CREATE_ROOM] Room creation finished');
  }
};

// Handle joining a room
const handleJoinRoom = async (roomId: string) => {
  if (!currentUserId.value) return;

  loading.value = true;
  try {
    await execute('joinRoom', {
      room_id: roomId,
      user_id: currentUserId.value,
    });

    // Navigate to chat room
    router.push({ name: 'Chat', params: { roomId } });
  } catch (error: any) {
    console.error('Failed to join room:', error);
    errorMessage.value = error.message || 'Failed to join room. Please try again.';
    showError.value = true;
  } finally {
    loading.value = false;
  }
};

// On mount, check if already logged in
onMounted(() => {
  console.log('[MOUNT] Component mounted');
  console.log('[MOUNT] Current user ID from localStorage:', localStorage.getItem('userId'));
  console.log('[MOUNT] Current username from localStorage:', localStorage.getItem('username'));
  console.log('[MOUNT] currentUserId.value:', currentUserId.value);
  console.log('[MOUNT] currentUsername.value:', currentUsername.value);
  console.log('[MOUNT] Room subscriptions are active - rooms will load automatically');

  if (currentUserId.value) {
    console.log('[MOUNT] User already logged in');
  } else {
    console.log('[MOUNT] No user logged in, showing login screen');
  }
});
</script>

<style scoped>
.fill-height {
  min-height: 100vh;
}

.room-item {
  cursor: pointer;
  transition: background-color 0.2s;
}

.room-item:hover {
  background-color: rgba(0, 0, 0, 0.04);
}
</style>
