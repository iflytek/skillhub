/** @vitest-environment jsdom */

import { act, cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const useMyNamespacesPageMock = vi.hoisted(() => vi.fn())

vi.mock('@/shared/hooks/use-namespace-queries', () => ({
  useMyNamespacesPage: useMyNamespacesPageMock,
}))

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}))

import { NamespacePicker } from './namespace-picker'

const firstPage = {
  items: [
    {
      id: 1,
      slug: 'active-team',
      displayName: 'Active Team',
      status: 'ACTIVE',
      type: 'TEAM',
      immutable: false,
      canFreeze: false,
      canUnfreeze: false,
      canArchive: false,
      canRestore: false,
      canDelete: false,
    },
  ],
  total: 21,
  page: 0,
  size: 20,
}

describe('NamespacePicker', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    useMyNamespacesPageMock.mockImplementation((params: { page?: number }) => ({
      data: params.page === 1
        ? { ...firstPage, items: [{ ...firstPage.items[0], id: 21, slug: 'next-team', displayName: 'Next Team' }], page: 1 }
        : firstPage,
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    }))
  })

  afterEach(() => {
    cleanup()
    vi.useRealTimers()
    useMyNamespacesPageMock.mockReset()
  })

  it('debounces an active-only namespace search without loading all pages', () => {
    render(<NamespacePicker value="" onValueChange={vi.fn()} status="ACTIVE" />)

    expect(useMyNamespacesPageMock).toHaveBeenLastCalledWith({
      page: 0,
      size: 20,
      status: 'ACTIVE',
    }, false)

    fireEvent.click(screen.getByRole('button', { name: 'namespacePicker.placeholder' }))
    fireEvent.change(screen.getByRole('searchbox', { name: 'namespacePicker.search' }), {
      target: { value: 'team ai' },
    })

    expect(useMyNamespacesPageMock).not.toHaveBeenCalledWith(expect.objectContaining({ q: 'team ai' }), true)
    act(() => vi.advanceTimersByTime(300))
    expect(useMyNamespacesPageMock).toHaveBeenLastCalledWith({
      page: 0,
      size: 20,
      status: 'ACTIVE',
      q: 'team ai',
    }, true)
  })

  it('keeps the current value in the accessible name when associated with a label', () => {
    render(
      <>
        <label htmlFor="namespace">Namespace</label>
        <NamespacePicker
          id="namespace"
          accessibleLabel="Namespace"
          value="active-team"
          onValueChange={vi.fn()}
        />
      </>,
    )

    expect(document.getElementById('namespace')).toBe(
      screen.getByRole('button', { name: 'Namespace: @active-team' }),
    )
  })

  it('paginates bounded results and emits the selected slug', () => {
    const onValueChange = vi.fn()
    render(<NamespacePicker value="selected-outside-page" onValueChange={onValueChange} />)

    expect(screen.getByRole('button', { name: '@selected-outside-page' })).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: '@selected-outside-page' }))
    fireEvent.click(screen.getByRole('button', { name: 'namespacePicker.next' }))

    expect(useMyNamespacesPageMock).toHaveBeenLastCalledWith({ page: 1, size: 20 }, true)
    fireEvent.click(screen.getByRole('button', { name: 'Next Team (@next-team)' }))

    expect(onValueChange).toHaveBeenCalledWith('next-team')
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('supports clearing an optional namespace filter', () => {
    const onValueChange = vi.fn()
    render(
      <NamespacePicker
        value="active-team"
        onValueChange={onValueChange}
        emptyValueLabel="All namespaces"
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: '@active-team' }))
    fireEvent.click(screen.getByRole('button', { name: 'All namespaces' }))

    expect(onValueChange).toHaveBeenCalledWith('')
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('uses the optional empty label for an empty trigger value', () => {
    render(
      <NamespacePicker
        value=""
        onValueChange={vi.fn()}
        emptyValueLabel="All namespaces"
      />,
    )

    expect(screen.getByRole('button', { name: 'All namespaces' })).toBeTruthy()
  })
})
