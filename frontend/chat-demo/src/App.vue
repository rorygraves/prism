<template>
  <v-app>
    <v-app-bar color="primary" app>
      <v-toolbar-title>Prism Chat Demo</v-toolbar-title>
      <v-spacer />
      <v-chip
        :color="connected ? 'success' : 'error'"
        :prepend-icon="connected ? 'mdi-wifi' : 'mdi-wifi-off'"
        variant="flat"
      >
        {{ connected ? 'Connected' : 'Disconnected' }}
      </v-chip>
    </v-app-bar>

    <v-main>
      <router-view />
    </v-main>
  </v-app>
</template>

<script setup lang="ts">
import { usePrismConnection } from '@prism/vue';

const { connected } = usePrismConnection();
</script>

<style>
html,
body {
  margin: 0;
  padding: 0;
  height: 100%;
  overflow: hidden;
}

#app {
  height: 100%;
}
</style>
