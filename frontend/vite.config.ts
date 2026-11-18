/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// The Spring backend runs on :8080. In dev, the browser only talks to Vite (:5173), which proxies
// API calls - same origin, so no CORS configuration is needed in Spring.
const backend = process.env.BACKEND_URL ?? 'http://localhost:8080'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': backend,
      '/actuator': backend,
      '/v3/api-docs': backend,
    },
  },
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.{ts,tsx}'],
  },
})
