import { renderToStaticMarkup } from 'react-dom/server'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const useAdminLabelDefinitionsMock = vi.fn()

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

vi.mock('@/shared/lib/date-time', () => ({
  formatLocalDateTime: (value: string) => value,
}))

vi.mock('@/shared/lib/toast', () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}))

vi.mock('@/features/admin/use-admin-labels', () => ({
  useAdminLabelDefinitions: () => useAdminLabelDefinitionsMock(),
  useCreateAdminLabel: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useUpdateAdminLabel: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useDeleteAdminLabel: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useUpdateAdminLabelSortOrder: () => ({ mutateAsync: vi.fn(), isPending: false }),
}))

import { AdminLabelsPage } from './labels'

describe('AdminLabelsPage', () => {
  beforeEach(() => {
    useAdminLabelDefinitionsMock.mockReturnValue({
      data: [],
      isLoading: false,
    })
  })

  it('shows the empty state when there are no label definitions', () => {
    const html = renderToStaticMarkup(<AdminLabelsPage />)

    expect(html).toContain('adminLabels.empty')
  })

  it('renders existing label definitions in the table', () => {
    useAdminLabelDefinitionsMock.mockReturnValue({
      data: [
        {
          slug: 'official',
          type: 'RECOMMENDED',
          visibleInFilter: true,
          sortOrder: 0,
          translations: [{ locale: 'en', displayName: 'Official' }],
          createdAt: '2026-03-20T00:00:00Z',
        },
      ],
      isLoading: false,
    })

    const html = renderToStaticMarkup(<AdminLabelsPage />)

    expect(html).toContain('official')
    expect(html).toContain('Official')
    expect(html).toContain('adminLabels.editAction')
    expect(html).toContain('adminLabels.deleteAction')
  })
})
