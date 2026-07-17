/// <reference types="vitest" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000
  },
  test: {
    globals: true,
    setupFiles: './src/test/setup.ts',
    environmentOptions: {
      jsdom: {
        url: 'http://localhost:3000'
      }
    }
  }
});
