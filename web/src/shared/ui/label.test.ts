import { describe, expect, it } from 'vitest'
import { Label } from './label'

describe('Label component', () => {
  it('exports the Label component', () => {
    expect(Label).toBeDefined()
  })

  it('sets displayName', () => {
    expect(Label.displayName).toBe('Label')
  })
})
