/** @vitest-environment jsdom */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import type { ReactNode } from 'react'
const mocks = vi.hoisted(() => ({ inbox: vi.fn(), activity: vi.fn(), refetch: vi.fn(), notifications: vi.fn(), markRead: vi.fn() }))
vi.mock('@tanstack/react-router', () => ({ Link: ({ to, children }: { to: string; children: ReactNode }) => <a href={to}>{children}</a> }))
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }))
vi.mock('@/features/governance/use-governance', () => ({ GOVERNANCE_PAGE_SIZE: 10, useGovernanceInbox: (...args: unknown[]) => mocks.inbox(...args), useGovernanceActivity: (...args: unknown[]) => mocks.activity(...args), useGovernanceNotifications: (...args: unknown[]) => mocks.notifications(...args), useMarkGovernanceNotificationRead: () => ({ mutate: mocks.markRead, isPending: false }) }))
vi.mock('@/features/governance/governance-inbox', () => ({ GovernanceInbox: ({ items }: { items?: { title: string }[] }) => <div>{items?.map((item) => <p key={item.title}>{item.title}</p>)}</div> }))
vi.mock('@/features/governance/governance-notifications', () => ({ GovernanceNotifications: ({ onMarkRead }: { onMarkRead: (id: number) => void }) => <button onClick={() => onMarkRead(8)}>mark-governance-read</button> }))
vi.mock('@/features/governance/governance-activity', () => ({ GovernanceActivity: () => <p>activity-list</p> }))
import { GovernancePage } from './governance'
afterEach(cleanup)
beforeEach(() => {
  vi.clearAllMocks()
  mocks.inbox.mockImplementation((type: string) => ({ data: { items: [{ title: type }], total: 22, size: 10 }, isLoading: false, refetch: mocks.refetch }))
  mocks.notifications.mockReturnValue({ data: { items: [], total: 0, size: 10 }, isLoading: false, refetch: mocks.refetch })
  mocks.activity.mockReturnValue({ data: { items: [], total: 0, size: 10 }, isLoading: false, refetch: mocks.refetch })
})
describe('governance responsibility boundary', () => {
  it('preserves the separate governance notification feed and mark-read action', () => {
    render(<GovernancePage />)
    expect(screen.getByText('reviewCenter.governanceNotifications').closest('details')?.open).toBe(false)
    fireEvent.click(screen.getByText('mark-governance-read'))
    expect(mocks.markRead).toHaveBeenCalledWith(8, expect.objectContaining({ onError: expect.any(Function) }))
    expect(mocks.notifications).toHaveBeenCalledWith(0, 10)
  })
  it('requests only report and promotion queues and links to the one review center', () => {
    render(<GovernancePage />)
    expect(mocks.inbox.mock.calls.map((call) => call[0])).toEqual(['REPORT', 'PROMOTION'])
    expect(screen.getByText('REPORT')).toBeTruthy()
    expect(screen.getByText('PROMOTION')).toBeTruthy()
    expect(screen.queryByRole('tab')).toBeNull()
    expect(screen.getByRole('link', { name: 'reviewCenter.governanceReviewLink' }).getAttribute('href')).toBe('/dashboard/reviews')
    expect(screen.queryByText('governance.searchMaintenanceTitle')).toBeNull()
  })
  it('keeps report and promotion pagination independent', () => {
    render(<GovernancePage />)
    fireEvent.click(screen.getAllByRole('button', { name: 'pagination.next' })[0])
    expect(mocks.inbox).toHaveBeenCalledWith('REPORT', 1, 10)
    expect(mocks.inbox).toHaveBeenLastCalledWith('PROMOTION', 0, 10)
  })
  it('provides a retry for failed governance data instead of showing an empty queue', () => {
    mocks.inbox.mockImplementation((type: string) => type === 'REPORT' ? { isError: true, refetch: mocks.refetch } : { data: { items: [], total: 0, size: 10 } })
    render(<GovernancePage />)
    expect(screen.getByRole('alert').textContent).toContain('reviewCenter.loadFailed')
    fireEvent.click(screen.getByRole('button', { name: 'reviewCenter.retry' }))
    expect(mocks.refetch).toHaveBeenCalledTimes(1)
  })
})
