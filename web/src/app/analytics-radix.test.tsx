/** @vitest-environment jsdom */
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'

beforeEach(() => {
  vi.stubGlobal('ResizeObserver', class { observe() {} unobserve() {} disconnect() {} })
  Element.prototype.scrollIntoView ??= () => {}
})
afterEach(() => { cleanup(); vi.unstubAllGlobals() })
function fixture(onValueChange: (value: string) => void, open = true) {
  return render(<Select defaultValue="alpha" defaultOpen={open} onValueChange={onValueChange}>
    <SelectTrigger data-analytics-id="search.fixture.interact" aria-label="Fixture filter"><SelectValue /></SelectTrigger>
    <SelectContent>
      <SelectItem value="alpha" data-analytics-id="search.fixture.filter">Alpha</SelectItem>
      <SelectItem value="beta" data-analytics-id="search.fixture.filter">Beta</SelectItem>
      <SelectItem value="gamma" data-analytics-id="search.fixture.filter" disabled>Gamma</SelectItem>
    </SelectContent>
  </Select>)
}

describe('actual Radix item activation with passive caller attributes', () => {
  it.each(['Enter', ' '])('retains %s keyboard selection and the item marker', async key => {
    const change = vi.fn()
    fixture(change)
    const option = await screen.findByRole('option', { name: 'Beta' })
    expect(option.getAttribute('data-analytics-id')).toBe('search.fixture.filter')
    fireEvent.keyDown(option, { key })
    await waitFor(() => expect(change).toHaveBeenCalledExactlyOnceWith('beta'))
    expect(screen.queryByRole('option', { name: 'Beta' })).toBeNull()
  })

  it('selects mouse items on pointerup, not pointerdown, without requiring a click', async () => {
    const change = vi.fn()
    fixture(change)
    const option = await screen.findByRole('option', { name: 'Beta' })
    fireEvent.pointerDown(option, { pointerType: 'mouse', button: 0 })
    expect(change).not.toHaveBeenCalled()
    fireEvent.pointerUp(option, { pointerType: 'mouse', button: 0 })
    await waitFor(() => expect(change).toHaveBeenCalledExactlyOnceWith('beta'))
  })

  it.each(['touch', 'pen'])('selects %s items on click, not pointerup', async pointerType => {
    const change = vi.fn()
    fixture(change)
    const option = await screen.findByRole('option', { name: 'Beta' })
    fireEvent.pointerDown(option, { pointerType })
    fireEvent.pointerUp(option, { pointerType })
    expect(change).not.toHaveBeenCalled()
    fireEvent.click(option)
    await waitFor(() => expect(change).toHaveBeenCalledExactlyOnceWith('beta'))
  })

  it('retains disabled selection guards', async () => {
    const change = vi.fn()
    fixture(change)
    const option = await screen.findByRole('option', { name: 'Gamma' })
    expect(option.getAttribute('data-disabled')).toBe('')
    fireEvent.keyDown(option, { key: 'Enter' })
    fireEvent.pointerDown(option, { pointerType: 'mouse' })
    fireEvent.pointerUp(option, { pointerType: 'mouse' })
    fireEvent.click(option)
    expect(change).not.toHaveBeenCalled()
  })

  it('documents closed-trigger typeahead as a real change with no item-activation event', async () => {
    const change = vi.fn()
    fixture(change, false)
    const trigger = screen.getByRole('combobox')
    expect(trigger.getAttribute('data-analytics-id')).toBe('search.fixture.interact')
    expect(screen.queryByRole('option')).toBeNull()
    fireEvent.keyDown(trigger, { key: 'b' })
    await waitFor(() => expect(change).toHaveBeenCalledExactlyOnceWith('beta'))
    expect(screen.queryByRole('option')).toBeNull()
  })
})
