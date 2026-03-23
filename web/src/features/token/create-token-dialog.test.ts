import { describe, expect, it } from 'vitest'
import { CreateTokenDialog } from './create-token-dialog'

// CreateTokenDialog is a stateful React component that handles API token creation
// with duplicate-name checks, expiration selection, and a one-time token reveal.
// Its internal MAX_TOKEN_NAME_LENGTH constant and validation logic are not exported
// and can only be exercised through component rendering.

describe('create-token-dialog module', () => {
  it('exports the CreateTokenDialog component', () => {
    expect(CreateTokenDialog).toBeDefined()
    expect(typeof CreateTokenDialog).toBe('function')
  })
})
