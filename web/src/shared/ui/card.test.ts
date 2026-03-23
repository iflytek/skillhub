import { describe, expect, it } from 'vitest'
import { Card, CardHeader, CardTitle, CardDescription, CardContent, CardFooter } from './card'

describe('Card components', () => {
  it('exports all card sub-components', () => {
    expect(Card).toBeDefined()
    expect(CardHeader).toBeDefined()
    expect(CardTitle).toBeDefined()
    expect(CardDescription).toBeDefined()
    expect(CardContent).toBeDefined()
    expect(CardFooter).toBeDefined()
  })

  it('sets displayName on all card sub-components', () => {
    expect(Card.displayName).toBe('Card')
    expect(CardHeader.displayName).toBe('CardHeader')
    expect(CardTitle.displayName).toBe('CardTitle')
    expect(CardDescription.displayName).toBe('CardDescription')
    expect(CardContent.displayName).toBe('CardContent')
    expect(CardFooter.displayName).toBe('CardFooter')
  })
})
