import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
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

  it('renders semantic tablist, tab, and tabpanel roles', () => {
    const html = renderToStaticMarkup(
      createElement(Tabs, {
        defaultValue: 'clawhub',
        children: [
          createElement(TabsList, {
            key: 'list',
            children: [
              createElement(TabsTrigger, { key: 'clawhub', value: 'clawhub', children: 'ClawHub CLI' }),
              createElement(TabsTrigger, { key: 'skillhub', value: 'skillhub', children: 'SkillHub CLI' }),
            ],
          }),
          createElement(TabsContent, { key: 'clawhub-content', value: 'clawhub', children: 'clawhub command' }),
          createElement(TabsContent, { key: 'skillhub-content', value: 'skillhub', children: 'skillhub command' }),
        ],
      }),
    )

    expect(html).toContain('role="tablist"')
    expect(html).toContain('role="tab"')
    expect(html).toContain('aria-selected="true"')
    expect(html).toContain('aria-selected="false"')
    expect(html).toContain('role="tabpanel"')
  })
})
