import { describe, expect, it } from 'vitest'
import { Tabs, TabsList, TabsTrigger, TabsContent } from './tabs'

describe('Tabs components', () => {
  it('exports all tabs sub-components', () => {
    expect(Tabs).toBeDefined()
    expect(TabsList).toBeDefined()
    expect(TabsTrigger).toBeDefined()
    expect(TabsContent).toBeDefined()
  })

  it('exports Tabs as a named function', () => {
    expect(typeof Tabs).toBe('function')
    expect(Tabs.name).toBe('Tabs')
  })

  it('exports TabsList as a named function', () => {
    expect(typeof TabsList).toBe('function')
    expect(TabsList.name).toBe('TabsList')
  })

  it('exports TabsTrigger as a named function', () => {
    expect(typeof TabsTrigger).toBe('function')
    expect(TabsTrigger.name).toBe('TabsTrigger')
  })

  it('exports TabsContent as a named function', () => {
    expect(typeof TabsContent).toBe('function')
    expect(TabsContent.name).toBe('TabsContent')
  })
})
