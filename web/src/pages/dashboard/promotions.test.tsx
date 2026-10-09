/** @vitest-environment jsdom */
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { PromotionStatus, PromotionTask } from '@/api/types'

const mocks = vi.hoisted(() => ({
  approveMutate: vi.fn(),
  rejectMutate: vi.fn(),
  approveRevocation: vi.fn(),
  rejectRevocation: vi.fn(),
  pendingRevocations: vi.fn(),
  revocationHistory: vi.fn(),
  usePromotionList: vi.fn(),
  paginationProps: [] as Array<{ page: number; totalPages: number; onPageChange: (page: number) => void }>,
  translations: {
    'promotions.approve': 'Approve',
    'promotions.approveRevocation': 'Approve revocation',
    'promotions.colReviewComment': 'Review Comment',
    'promotions.colReviewedAt': 'Reviewed At',
    'promotions.colReviewer': 'Reviewer',
    'promotions.colSkill': 'Skill',
    'promotions.colSubmitter': 'Submitter',
    'promotions.colVersion': 'Version',
    'promotions.commentPlaceholder': 'Review comment (optional)',
    'promotions.downloadCountTag': '{{value}} downloads',
    'promotions.empty': 'No promotion requests',
    'promotions.emptyValue': '-',
    'promotions.fileCountTag': '{{count}} files',
    'promotions.kindInitial': 'Initial promotion',
    'promotions.kindUpdate': 'Update global skill',
    'promotions.targetVersionTag': 'Current global v{{version}}',
    'promotions.updateReviewHint': 'Approval adds a version to the existing global skill.',
    'promotions.historyTableLabel': 'Promotion history',
    'promotions.packageSizeTag': '{{size}}',
    'promotions.reject': 'Reject',
    'promotions.rejectRevocation': 'Reject revocation',
    'promotions.revocationConfirmTitle': 'Confirm revocation approval',
    'promotions.revocationConfirmDescription': 'Approval deletes @global/{{slug}}.',
    'promotions.revocationIds': 'Source skill #{{source}} · Global skill #{{target}}',
    'promotions.revocationReason': 'Reason: {{reason}}',
    'promotions.revocationActors': 'Requested by {{submitter}} · Reviewed by {{reviewer}}',
    'promotions.revocationStatus.APPROVED': 'Revoked',
    'promotions.sortReviewedTimeAsc': 'Sort by reviewed time ascending',
    'promotions.sortReviewedTimeDesc': 'Sort by reviewed time descending',
    'promotions.starCountTag': '{{value}} stars',
    'promotions.submitterTag': 'Submitter {{user}}',
    'promotions.subtitle': 'Review promotion requests',
    'promotions.tabApproved': 'Approved',
    'promotions.tabPending': 'Pending',
    'promotions.tabRevocations': 'Revocations to review',
    'promotions.tabRevocationHistory': 'Revocation history',
    'promotions.tabRejected': 'Rejected',
    'promotions.title': 'Promotion Review',
    'promotions.versionTag': 'v{{version}}',
  } as Record<string, string>,
}))

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      i18n: { language: 'en' },
      t: (key: string, values?: Record<string, unknown>) => {
        const template = mocks.translations[key] ?? key
        return Object.entries(values ?? {}).reduce(
          (result, [name, value]) => result.split(`{{${name}}}`).join(String(value)),
          template,
        )
      },
    }),
  }
})

vi.mock('@/features/promotion/use-promotion-list', () => ({
  useApprovePromotion: () => ({ mutate: mocks.approveMutate, isPending: false }),
  usePromotionList: (params: unknown) => mocks.usePromotionList(params),
  useRejectPromotion: () => ({ mutate: mocks.rejectMutate, isPending: false }),
}))

vi.mock('@/features/promotion/use-promotion-revocations', () => ({
  useApprovePromotionRevocation: () => ({ mutateAsync: mocks.approveRevocation, isPending: false }),
  usePendingPromotionRevocations: () => mocks.pendingRevocations(),
  useAdminPromotionRevocationHistory: (...args: unknown[]) => mocks.revocationHistory(...args),
  useRejectPromotionRevocation: () => ({ mutateAsync: mocks.rejectRevocation, isPending: false }),
}))

vi.mock('@/shared/lib/toast', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))

vi.mock('@/shared/components/dashboard-page-header', () => ({
  DashboardPageHeader: ({ title, subtitle }: { title: string; subtitle: string }) => (
    <header>
      <h1>{title}</h1>
      <p>{subtitle}</p>
    </header>
  ),
}))

