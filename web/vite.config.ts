import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

const devProxyTarget = process.env.VITE_DEV_PROXY_TARGET ?? 'http://localhost:8080'

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  test: {
    exclude: ['**/node_modules/**', '**/e2e/**'],
  },
  server: {
    port: 3000,
    watch: {
      usePolling: true,
      interval: 150,
    },
    proxy: {
      '/api': {
        target: devProxyTarget,
        changeOrigin: true,
      },
      '/oauth2': {
        target: devProxyTarget,
        changeOrigin: true,
      },
    },
  },
})
