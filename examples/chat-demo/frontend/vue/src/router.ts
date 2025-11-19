/**
 * Vue Router configuration.
 */

import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'Home',
    component: () => import('./views/HomeView.vue'),
  },
  {
    path: '/chat/:roomId',
    name: 'Chat',
    component: () => import('./views/ChatView.vue'),
    props: true,
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

export default router;
