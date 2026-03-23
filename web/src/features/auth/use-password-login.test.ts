import { describe, expect, it } from 'vitest'
import * as passwordLogin from './use-password-login'

/**
 * use-password-login exports a single useMutation hook that conditionally routes to
 * authApi.directLogin or authApi.localLogin based on runtime configuration.
 * The branching logic lives inside the hook's mutationFn and cannot be unit-tested
 * without rendering the hook in a React/QueryClient context.
 *
 * This file verifies the public API surface so that accidental export removals are caught.
 */
describe('use-password-login module exports', () => {
  it('exports usePasswordLogin hook', () => {
    expect(passwordLogin.usePasswordLogin).toBeTypeOf('function')
  })
})
