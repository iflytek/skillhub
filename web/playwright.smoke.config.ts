import { defineConfig } from '@playwright/test'
import baseConfig from './playwright.config'

const smokeSpecs = [
  'auth-entry.spec.ts',
  'landing-navigation.spec.ts',
  'route-guard.spec.ts',
  'dashboard-shell.spec.ts',
]

export default defineConfig({
  ...baseConfig,
  testMatch: smokeSpecs,
})
