import { defineConfig } from '@playwright/test'
import baseConfig from './playwright.config'

const smokeSpecs = [
  'auth-entry.spec.ts',
  'search-flow.spec.ts',
  'skill-detail-browse.spec.ts',
  'publish-flow.spec.ts',
  'review-detail.spec.ts',
  'role-access-control.spec.ts',
]

export default defineConfig({
  ...baseConfig,
  testMatch: smokeSpecs,
})
