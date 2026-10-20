import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// В разработке запросы /api проксируются на backend, поэтому CORS настраивать не нужно
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
});
