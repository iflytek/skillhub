import { describe, expect, it, vi } from 'vitest'

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

vi.mock('@/shared/ui/button', () => ({
  Button: ({ children }: { children: unknown }) => children,
}))

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

vi.mock('@/features/review/use-review-list', () => ({
  useReviewList: () => ({ data: null, isLoading: false }),
}))

vi.mock('@/features/auth/use-auth', () => ({
  useAuth: () => ({ hasRole: () => false }),
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
  it('exports a named component function', () => {
    expect(typeof ReviewsPage).toBe('function')
  })
})
