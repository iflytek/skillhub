/** @vitest-environment jsdom */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { Children, isValidElement, type ReactNode } from 'react'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import type { ManagedNamespace } from '@/api/types'

const mocks = vi.hoisted(() => ({
  search: {} as Record<string, unknown>, navigate: vi.fn(), list: vi.fn(), refetch: vi.fn(), namespaces: [] as ManagedNamespace[], roles: ['SKILL_ADMIN'], scopes: undefined as { id: number; slug: string; displayName: string }[] | undefined, scopeError: false,
}))
vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => mocks.navigate, useSearch: () => mocks.search,
  Link: ({ children, to }: { children: ReactNode; to: string }) => <a href={to}>{children}</a>,
}))
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }) }))
vi.mock('@/features/auth/use-auth', () => ({ useAuth: () => ({ hasRole: (role: string) => mocks.roles.includes(role) }) }))
vi.mock('@/shared/hooks/use-namespace-queries', () => ({
  useMyNamespaces: () => ({ data: mocks.namespaces, isLoading: false }),
  useNamespaceDetail: (slug: string) => ({ data: mocks.namespaces.find((namespace) => namespace.slug === slug), isLoading: false }),
}))
vi.mock('@/features/review/use-review-scopes', () => ({ useReviewScopes: () => ({ data: mocks.scopeError ? undefined : mocks.scopes ?? mocks.namespaces, isLoading: false, isError: mocks.scopeError, refetch: mocks.refetch }) }))
vi.mock('@/features/review/use-review-list', () => ({ useReviewList: (...args: unknown[]) => mocks.list(...args) }))
vi.mock('@/features/skill/skill-avatar', () => ({ SkillAvatar: () => <span /> }))
vi.mock('./profile-review-table', () => ({ ProfileReviewTable: () => <div>profile-review-table</div> }))
vi.mock('@/shared/ui/select', () => {
  function descendants(children: ReactNode): Array<{ value?: string; id?: string; className?: string; children?: ReactNode }> {
    return Children.toArray(children).flatMap((child) => isValidElement<{ value?: string; id?: string; className?: string; children?: ReactNode }>(child) ? [child.props, ...descendants(child.props.children)] : [])
  }
  return {
    Select: ({ value, onValueChange, children }: { value: string; onValueChange: (value: string) => void; children: ReactNode }) => {
      const props = descendants(children)
      const trigger = props.find((element) => element.id)
      return <select id={trigger?.id} aria-label={trigger?.id ? undefined : 'sort'} value={value} onChange={(event) => onValueChange(event.target.value)}>{props.filter((element) => element.value).map((element) => <option key={element.value} value={element.value}>{descendants(element.children).length ? descendants(element.children).map((child) => typeof child.children === 'string' ? child.children : '').join('') : element.children}</option>)}</select>
    },
    SelectContent: ({ children }: { children: ReactNode }) => children,
    SelectItem: ({ children }: { children: ReactNode }) => children,
    SelectTrigger: ({ children }: { children: ReactNode }) => children,
    SelectValue: () => null,
  }
})
import { ReviewsPage, reviewTaskKind } from './reviews'
import type { ReviewTask } from '@/api/types'

function lastFilters() { return mocks.list.mock.calls.at(-1) }
function renderPage() { return render(<ReviewsPage />) }

afterEach(cleanup)
beforeEach(() => {
  vi.clearAllMocks()
  mocks.search = {}
  mocks.roles = ['SKILL_ADMIN']
  mocks.namespaces = []
  mocks.scopes = undefined
  mocks.scopeError = false
  mocks.list.mockImplementation(() => ({ data: { items: [{ id: 1, namespace: 'design', skillSlug: 'notes', displayName: 'Meeting notes', version: '1.0.0', status: 'PENDING', submittedBy: 'author', submittedAt: '2026-11-05T10:00:00Z', reviewKind: 'VERSION_UPDATE' }], total: 21, totalElements: 21, totalPages: 2 }, isLoading: false, refetch: mocks.refetch }))
})

