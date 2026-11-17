// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { MarketSearchControls } from './market-search-controls'

vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }))

class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

function setup(labelCount = 8) {
  vi.stubGlobal('ResizeObserver', ResizeObserverStub)
  const props = {
    query: '', onQueryChange: vi.fn(), onSearch: vi.fn(), source: 'platform' as const, onSourceChange: vi.fn(),
    labels: ['Office', 'Development', 'Learning', 'Writing', 'Research', 'Security', 'Design', 'Archive'].slice(0, labelCount).map(displayName => ({ slug: displayName.toLowerCase(), displayName, type: 'RECOMMENDED' })),
    selectedLabel: 'office', onLabelChange: vi.fn(), starredOnly: false, onStarredToggle: vi.fn(),
    sort: 'newest', onSortChange: vi.fn(), namespace: '', onNamespaceClear: vi.fn(), resultCount: 26, isUpdating: false,
  }
  const view = render(<MarketSearchControls {...props} />)
  return { ...props, updateFavorites: (starredOnly: boolean) => view.rerender(<MarketSearchControls {...props} starredOnly={starredOnly} />), updateQuery: (query: string) => view.rerender(<MarketSearchControls {...props} query={query} />) }
}

afterEach(() => { cleanup(); vi.unstubAllGlobals() })

describe('market search controls', () => {
  it('keeps search explicit and routes source and favorite selection to the page', () => {
    const props = setup()
    const input = screen.getByRole('textbox', { name: 'search.market.placeholder' })
    fireEvent.change(input, { target: { value: '@global writer' } })
    expect(props.onQueryChange).toHaveBeenCalledWith('@global writer')
    expect(props.onSearch).not.toHaveBeenCalled()
    props.updateQuery('@global writer')
    fireEvent.submit(input.closest('form')!)
    expect(props.onSearch).toHaveBeenCalledWith('@global writer')
    fireEvent.click(screen.getByRole('button', { name: 'search.source.github' }))
    expect(props.onSourceChange).toHaveBeenCalledWith('github')
    fireEvent.click(screen.getByRole('button', { name: 'search.market.onlyFavorites' }))
    expect(props.onStarredToggle).toHaveBeenCalledOnce()
  })

  it('reflects favorite mode in both toggle buttons and restores source controls', () => {
    const props = setup()
    const toggle = screen.getByRole('button', { name: 'search.market.onlyFavorites' })
    expect(toggle.getAttribute('aria-pressed')).toBe('false')
    props.updateFavorites(true)
    expect(toggle.getAttribute('aria-pressed')).toBe('true')
    expect(screen.getByRole('button', { name: 'search.market.favorites' }).getAttribute('aria-pressed')).toBe('true')
    expect(toggle.querySelector('svg')?.getAttribute('fill')).toBe('currentColor')
    expect(screen.queryByRole('button', { name: 'search.source.github' })).toBeNull()
    fireEvent.click(toggle)
    expect(props.onStarredToggle).toHaveBeenCalledOnce()
    props.updateFavorites(false)
    expect(screen.getByRole('button', { name: 'search.source.github' })).toBeTruthy()
    expect(screen.queryByRole('checkbox')).toBeNull()
  })

  it('searches all desktop labels, selects immediately and returns focus', async () => {
    const props = setup()
    const trigger = screen.getByRole('button', { name: 'search.market.moreLabels' })
    fireEvent.click(trigger)
    fireEvent.change(screen.getByRole('textbox', { name: 'search.market.findLabel' }), { target: { value: 'archive' } })
    fireEvent.click(screen.getByRole('button', { name: 'Archive' }))
    expect(props.onLabelChange).toHaveBeenCalledWith('archive')
    await waitFor(() => expect(screen.queryByRole('textbox', { name: 'search.market.findLabel' })).toBeNull())
    await waitFor(() => expect(document.activeElement).toBe(trigger))
  })

  it('shows only additional desktop labels by default but searches the full catalog', () => {
    setup()
    fireEvent.click(screen.getByRole('button', { name: 'search.market.moreLabels' }))
    const list = screen.getByRole('group', { name: 'search.market.allLabels' })
    expect(list.textContent).toBe('DesignArchive')
    expect(list.querySelectorAll('button')).toHaveLength(2)
    fireEvent.change(screen.getByRole('textbox', { name: 'search.market.findLabel' }), { target: { value: 'office' } })
    expect(list.textContent).toBe('Office')
    fireEvent.change(screen.getByRole('textbox', { name: 'search.market.findLabel' }), { target: { value: 'not-found' } })
    expect(list.textContent).toBe('search.market.noLabels')
  })

  it('hides More labels when every label is already visible and keeps the mobile full list', () => {
    setup(6)
    expect(screen.queryByRole('button', { name: 'search.market.moreLabels' })).toBeNull()
    fireEvent.click(screen.getByRole('button', { name: 'search.market.allLabels' }))
    const list = screen.getByRole('group', { name: 'search.market.allLabels' })
    expect(list.querySelectorAll('button')).toHaveLength(7)
  })

  it('keeps mobile label changes in a draft until Apply and discards Cancel', () => {
    const props = setup()
    const trigger = screen.getByRole('button', { name: 'search.market.allLabels' })
    fireEvent.click(trigger)
    fireEvent.click(screen.getByRole('button', { name: 'Archive' }))
    fireEvent.click(screen.getByRole('button', { name: 'search.market.cancel' }))
    expect(props.onLabelChange).not.toHaveBeenCalled()
    fireEvent.click(trigger)
    fireEvent.click(screen.getByRole('button', { name: 'Archive' }))
    fireEvent.click(screen.getByRole('button', { name: 'search.market.apply' }))
    expect(props.onLabelChange).toHaveBeenCalledWith('archive')
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('retains a label when the mobile selection has not changed', () => {
    const props = setup()
    fireEvent.click(screen.getByRole('button', { name: 'search.market.allLabels' }))
    fireEvent.click(screen.getByRole('button', { name: 'search.market.apply' }))
    expect(props.onLabelChange).not.toHaveBeenCalled()
  })
})
