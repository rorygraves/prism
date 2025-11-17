<template>
  <v-container class="fill-height" fluid>
    <v-row justify="center" align="center">
      <v-col cols="12" sm="10" md="8" lg="6">
        <v-card>
          <v-card-title class="text-h4 text-center py-6">
            Welcome to Prism Chat
          </v-card-title>

          <v-card-text>
            <v-tabs v-model="tab" fixed-tabs>
              <v-tab value="login">Login</v-tab>
              <v-tab value="register">Register</v-tab>
              <v-tab value="rooms">Rooms</v-tab>
            </v-tabs>

            <v-window v-model="tab" class="mt-4">
              <!-- Login Tab (simplified - just stores username) -->
              <v-window-item value="login">
                <v-form @submit.prevent="handleLogin">
                  <v-text-field
                    v-model="loginUsername"
                    label="Username"
                    :rules="[rules.required]"
                    prepend-icon="mdi-account"
                  />
                  <v-btn type="submit" color="primary" block :loading="loading">
                    Set Username
                  </v-btn>
                </v-form>
              </v-window-item>

              <!-- Register Tab -->
              <v-window-item value="register">
                <v-form @submit.prevent="handleRegister">
                  <v-text-field
                    v-model="registerForm.username"
                    label="Username"
                    :rules="[rules.required]"
                    prepend-icon="mdi-account"
                  />
                  <v-text-field
                    v-model="registerForm.displayName"
                    label="Display Name"
                    :rules="[rules.required]"
                    prepend-icon="mdi-card-account-details"
                  />
                  <v-text-field
                    v-model="registerForm.avatarUrl"
                    label="Avatar URL (optional)"
                    prepend-icon="mdi-image"
                  />
                  <v-btn type="submit" color="primary" block :loading="loading">
                    Create User
                  </v-btn>
                </v-form>
              </v-window-item>

              <!-- Rooms Tab -->
              <v-window-item value="rooms">
                <div v-if="!currentUserId">
                  <v-alert type="info" class="mb-4">
                    Please login or register first
                  </v-alert>
                </div>
                <div v-else>
                  <v-text-field
                    v-model="newRoomName"
                    label="New Room Name"
                    prepend-icon="mdi-chat"
                    @keyup.enter="handleCreateRoom"
                  />
                  <v-btn color="primary" block @click="handleCreateRoom" :loading="loading">
                    Create Room
                  </v-btn>

                  <v-divider class="my-4" />

                  <div v-if="availableRooms.length === 0">
                    <v-alert type="info">
                      No rooms available. Create one to get started!
                    </v-alert>
                  </div>
                  <v-list v-else>
                    <v-list-item
                      v-for="room in availableRooms"
                      :key="room.id"
                      @click="handleJoinRoom(room.id)"
                    >
                      <v-list-item-title>{{ room.name }}</v-list-item-title>
                      <v-list-item-subtitle>
                        {{ room.member_ids?.length || 0 }} members
                      </v-list-item-subtitle>
                      <template #append>
                        <v-btn color="primary" variant="text">Join</v-btn>
                      </template>
                    </v-list-item>
                  </v-list>
                </div>
              </v-window-item>
            </v-window>
          </v-card-text>

          <v-card-actions v-if="currentUserId" class="px-4 pb-4">
            <v-chip color="success">Logged in as: {{ currentUsername }}</v-chip>
          </v-card-actions>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { usePrismRequest } from '@prism/vue';
import type { ChatRoom } from '../types';

const router = useRouter();
const tab = ref('login');
const loading = ref(false);

// Current user state (simplified - would use proper auth in production)
const currentUserId = ref<string | null>(localStorage.getItem('userId'));
const currentUsername = ref<string | null>(localStorage.getItem('username'));

// Login form
const loginUsername = ref('');

// Register form
const registerForm = ref({
  username: '',
  displayName: '',
  avatarUrl: '',
});

// Room creation
const newRoomName = ref('');
const availableRooms = ref<ChatRoom[]>([]);

// Validation rules
const rules = {
  required: (v: string) => !!v || 'Required',
};

// Request composable
const { execute } = usePrismRequest();

// Handle login (simplified)
const handleLogin = () => {
  if (loginUsername.value) {
    currentUsername.value = loginUsername.value;
    currentUserId.value = `temp-${Date.now()}`;
    localStorage.setItem('username', loginUsername.value);
    localStorage.setItem('userId', currentUserId.value);
    tab.value = 'rooms';
  }
};

// Handle user registration
const handleRegister = async () => {
  loading.value = true;
  try {
    const result = await execute('createUser', {
      username: registerForm.value.username,
      display_name: registerForm.value.displayName,
      avatar_url: registerForm.value.avatarUrl || null,
    });

    if (result && result.user) {
      currentUserId.value = result.user.id;
      currentUsername.value = registerForm.value.username;
      localStorage.setItem('userId', result.user.id);
      localStorage.setItem('username', registerForm.value.username);
      tab.value = 'rooms';
    }
  } catch (error) {
    console.error('Registration failed:', error);
    alert('Registration failed. Please try again.');
  } finally {
    loading.value = false;
  }
};

// Handle room creation
const handleCreateRoom = async () => {
  if (!newRoomName.value || !currentUserId.value) return;

  loading.value = true;
  try {
    const result = await execute('createRoom', {
      name: newRoomName.value,
      description: null,
      creator_id: currentUserId.value,
    });

    if (result && result.room) {
      newRoomName.value = '';
      // Room created, could navigate to it or refresh list
      availableRooms.value.push(result.room);
    }
  } catch (error) {
    console.error('Room creation failed:', error);
    alert('Room creation failed. Please try again.');
  } finally {
    loading.value = false;
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
  } catch (error) {
    console.error('Failed to join room:', error);
    alert('Failed to join room. Please try again.');
  } finally {
    loading.value = false;
  }
};
</script>

<style scoped>
.fill-height {
  height: 100%;
}
</style>
