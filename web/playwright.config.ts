import { defineConfig, devices } from '@playwright/test'

const localNoProxyHosts = ['localhost', '127.0.0.1', '::1']
const mergedNoProxy = Array.from(new Set([
  ...(process.env.NO_PROXY?.split(',').filter(Boolean) ?? []),
  ...(process.env.no_proxy?.split(',').filter(Boolean) ?? []),
  ...localNoProxyHosts,
])).join(',')

process.env.NO_PROXY = mergedNoProxy
process.env.no_proxy = mergedNoProxy
const externalBaseUrl = process.env.PLAYWRIGHT_BASE_URL
const baseURL = externalBaseUrl ?? 'http://127.0.0.1:3000'

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  timeout: process.env.CI ? 90_000 : 45_000,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: Number(process.env.PLAYWRIGHT_WORKERS ?? 1),
  reporter: 'html',
  use: {
    baseURL,
    trace: 'on-first-retry',
    screenshot: 'on',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: externalBaseUrl
    ? undefined
    : {
        command: 'pnpm exec vite --host 127.0.0.1 --port 3000 --strictPort',
        url: baseURL,
        reuseExistingServer: true,
        timeout: 120000,
      },
})
