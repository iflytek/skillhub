import { readFileSync } from 'node:fs'
import { defineConfig, type Plugin } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'
import { validateBasePath } from './base-path-config'

const JS_BUILD_TARGET = 'es2020'
const LEGACY_BROWSER_TARGETS = ['chrome83', 'edge83', 'firefox78', 'safari14']
const basePath = validateBasePath(process.env.VITE_BASE_PATH ?? '/')
const guideTemplate = readFileSync(path.resolve(__dirname, 'src/docs/skill.md.template'), 'utf8')
const safeHostPattern = /^(?:[A-Za-z0-9.-]+|\[[0-9A-Fa-f:.]+\])(?::[0-9]{1,5})?$/

function registryGuidePlugin(): Plugin {
  const basePrefix = basePath === '/' ? '' : basePath.slice(0, -1)
  const guidePath = `${basePrefix}/registry/skill.md`

  return {
    name: 'skillhub-cli-guide',
    generateBundle() {
      this.emitFile({
        type: 'asset',
        fileName: 'registry/skill.md',
        source: guideTemplate,
      })
    },
    configureServer(server) {
      // Install after Vite's built-in Host check so an untrusted Host can never
      // be reflected into CLI commands. originalUrl survives SPA/base rewrites.
      return () => {
        server.middlewares.use((request, response, next) => {
          const requestPath = new URL(request.originalUrl ?? request.url ?? '/', 'http://localhost').pathname
          if (requestPath !== guidePath) {
            next()
            return
          }

          const host = request.headers.host
          if (!host || !safeHostPattern.test(host)) {
            response.statusCode = 400
            response.end('Invalid Host')
            return
          }

          const publicBaseUrl = `http://${host}${basePrefix}`
          const guide = guideTemplate.replaceAll('${SKILLHUB_PUBLIC_BASE_URL}', publicBaseUrl)
          response.statusCode = 200
          response.setHeader('Content-Type', 'text/markdown; charset=utf-8')
          response.setHeader('Cache-Control', 'no-cache, no-store, must-revalidate')
          response.end(guide)
        })
      }
    },
  }
}

export default defineConfig({
  base: basePath,
  plugins: [registryGuidePlugin(), react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  build: {
    target: JS_BUILD_TARGET,
    cssTarget: LEGACY_BROWSER_TARGETS,
  },
  optimizeDeps: {
    esbuildOptions: {
      target: JS_BUILD_TARGET,
    },
  },
  test: {
    exclude: ['**/node_modules/**', '**/e2e/**'],
    testTimeout: 30000,
    hookTimeout: 30000,
  },
  server: {
    port: 3000,
    watch: {
      usePolling: true,
      interval: 150,
    },
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/oauth2': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
