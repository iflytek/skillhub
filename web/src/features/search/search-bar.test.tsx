/** @vitest-environment jsdom */
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { SearchBar } from './search-bar'

vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }))

describe('SearchBar explicit submission', () => {
  afterEach(cleanup)

  it('does not search when typing or clearing, but submits on confirmation', () => {
    const onSearch = vi.fn()
    render(<SearchBar onSearch={onSearch} />)
    const input = screen.getByRole('textbox')
    fireEvent.change(input, { target: { value: 'agent' } })
    expect(onSearch).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: 'searchBar.button' }))
    expect(onSearch).toHaveBeenLastCalledWith('agent')
    onSearch.mockClear()
    fireEvent.click(screen.getByRole('button', { name: 'searchBar.clear' }))
    expect(onSearch).not.toHaveBeenCalled()
    fireEvent.submit(input.closest('form')!)
    expect(onSearch).toHaveBeenCalledWith('')
  })
})
