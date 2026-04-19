import { defineConfig, devices } from '@playwright/test'

const heavySearchSpecPattern = /search-(page-full|card-interaction)\.spec\.ts/

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  timeout: process.env.CI ? 90_000 : 45_000,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: Number(process.env.PLAYWRIGHT_WORKERS ?? 1),
  reporter: 'html',
  use: {
    baseURL: 'http://localhost:3000',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'setup-search-seed',
      testMatch: '**/e2e/setup/search-heavy-seed.setup.ts',
      use: { ...devices['Desktop Chrome'] },
    },
    {
      name: 'chromium',
      testIgnore: heavySearchSpecPattern,
      use: { ...devices['Desktop Chrome'] },
    },
    {
      name: 'chromium-heavy-search',
      testMatch: heavySearchSpecPattern,
      dependencies: ['setup-search-seed'],
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: {
    command: 'pnpm exec vite --host 127.0.0.1 --port 3000 --strictPort',
    url: 'http://localhost:3000',
    reuseExistingServer: true,
    timeout: 120000,
  },
})
