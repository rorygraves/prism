<template>
  <v-container class="fill-height pa-0" fluid>
    <v-row no-gutters class="fill-height">
      <v-col cols="12" class="d-flex flex-column fill-height">
        <!-- Header -->
        <v-card class="flex-0-0">
          <v-card-title class="d-flex align-center">
            <v-btn icon @click="$router.push('/')">
              <v-icon>mdi-arrow-left</v-icon>
            </v-btn>
            <span class="ml-2">{{ roomData?.name || 'Loading...' }}</span>
            <v-spacer />
            <v-chip v-if="roomData" prepend-icon="mdi-account-multiple">
              {{ roomData.member_ids?.length || 0 }} members
            </v-chip>
          </v-card-title>
        </v-card>

        <!-- Messages Area -->
        <v-card class="flex-1-1 overflow-auto" flat>
          <v-card-text ref="messagesContainer" class="messages-container">
            <div v-if="messagesLoading" class="text-center">
              <v-progress-circular indeterminate />
            </div>
            <div v-else-if="messages.length === 0" class="text-center text-grey">
              No messages yet. Be the first to send one!
            </div>
            <div v-else>
              <div
                v-for="msg in messages"
                :key="msg.id"
                class="message mb-4"
                :class="{ 'my-message': msg.user_id === currentUserId }"
              >
                <div class="message-header d-flex align-center mb-1">
                  <strong>{{ getUserName(msg.user_id) }}</strong>
                  <span class="text-caption text-grey ml-2">
                    {{ formatTime(msg.created_at) }}
                  </span>
                </div>
                <v-card
                  :color="msg.user_id === currentUserId ? 'primary' : 'grey-lighten-3'"
                  :class="msg.user_id === currentUserId ? 'white--text' : ''"
                  class="message-bubble"
                >
                  <v-card-text>{{ msg.content }}</v-card-text>
                </v-card>
              </div>
            </div>
          </v-card-text>
        </v-card>

        <!-- Message Input -->
        <v-card class="flex-0-0">
          <v-card-text>
            <v-form @submit.prevent="sendMessage">
              <v-text-field
                v-model="newMessage"
                label="Type a message..."
                hide-details
                :disabled="!currentUserId"
                append-inner-icon="mdi-send"
                @click:append-inner="sendMessage"
              />
            </v-form>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted } from 'vue';
import { usePrismRequest, usePrismObject, usePrismClient } from '@prism/vue';
import type { ChatRoom, Message, User } from '../types';

// Props
const props = defineProps<{
  roomId: string;
}>();

// State
const currentUserId = ref<string | null>(localStorage.getItem('userId'));
const newMessage = ref('');
const messages = ref<Message[]>([]);
const messagesLoading = ref(false);
const messagesContainer = ref<HTMLElement | null>(null);
const users = ref<Map<string, User>>(new Map());

// Prism client
const prismClient = usePrismClient();

// Get room data
const { data: roomData, loading: roomLoading } = usePrismObject<ChatRoom>(
  ref(props.roomId),
  'default'
);

// Request composable for sending messages
const { execute } = usePrismRequest();

// Load messages when room is loaded
watch(roomData, async (room) => {
  if (room) {
    await loadMessages();
  }
});

// Load messages for the room
const loadMessages = async () => {
  messagesLoading.value = true;
  try {
    const result = await execute('getRoomMessages', {
      room_id: props.roomId,
      limit: 50,
    });

    if (result && result.messages) {
      messages.value = result.messages;
      // Subscribe to all messages for real-time updates
      for (const msg of messages.value) {
        prismClient.watch(msg.id, (updated) => {
          const index = messages.value.findIndex((m) => m.id === msg.id);
          if (index !== -1) {
            messages.value[index] = updated as Message;
          }
        });
      }
      await nextTick();
      scrollToBottom();
    }
  } catch (error) {
    console.error('Failed to load messages:', error);
  } finally {
    messagesLoading.value = false;
  }
};

// Send a message
const sendMessage = async () => {
  if (!newMessage.value.trim() || !currentUserId.value) return;

  const content = newMessage.value;
  newMessage.value = '';

  try {
    const result = await execute('sendMessage', {
      room_id: props.roomId,
      user_id: currentUserId.value,
      content,
    });

    if (result && result.message) {
      // Add message to list
      messages.value.push(result.message);

      // Watch for updates
      prismClient.watch(result.message.id, (updated) => {
        const index = messages.value.findIndex((m) => m.id === result.message.id);
        if (index !== -1) {
          messages.value[index] = updated as Message;
        }
      });

      await nextTick();
      scrollToBottom();
    }
  } catch (error) {
    console.error('Failed to send message:', error);
    alert('Failed to send message. Please try again.');
  }
};

// Get user name (simplified)
const getUserName = (userId: string) => {
  const user = users.value.get(userId);
  return user?.display_name || user?.username || 'Unknown User';
};

// Format timestamp
const formatTime = (timestamp: string) => {
  const date = new Date(timestamp);
  return date.toLocaleTimeString('en-US', {
    hour: '2-digit',
    minute: '2-digit',
  });
};

// Scroll to bottom of messages
const scrollToBottom = () => {
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight;
  }
};

// Load initial data
onMounted(() => {
  if (!currentUserId.value) {
    alert('Please login first');
    // Could redirect to home
  }
});
</script>

<style scoped>
.fill-height {
  height: 100vh;
}

.messages-container {
  max-height: calc(100vh - 200px);
  overflow-y: auto;
}

.message {
  max-width: 80%;
}

.my-message {
  margin-left: auto;
}

.message-bubble {
  border-radius: 12px;
}
</style>
