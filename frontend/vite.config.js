import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The API is proxied rather than called cross-origin, so in development the browser
// sees one origin. That keeps the session cookie and the CSRF cookie behaving exactly
// as they will in production, instead of working locally and breaking on deploy.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.API_TARGET || 'http://localhost:8080',
        changeOrigin: false,
      },
    },
  },
});
