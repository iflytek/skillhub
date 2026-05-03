import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

const apiProxyTarget = process.env.SKILLHUB_API_PROXY_TARGET || 'http://localhost:8080'

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
        target: apiProxyTarget,
        changeOrigin: true,
      },
      '/oauth2': {
        target: apiProxyTarget,
        changeOrigin: true,
      },
    },
  },
})
