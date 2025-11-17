/**
 * Type definitions for chat demo.
 */

export interface User {
  id: string;
  username: string;
  display_name: string;
  avatar_url: string | null;
  created_at: string;
}

export interface ChatRoom {
  id: string;
  name: string;
  description: string | null;
  member_ids: string[];
  created_at: string;
  created_by: string;
}

export interface Message {
  id: string;
  room_id: string;
  user_id: string;
  content: string;
  created_at: string;
  edited_at: string | null;
}
