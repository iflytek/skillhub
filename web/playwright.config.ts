import { defineConfig, devices } from '@playwright/test'

const webPort = process.env.PLAYWRIGHT_WEB_PORT ?? '3000'
const webBaseUrl = `http://localhost:${webPort}`

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  timeout: process.env.CI ? 90_000 : 45_000,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: Number(process.env.PLAYWRIGHT_WORKERS ?? 1),
  reporter: 'html',
  use: {
    baseURL: webBaseUrl,
    trace: 'on-first-retry',
    screenshot: 'on',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: {
    command: `pnpm exec vite --host 127.0.0.1 --port ${webPort} --strictPort`,
    url: webBaseUrl,
    reuseExistingServer: true,
    timeout: 120000,
  },
})
