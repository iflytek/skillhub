// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { Dialog, DialogContent, DialogTitle, DialogDescription } from './dialog'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from './select'

beforeEach(() => {
  // jsdom has no scrolling implementation; keyboard focus is asserted below.
  Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', { configurable: true, value: vi.fn() })
})

afterEach(() => {
  delete (HTMLElement.prototype as Partial<HTMLElement>).scrollIntoView
  cleanup()
  document.getElementById('skillhub-portals')?.remove()
})

describe('Select nested in Dialog', () => {
  it.each(['default', 'compact'] as const)('keeps focus and keyboard selection in %s portaled options, then restores trigger focus', async (density) => {
    const portals = document.createElement('div')
    portals.id = 'skillhub-portals'
    document.body.append(portals)
    render(<Dialog defaultOpen><DialogContent><DialogTitle>Token</DialogTitle><DialogDescription>Expiration</DialogDescription>
      <Select defaultValue="never"><SelectTrigger aria-label="expiry"><SelectValue /></SelectTrigger><SelectContent density={density}>
        <SelectItem value="never">Never</SelectItem><SelectItem value="custom">Custom</SelectItem>
      </SelectContent></Select>
    </DialogContent></Dialog>)
    fireEvent.keyDown(screen.getByRole('combobox'), { key: 'ArrowDown' })
    await waitFor(() => expect(document.activeElement?.textContent).toBe('Never'))
    fireEvent.keyDown(document.activeElement!, { key: 'ArrowDown' })
    await waitFor(() => expect(document.activeElement?.textContent).toBe('Custom'))
    expect(document.activeElement?.hasAttribute('data-highlighted')).toBe(true)
    fireEvent.keyDown(document.activeElement!, { key: 'Escape' })
    await waitFor(() => expect(screen.queryByRole('listbox')).toBeNull())
    expect(screen.getByRole('dialog', { name: 'Token' })).toBeDefined()
    await waitFor(() => expect(document.activeElement).toBe(screen.getByRole('combobox')))
  })
})
