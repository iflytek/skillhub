import { describe, expect, it } from 'vitest'
import * as loginButton from './login-button'

/**
 * LoginButton is a React component that renders OAuth login buttons from backend-provided
 * auth methods. It filters for OAUTH_REDIRECT method types and shows a loading state.
 * There are no exported pure functions, constants, or data transformations to unit-test.
 *
 * Full rendering tests would require a React test renderer, QueryClient provider,
 * and i18next setup. This file verifies the export surface.
 */
describe('login-button module exports', () => {
  it('exports LoginButton component', () => {
    expect(loginButton.LoginButton).toBeTypeOf('function')
  })
})