vi.mock('@/shared/components/pagination', () => ({
  Pagination: (props: { page: number; totalPages: number; onPageChange: (page: number) => void }) => {
    mocks.paginationProps.push(props)
    return (
      <button type="button" onClick={() => props.onPageChange(props.page + 1)}>
        pagination:{props.page}/{props.totalPages}
      </button>
    )
  },
}))

import { PromotionsPage } from './promotions'

function createPromotion(overrides: Partial<PromotionTask> = {}): PromotionTask {
  return {
    id: 1,
    sourceSkillId: 101,
    sourceSkillDisplayName: 'Knowledge Helper',
    sourceSkillSummary: 'Summary for Knowledge Helper',
    sourceNamespace: 'team-ai',
    sourceSkillSlug: 'knowledge-helper',
    sourceVersion: '1.3.0',
    sourceVersionFileCount: 23,
    sourceVersionTotalSize: 1_843_200,
    sourceSkillDownloadCount: 18,
    sourceSkillStarCount: 5,
    targetNamespace: 'global',
    targetSkillId: null,
    status: 'PENDING',
    submittedBy: 'owner-1',
    submittedByName: 'Owner One',
    reviewedBy: null,
    reviewedByName: null,
    reviewComment: null,
    submittedAt: '2026-06-18T12:00:00Z',
    reviewedAt: null,
    ...overrides,
  }
}

function installPromotionListMock(overrides: {
  pending?: PromotionTask[]
  pendingTotal?: number
  approvedDesc?: PromotionTask[]
  approvedTotal?: number
  approvedAsc?: PromotionTask[]
  rejectedDesc?: PromotionTask[]
  rejectedTotal?: number
  rejectedAsc?: PromotionTask[]
} = {}) {
  const pending = overrides.pending ?? [createPromotion()]
  const approvedDesc = overrides.approvedDesc ?? [
    createPromotion({
      id: 2,
      status: 'APPROVED',
      sourceSkillDisplayName: 'Newest Approved',
      sourceSkillSlug: 'newest-approved',
      reviewedBy: 'admin-1',
      reviewedByName: 'Admin',
      reviewComment: 'Looks good.',
      reviewedAt: '2026-06-18T09:00:00Z',
    }),
    createPromotion({
      id: 3,
      status: 'APPROVED',
      sourceSkillDisplayName: 'Oldest Approved',
      sourceSkillSlug: 'oldest-approved',
      reviewedBy: 'admin-1',
      reviewedByName: 'Admin',
      reviewComment: 'Approved after review.',
      reviewedAt: '2026-06-17T09:00:00Z',
    }),
  ]
  const rejectedDesc = overrides.rejectedDesc ?? [
    createPromotion({
      id: 4,
      status: 'REJECTED',
      sourceSkillDisplayName: 'Newest Rejected',
      sourceSkillSlug: 'newest-rejected',
      reviewedBy: 'admin-1',
      reviewedByName: 'Admin',
      reviewComment: 'Needs clearer docs before promotion.',
      reviewedAt: '2026-06-18T08:00:00Z',
    }),
    createPromotion({
      id: 5,
      status: 'REJECTED',
      sourceSkillDisplayName: 'Oldest Rejected',
      sourceSkillSlug: 'oldest-rejected',
      reviewedBy: 'admin-1',
      reviewedByName: 'Admin',
      reviewComment: null,
      reviewedAt: '2026-06-16T08:00:00Z',
    }),
  ]
  const approvedAsc = overrides.approvedAsc ?? [...approvedDesc].reverse()
  const rejectedAsc = overrides.rejectedAsc ?? [...rejectedDesc].reverse()

  mocks.usePromotionList.mockImplementation((params: {
    status?: PromotionStatus
    page?: number
    size?: number
    sortDirection?: 'ASC' | 'DESC'
  } = {}) => {
    const page = params.page ?? 0
    const size = params.size ?? 20
    if (params.status === 'APPROVED') {
      return {
        data: { items: params.sortDirection === 'ASC' ? approvedAsc : approvedDesc, total: overrides.approvedTotal ?? approvedDesc.length, page, size },
        isLoading: false,
      }
    }
    if (params.status === 'REJECTED') {
      return {
        data: { items: params.sortDirection === 'ASC' ? rejectedAsc : rejectedDesc, total: overrides.rejectedTotal ?? rejectedDesc.length, page, size },
        isLoading: false,
      }
    }
    return {
      data: { items: pending, total: overrides.pendingTotal ?? pending.length, page, size },
      isLoading: false,
    }
  })
}

