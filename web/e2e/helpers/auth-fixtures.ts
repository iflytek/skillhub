import type { Page } from '@playwright/test'
import { authMethod, fulfillJson, user, type TestAuthMethod, type TestUser } from './api-fixtures'

type MockAuthStateOptions = {
  authenticated?: boolean
  currentUser?: TestUser
  userRole?: string
  authMethods?: TestAuthMethod[]
  providers?: unknown[]
}

type MutationOptions = {
  succeed?: boolean
  responseUser?: TestUser
  errorMessage?: string
}

export async function setEnglishLocale(page: Page) {
  await page.addInitScript(() => {
    window.localStorage.setItem('i18nextLng', 'en')
  })
}

export async function mockAuthState(page: Page, options: MockAuthStateOptions = {}) {
  const authenticated = options.authenticated ?? false
  const currentUser = options.currentUser ?? user('local-user', 'Local User', options.userRole ?? 'USER')
  const authMethods = options.authMethods ?? []
  const providers = options.providers ?? []

  await page.route('**/api/v1/auth/me**', async (route) => {
    if (!authenticated) {
      await fulfillJson(route, null, { status: 401, msg: 'Unauthorized' })
      return
    }

    await fulfillJson(route, currentUser)
  })

  await page.route('**/api/v1/auth/methods**', async (route) => {
    await fulfillJson(route, authMethods)
  })

  await page.route('**/api/v1/auth/providers**', async (route) => {
    await fulfillJson(route, providers)
  })
}

export async function mockLoginMutation(page: Page, options: MutationOptions = {}) {
  const responseUser = options.responseUser ?? user('local-user', 'Local User')
  const succeed = options.succeed ?? true

  await page.route('**/api/v1/auth/local/login', async (route) => {
    if (!succeed) {
      await fulfillJson(route, null, { status: 401, msg: options.errorMessage ?? 'Invalid credentials' })
      return
    }

    await fulfillJson(route, responseUser)
  })
}

export async function mockRegisterMutation(page: Page, options: MutationOptions = {}) {
  const responseUser = options.responseUser ?? user('local-user', 'Local User')
  const succeed = options.succeed ?? true

  await page.route('**/api/v1/auth/local/register', async (route) => {
    if (!succeed) {
      await fulfillJson(route, null, { status: 400, msg: options.errorMessage ?? 'Registration failed' })
      return
    }

    await fulfillJson(route, responseUser)
  })
}

export function oauthOnlyMethods(returnTo: string = '/dashboard') {
  return [
    authMethod('github', 'GitHub', {
      actionUrl: `https://auth.example.com/github?returnTo=${encodeURIComponent(returnTo)}`,
    }),
  ]
}
