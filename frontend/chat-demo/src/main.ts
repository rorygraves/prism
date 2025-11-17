/**
 * Main entry point for the chat demo app.
 */

import { createApp } from 'vue';
import { createVuetify } from 'vuetify';
import * as components from 'vuetify/components';
import * as directives from 'vuetify/directives';
import '@mdi/font/css/materialdesignicons.css';
import 'vuetify/styles';

import { PrismClient } from '@prism/client';
import { PrismClientKey } from '@prism/vue';

import App from './App.vue';
import router from './router';

// Create Vuetify instance
const vuetify = createVuetify({
  components,
  directives,
  theme: {
    defaultTheme: 'light',
    themes: {
      light: {
        colors: {
          primary: '#1976D2',
          secondary: '#424242',
          accent: '#82B1FF',
          error: '#FF5252',
          info: '#2196F3',
          success: '#4CAF50',
          warning: '#FFC107',
        },
      },
    },
  },
});

// Create Prism client
const prismClient = new PrismClient('ws://localhost:8000/ws');

// Create and mount app
const app = createApp(App);

app.use(router);
app.use(vuetify);

// Provide Prism client to all components
app.provide(PrismClientKey, prismClient);

// Connect to server
prismClient
  .connect()
  .then(() => {
    console.log('Connected to Prism server');
  })
  .catch((error) => {
    console.error('Failed to connect to Prism server:', error);
  });

app.mount('#app');