describe('unified review center', () => {
  it('does not infer a version update or replace its primary request kind with attached avatar changes', () => {
    expect(reviewTaskKind({ subjectType: 'SKILL_VERSION' } as ReviewTask)).toBe('SKILL_VERSION')
    expect(reviewTaskKind({ subjectType: 'SKILL_METADATA', requestedVisibility: 'PUBLIC', avatarRemoval: true } as ReviewTask)).toBe('VISIBILITY_CHANGE')
    mocks.list.mockReturnValue({ data: { items: [{ id: 2, subjectType: 'SKILL_VERSION', reviewKind: 'VERSION_UPDATE', avatarRemoval: true, status: 'PENDING', submittedAt: '2026-11-05T10:00:00Z' }], totalElements: 1, totalPages: 1 }, refetch: mocks.refetch })
    renderPage()
    expect(screen.getAllByText('reviewCenter.kind.VERSION_UPDATE').filter((element) => element.tagName !== 'OPTION')).toHaveLength(2)
    expect(screen.queryByText('reviewCenter.kind.AVATAR_REMOVAL')).toBeNull()
  })

  it('offers a first-page action for an empty historical page rather than claiming no matching requests', () => {
    mocks.search = { page: 2 }
    mocks.list.mockReturnValue({ data: { items: [], totalElements: 1, totalPages: 1 }, refetch: mocks.refetch })
    renderPage()
    expect(screen.getByText('reviewCenter.pageEmpty')).toBeTruthy()
    expect(screen.queryByText('reviewCenter.empty')).toBeNull()
    fireEvent.click(screen.getByRole('button', { name: 'reviewCenter.firstPage' }))
    expect(lastFilters()?.[2]).toBe(0)
  })

  it('uses authorized scopes even when a platform reviewer is not a member', () => {
    mocks.scopes = [{ id: 90, slug: 'foreign', displayName: 'Foreign team' }]
    mocks.search = { namespace: 'foreign' }
    renderPage()
    expect(screen.getByRole('option', { name: 'Foreign team' })).toBeTruthy()
    expect(lastFilters()?.[1]).toBe(90)
    expect(lastFilters()?.[5]).toBe(true)
  })

  it('does not run an unrestricted query when the authorized scope request fails', () => {
    mocks.scopeError = true
    renderPage()
    expect(lastFilters()?.[5]).toBe(false)
    expect(screen.getByRole('alert')).toBeTruthy()
  })

  it('uses one request queue with no status or source tabs', () => {
    renderPage()
    expect(screen.queryByRole('tab')).toBeNull()
    expect(screen.getByRole('form', { name: 'reviewCenter.filters.title' })).toBeTruthy()
    expect(screen.getAllByText('Meeting notes')).toHaveLength(2)
    expect(lastFilters()?.slice(0, 6)).toEqual(['PENDING', undefined, 0, 20, 'DESC', true])
  })

  it('keeps draft changes out of the query until Search then resets the page', () => {
    mocks.search = { page: 2 }
    renderPage()
    fireEvent.change(screen.getByLabelText('reviewCenter.filters.q'), { target: { value: '  notes  ' } })
    fireEvent.change(screen.getByLabelText('reviewCenter.filters.originType'), { target: { value: 'GITHUB_SYNC' } })
    expect(lastFilters()?.[2]).toBe(2)
    expect(lastFilters()?.[6]).toEqual({ q: '', reviewKind: 'ALL', originType: 'ALL' })
    fireEvent.click(screen.getByRole('button', { name: 'reviewCenter.filters.apply' }))
    expect(lastFilters()?.[2]).toBe(0)
    expect(lastFilters()?.[6]).toEqual({ q: 'notes', reviewKind: 'ALL', originType: 'GITHUB_SYNC' })
    expect(mocks.navigate).toHaveBeenLastCalledWith(expect.objectContaining({ search: expect.objectContaining({ q: 'notes', originType: 'GITHUB_SYNC', page: undefined }) }))
  })

  it('reset restores pending requests, clears conditions and refreshes identical queries', () => {
    mocks.search = { q: 'notes', status: 'REJECTED', originType: 'USER', page: 2 }
    renderPage()
    fireEvent.click(screen.getByRole('button', { name: 'reviewCenter.filters.reset' }))
    expect(lastFilters()?.slice(0, 6)).toEqual(['PENDING', undefined, 0, 20, 'DESC', true])
    expect(lastFilters()?.[6]).toEqual({ q: '', reviewKind: 'ALL', originType: 'ALL' })
    fireEvent.click(screen.getByRole('button', { name: 'reviewCenter.filters.reset' }))
    expect(mocks.refetch).toHaveBeenCalledTimes(1)
  })

  it('paginates using applied values even while another search is being drafted', () => {
    renderPage()
    fireEvent.change(screen.getByLabelText('reviewCenter.filters.q'), { target: { value: 'unsubmitted' } })
    fireEvent.click(screen.getByRole('button', { name: /pagination.next/i }))
    expect(lastFilters()?.[2]).toBe(1)
    expect(lastFilters()?.[6]).toEqual({ q: '', reviewKind: 'ALL', originType: 'ALL' })
  })

  it('allows a team owner to use the same center but does not treat USER_ADMIN as a skill reviewer', () => {
    mocks.roles = ['USER_ADMIN']
    const first = renderPage()
    expect(screen.getByText('profile-review-table')).toBeTruthy()
    expect(lastFilters()?.[5]).toBe(false)
    first.unmount()
    mocks.roles = []
    mocks.namespaces = [{ id: 4, slug: 'design', displayName: 'Design', type: 'TEAM', currentUserRole: 'OWNER' } as ManagedNamespace]
    mocks.search = { namespace: 'design' }
    renderPage()
    expect(screen.getByRole('form')).toBeTruthy()
    expect(lastFilters()?.[1]).toBe(4)
    expect(lastFilters()?.[5]).toBe(true)
  })

  it('does not present a failed request as an empty result', () => {
    mocks.list.mockReturnValue({ isError: true, isLoading: false, refetch: mocks.refetch })
    renderPage()
    expect(screen.getByRole('alert').textContent).toContain('reviewCenter.loadFailed')
    expect(screen.queryByText('reviewCenter.empty')).toBeNull()
    fireEvent.click(screen.getByRole('button', { name: 'reviewCenter.retry' }))
    expect(mocks.refetch).toHaveBeenCalledTimes(1)
  })

  it('restores applied filter state when the URL changes', () => {
    const view = renderPage()
    mocks.search = { q: 'historic', status: 'APPROVED', page: 1 }
    view.rerender(<ReviewsPage />)
    expect(lastFilters()?.[0]).toBe('APPROVED')
    expect(lastFilters()?.[2]).toBe(1)
    expect(screen.getByLabelText<HTMLInputElement>('reviewCenter.filters.q').value).toBe('historic')
  })
})
