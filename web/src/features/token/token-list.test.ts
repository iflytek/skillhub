import { describe, expect, it } from 'vitest'
import { TokenList } from './token-list'

// TokenList is a complex stateful React component that owns token pagination,
// optimistic deletion, expiration editing, and creation entry points. Its internal
// PAGE_SIZE constant and formatting helpers are not exported and can only be
// exercised through component rendering.

describe('token-list module', () => {
  it('exports the TokenList component', () => {
    expect(TokenList).toBeDefined()
    expect(typeof TokenList).toBe('function')
  })
})
