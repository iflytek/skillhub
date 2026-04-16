import { beforeEach, describe, expect, it, vi } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import { createElement } from 'react'

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => vi.fn(),
}))

vi.mock('lucide-react', () => ({
  FileCheck2: () => null,
}))

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => key,
      i18n: { language: 'en' },
    }),
  }
})

vi.mock('@/shared/ui/card', () => ({
  Card: ({ children }: { children: unknown }) => children,
  CardContent: ({ children }: { children: unknown }) => children,
  CardDescription: ({ children }: { children: unknown }) => children,
  CardHeader: ({ children }: { children: unknown }) => children,
  CardTitle: ({ children }: { children: unknown }) => children,
}))

vi.mock('@/shared/ui/select', () => ({
  Select: ({ children }: { children: unknown }) => children,
  SelectContent: ({ children }: { children: unknown }) => children,
  SelectItem: ({ children }: { children: unknown }) => children,
  SelectTrigger: ({ children }: { children: unknown }) => children,
  SelectValue: () => null,
}))

vi.mock('@/shared/ui/tabs', () => ({
  Tabs: ({ children }: { children: unknown }) => children,
  TabsContent: ({ children }: { children: unknown }) => children,
  TabsList: ({ children }: { children: unknown }) => children,
  TabsTrigger: ({ children }: { children: unknown }) => children,
}))

vi.mock('@/shared/ui/table', () => ({
  Table: ({ children }: { children: unknown }) => children,
  TableBody: ({ children }: { children: unknown }) => children,
  TableCell: ({ children }: { children: unknown }) => children,
  TableHead: ({ children }: { children: unknown }) => children,
  TableHeader: ({ children }: { children: unknown }) => children,
  TableRow: ({ children }: { children: unknown }) => children,
}))

const paginationProps: Array<{ page: number; totalPages: number; onPageChange: (page: number) => void }> = []
vi.mock('@/shared/components/pagination', () => ({
  Pagination: (props: { page: number; totalPages: number; onPageChange: (page: number) => void }) => {
    paginationProps.push(props)
    return null
  },
}))

const useReviewListMock = vi.fn()
vi.mock('@/features/review/use-review-list', () => ({
  useReviewList: (...args: unknown[]) => useReviewListMock(...args),
}))

const hasRoleMock = vi.fn()
const userMock = { platformRoles: ['SKILL_ADMIN'] }
vi.mock('@/features/auth/use-auth', () => ({
  useAuth: () => ({ hasRole: hasRoleMock, user: userMock }),
}))

const useMyNamespacesMock = vi.fn()
vi.mock('@/shared/hooks/use-namespace-queries', () => ({
  useMyNamespaces: () => useMyNamespacesMock(),
}))

vi.mock('@/shared/components/dashboard-page-header', () => ({
  DashboardPageHeader: () => null,
}))

vi.mock('@/shared/lib/date-time', () => ({
  formatLocalDateTime: (v: string) => v,
}))

vi.mock('./profile-review-table', () => ({
  ProfileReviewTable: () => null,
}))

import { ReviewsPage } from './reviews'

describe('ReviewsPage', () => {
  function createReviewItem(id: number) {
    return {
      id,
      namespace: 'demo',
      skillSlug: `skill-${id}`,
      version: '1.0.0',
      submittedBy: 'user-1',
      submittedByName: 'User 1',
      submittedAt: '2026-04-01T12:00:00Z',
      reviewedBy: null,
      reviewedByName: null,
      reviewedAt: null,
      reviewComment: null,
    }
  }

  beforeEach(() => {
    paginationProps.length = 0
    hasRoleMock.mockReset()
    useReviewListMock.mockReset()
    useMyNamespacesMock.mockReset()
    hasRoleMock.mockImplementation((role: string) => role === 'SKILL_ADMIN')
    userMock.platformRoles = ['SKILL_ADMIN']
    useMyNamespacesMock.mockReturnValue({
      data: [],
      isLoading: false,
    })
    useReviewListMock.mockImplementation((status: string, _namespaceId: unknown, page: number, _size: number, _sortDirection: string, enabled: boolean) => {
      if (!enabled) {
        return { data: null, isLoading: false }
      }

      if (status === 'PENDING') {
        return {
          data: {
            items: [createReviewItem(1)],
            totalElements: 21,
            totalPages: 2,
            page,
            size: 20,
            total: 21,
          },
          isLoading: false,
        }
      }

      return { data: null, isLoading: false }
    })
  })

  it('exports a named component function', () => {
    expect(typeof ReviewsPage).toBe('function')
  })

  it('renders pagination for pending reviews when totalPages > 1', () => {
    const html = renderToStaticMarkup(createElement(ReviewsPage))

    expect(html).toContain('reviews.pageSummary')
    expect(paginationProps).toHaveLength(1)
    expect(paginationProps[0]?.page).toBe(0)
    expect(paginationProps[0]?.totalPages).toBe(2)
  })

  it('renders disabled-style pagination when there is only one page', () => {
    useReviewListMock.mockImplementation((status: string, _namespaceId: unknown, page: number, _size: number, _sortDirection: string, enabled: boolean) => {
      if (!enabled || status !== 'PENDING') {
        return { data: null, isLoading: false }
      }

      return {
        data: {
          items: [createReviewItem(2)],
          totalElements: 1,
          totalPages: 1,
          page,
          size: 20,
          total: 1,
        },
        isLoading: false,
      }
    })

    renderToStaticMarkup(createElement(ReviewsPage))

    expect(paginationProps).toHaveLength(1)
    expect(paginationProps[0]?.page).toBe(0)
    expect(paginationProps[0]?.totalPages).toBe(1)
  })
})
