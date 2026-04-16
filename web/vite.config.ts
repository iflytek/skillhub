import fs from 'fs'
import { defineConfig, loadEnv, type ProxyOptions } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

function forwardOriginalHostToBackend(): NonNullable<ProxyOptions['configure']> {
  return (proxy) => {
    proxy.on('proxyReq', (proxyReq, req) => {
      const host = req.headers.host
      if (host) {
        proxyReq.setHeader('X-Forwarded-Host', host)
        const proto = req.headers['x-forwarded-proto']
        proxyReq.setHeader(
          'X-Forwarded-Proto',
          typeof proto === 'string' ? proto : 'http',
        )
      }
    })
  }
}

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  // Override with VITE_BACKEND_URL in .env.local
  const backend = env.VITE_BACKEND_URL || 'http://localhost:8080'

  return {
    plugins: [
      react(),
      {
        name: 'serve-skill-md',
        configureServer(server) {
          server.middlewares.use('/registry/skill.md', (_req, res) => {
            const template = fs.readFileSync(
              path.resolve(__dirname, 'src/docs/skill.md.template'),
              'utf-8',
            )
            const origin = `http://localhost:${server.config.server.port || 8181}`
            res.setHeader('Content-Type', 'text/plain; charset=utf-8')
            res.end(
              template.replace(/\$\{SKILLHUB_PUBLIC_BASE_URL\}/g, origin),
            )
          })
        },
      },
    ],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, './src'),
      },
    },
    test: {
      exclude: ['**/node_modules/**', '**/e2e/**'],
    },
    server: {
      // Listen on 0.0.0.0 so LAN / other machines can reach dev (still need firewall rules for true public IP).
      host: true,
      port: 8181,
      watch: {
        usePolling: true,
        interval: 150,
      },
      proxy: {
        '/api': {
          target: backend,
          changeOrigin: true,
          configure: forwardOriginalHostToBackend(),
        },
        '/oauth2': {
          target: backend,
          changeOrigin: true,
          configure: forwardOriginalHostToBackend(),
        },
        '/login/oauth2': {
          target: backend,
          changeOrigin: true,
          configure: forwardOriginalHostToBackend(),
        },
        '/actuator': {
          target: backend,
          changeOrigin: true,
          configure: forwardOriginalHostToBackend(),
        },
      },
    },
  }
})