describe('PromotionsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.paginationProps.length = 0
    installPromotionListMock()
    mocks.pendingRevocations.mockReturnValue({ data: [], isLoading: false, error: null })
    mocks.revocationHistory.mockReturnValue({ data: { items: [], total: 0, page: 0, size: 20 }, isLoading: false, error: null })
  })

  afterEach(() => cleanup())

  it('renders enhanced pending card review context', () => {
    render(<PromotionsPage />)

    expect(screen.getByRole('heading', { name: 'Promotion Review' })).toBeTruthy()
    expect(screen.getByText('Knowledge Helper')).toBeTruthy()
    expect(screen.getByText('@team-ai/knowledge-helper -> @global')).toBeTruthy()
    expect(screen.getByText('Summary for Knowledge Helper')).toBeTruthy()
    expect(screen.getByText('v1.3.0')).toBeTruthy()
    expect(screen.getByText('Submitter Owner One')).toBeTruthy()
    expect(screen.getByText('23 files')).toBeTruthy()
    expect(screen.getByText('1.8 MB')).toBeTruthy()
    expect(screen.getByText('18 downloads')).toBeTruthy()
    expect(screen.getByText('5 stars')).toBeTruthy()
  })

  it('reviews a revocation only after a destructive confirmation', async () => {
    mocks.pendingRevocations.mockReturnValue({
      data: [{ id: 7, sourceSkillId: 101, targetSkillId: 202, skillSlug: 'knowledge-helper', submittedAt: '2026-06-18T12:00:00Z', submittedBy: 'owner-1', reason: 'Outdated' }],
      isLoading: false,
      error: null,
    })
    mocks.approveRevocation.mockResolvedValue(undefined)
    render(<PromotionsPage />)

    fireEvent.click(screen.getByRole('tab', { name: 'Revocations to review' }))
    expect(screen.getByText('@global/knowledge-helper')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Approve revocation' }))
    expect(mocks.approveRevocation).not.toHaveBeenCalled()
    const dialog = screen.getByRole('dialog', { name: 'Confirm revocation approval' })
    fireEvent.click(within(dialog).getByRole('button', { name: 'Approve revocation' }))
    await waitFor(() => expect(mocks.approveRevocation).toHaveBeenCalledWith({ id: 7, comment: '' }))
  })

  it('shows reviewed revocations in the admin history tab', () => {
    mocks.revocationHistory.mockReturnValue({
      data: { items: [{ id: 8, status: 'APPROVED', sourceSkillId: 101, targetSkillId: 202, skillSlug: 'knowledge-helper', submittedBy: 'owner-1', reviewedBy: 'admin-1', reviewedAt: '2026-06-18T13:00:00Z', reason: 'Outdated', reviewComment: null }], total: 1, page: 0, size: 20 },
      isLoading: false,
      error: null,
    })
    render(<PromotionsPage />)
    fireEvent.click(screen.getByRole('tab', { name: 'Revocation history' }))
    expect(mocks.revocationHistory).toHaveBeenCalledWith(0, 20)
    expect(screen.getByText('@global/knowledge-helper')).toBeTruthy()
    expect(screen.getByText(/Revoked/)).toBeTruthy()
  })

  it('rejects a revocation request with the entered review comment', async () => {
    mocks.pendingRevocations.mockReturnValue({
      data: [{ id: 7, sourceSkillId: 101, targetSkillId: 202, skillSlug: 'knowledge-helper', submittedAt: '2026-06-18T12:00:00Z', submittedBy: 'owner-1', reason: null }],
      isLoading: false,
      error: null,
    })
    mocks.rejectRevocation.mockResolvedValue(undefined)
    render(<PromotionsPage />)
    fireEvent.click(screen.getByRole('tab', { name: 'Revocations to review' }))
    fireEvent.change(screen.getByRole('textbox', { name: 'Review comment (optional)' }), { target: { value: 'Keep the global version' } })
    fireEvent.click(screen.getByRole('button', { name: 'Reject revocation' }))
    await waitFor(() => expect(mocks.rejectRevocation).toHaveBeenCalledWith({ id: 7, comment: 'Keep the global version' }))
  })

  it('shows the source and current global versions for an update request', () => {
    installPromotionListMock({ pending: [createPromotion({ requestKind: 'UPDATE', sourceVersion: '1.1', targetCurrentVersion: '1.3', targetSkillId: 202 })] })
    render(<PromotionsPage />)

    expect(screen.getByText('Update global skill')).toBeTruthy()
    expect(screen.getByText('v1.1')).toBeTruthy()
    expect(screen.getByText('Current global v1.3')).toBeTruthy()
    expect(screen.getByText('Approval adds a version to the existing global skill.')).toBeTruthy()
  })

  it('paginates pending and history queues independently', () => {
    installPromotionListMock({ pendingTotal: 21, approvedTotal: 21 })
    render(<PromotionsPage />)

    expect(mocks.usePromotionList).toHaveBeenCalledWith({
      status: 'PENDING',
      page: 0,
      size: 20,
    })
    fireEvent.click(screen.getByRole('button', { name: 'pagination:0/2' }))
    expect(mocks.usePromotionList).toHaveBeenCalledWith({
      status: 'PENDING',
      page: 1,
      size: 20,
    })
    expect(screen.getByRole('button', { name: 'pagination:1/2' })).toBeTruthy()

    fireEvent.click(screen.getByRole('tab', { name: 'Approved' }))
    expect(mocks.usePromotionList).toHaveBeenCalledWith({
      status: 'APPROVED',
      page: 0,
      size: 20,
      sortBy: 'reviewedAt',
      sortDirection: 'DESC',
    })
    expect(screen.getByRole('button', { name: 'pagination:0/2' })).toBeTruthy()
  })

  it('returns to the last valid page when a mutation empties the current page', async () => {
    const pending = createPromotion()
    mocks.usePromotionList.mockImplementation((params: { status?: PromotionStatus; page?: number } = {}) => {
      const page = params.page ?? 0
      return {
        data: {
          items: params.status === 'PENDING' && page === 0 ? [pending] : [],
          total: params.status === 'PENDING' ? 20 : 0,
          page,
          size: 20,
        },
        isLoading: false,
      }
    })
    render(<PromotionsPage />)

    mocks.paginationProps[0]?.onPageChange(1)

    await waitFor(() => {
      expect(mocks.usePromotionList).toHaveBeenLastCalledWith({
        status: 'PENDING',
        page: 0,
        size: 20,
      })
    })
    expect(screen.getByText('Knowledge Helper')).toBeTruthy()
  })

  it('renders approved history as a sortable table', () => {
    render(<PromotionsPage />)

    fireEvent.click(screen.getByRole('tab', { name: 'Approved' }))
    const table = screen.getByRole('table', { name: 'Promotion history' })
    let rows = within(table).getAllByRole('row')
    expect(rows[1]?.textContent).toContain('Newest Approved')
    expect(rows[2]?.textContent).toContain('Oldest Approved')

    const ascendingButton = screen.getByRole('button', { name: 'Sort by reviewed time ascending' })
    expect(ascendingButton.closest('th')?.getAttribute('aria-sort')).toBe('descending')
    expect(ascendingButton.querySelector('[aria-hidden="true"]')).toBeTruthy()

    fireEvent.click(ascendingButton)
    rows = within(screen.getByRole('table', { name: 'Promotion history' })).getAllByRole('row')
    expect(rows[1]?.textContent).toContain('Oldest Approved')
    expect(rows[2]?.textContent).toContain('Newest Approved')
    const descendingButton = screen.getByRole('button', { name: 'Sort by reviewed time descending' })
    expect(descendingButton.closest('th')?.getAttribute('aria-sort')).toBe('ascending')
  })

  it('keeps approved and rejected sort state independent', () => {
    render(<PromotionsPage />)

    fireEvent.click(screen.getByRole('tab', { name: 'Approved' }))
    fireEvent.click(screen.getByRole('button', { name: 'Sort by reviewed time ascending' }))
    expect(screen.getByRole('button', { name: 'Sort by reviewed time descending' })).toBeTruthy()

    fireEvent.click(screen.getByRole('tab', { name: 'Rejected' }))
    expect(screen.getByRole('button', { name: 'Sort by reviewed time ascending' })).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Sort by reviewed time ascending' }))
    expect(screen.getByRole('button', { name: 'Sort by reviewed time descending' })).toBeTruthy()

    fireEvent.click(screen.getByRole('tab', { name: 'Approved' }))
    expect(screen.getByRole('button', { name: 'Sort by reviewed time descending' })).toBeTruthy()
  })
})
