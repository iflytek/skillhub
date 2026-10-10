import { describe, expect, it } from 'vitest'
import { TOKEN_TABLE_ACTIONS_HEAD_CLASS_NAME, TOKEN_TABLE_HEAD_CLASS_NAME } from './token-table-style'

describe('token table header styles', () => {
  it('uses roomier spacing for standard header cells', () => {
    expect(TOKEN_TABLE_HEAD_CLASS_NAME).toContain('px-5')
    expect(TOKEN_TABLE_HEAD_CLASS_NAME).toContain('py-4')
    expect(TOKEN_TABLE_HEAD_CLASS_NAME).toContain('uppercase')
  })

  it('keeps the actions header centered', () => {
    expect(TOKEN_TABLE_ACTIONS_HEAD_CLASS_NAME).toContain('text-center')
  })
})
