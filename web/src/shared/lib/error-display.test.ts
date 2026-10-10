import { describe, expect, it } from 'vitest'
import { MAX_ERROR_MESSAGE_LENGTH, truncateErrorMessage } from './error-display'

describe('truncateErrorMessage', () => {
  it('keeps short messages unchanged', () => {
    expect(truncateErrorMessage('publish failed')).toBe('publish failed')
  })

  it('truncates long messages and appends an ellipsis', () => {
    const message = 'x'.repeat(MAX_ERROR_MESSAGE_LENGTH + 20)

    expect(truncateErrorMessage(message)).toBe(`${'x'.repeat(MAX_ERROR_MESSAGE_LENGTH)}...`)
  })
})
