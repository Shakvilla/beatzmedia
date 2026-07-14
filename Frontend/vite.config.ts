import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { TanStackRouterVite } from '@tanstack/router-vite-plugin'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    TanStackRouterVite(),
  ],
  server: {
    proxy: {
      // 127.0.0.1, not localhost: Node resolves "localhost" to IPv6 ::1 first, so a
      // localhost target can hit a different app bound to ::1:8080. Pin IPv4 to reach Quarkus.
      '/v1': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
})
