import { describe, expect, it } from 'vitest'
import { Textarea } from './textarea'

describe('Textarea component', () => {
  it('exports the Textarea component', () => {
    expect(Textarea).toBeDefined()
  })

  it('sets displayName', () => {
    expect(Textarea.displayName).toBe('Textarea')
  })
})
