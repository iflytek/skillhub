import { describe, expect, it } from 'vitest'
import * as sessionBootstrap from './use-session-bootstrap'

/**
 * use-session-bootstrap exports a single useMutation hook that delegates to
 * authApi.bootstrapSession and updates the ['auth', 'me'] query cache on success.
 * There are no exported pure functions, constants, or data transformations to unit-test.
 *
 * This file verifies the public API surface so that accidental export removals are caught.
 */
describe('use-session-bootstrap module exports', () => {
  it('exports useSessionBootstrap hook', () => {
    expect(sessionBootstrap.useSessionBootstrap).toBeTypeOf('function')
  })
})
