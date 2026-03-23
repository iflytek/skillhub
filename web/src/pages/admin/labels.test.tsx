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

import { AdminLabelsPage, normalizeLabelFormState, validateLabelFormState } from './labels'

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

  it('shows the create button', () => {
    const html = renderToStaticMarkup(<AdminLabelsPage />)

    expect(html).toContain('adminLabels.createAction')
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

  it('renders edit and delete actions for each label definition', () => {
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
        {
          slug: 'verified',
          type: 'PRIVILEGED',
          visibleInFilter: false,
          sortOrder: 1,
          translations: [{ locale: 'en', displayName: 'Verified' }],
          createdAt: '2026-03-21T00:00:00Z',
        },
      ],
      isLoading: false,
    })

    const html = renderToStaticMarkup(<AdminLabelsPage />)

    expect(html).toContain('official')
    expect(html).toContain('verified')
    expect(html).toContain('adminLabels.editAction')
    expect(html).toContain('adminLabels.deleteAction')
  })

  it('normalizes slugs and locales before submission', () => {
    const normalized = normalizeLabelFormState({
      slug: ' Code-Generation ',
      type: 'RECOMMENDED',
      visibleInFilter: true,
      sortOrder: Number.NaN,
      translations: [
        { locale: ' ZH_cn ', displayName: ' 代码生成 ' },
        { locale: ' ', displayName: ' ' },
      ],
    })

    expect(normalized.slug).toBe('code-generation')
    expect(normalized.sortOrder).toBe(0)
    expect(normalized.translations).toEqual([{ locale: 'zh-cn', displayName: '代码生成' }])
  })

  it('rejects invalid slug patterns and duplicate locales in validation', () => {
    expect(validateLabelFormState({
      slug: 'code_generation',
      type: 'RECOMMENDED',
      visibleInFilter: true,
      sortOrder: 0,
      translations: [{ locale: 'en', displayName: 'Code Generation' }],
    })).toEqual({
      titleKey: 'adminLabels.validationSlugTitle',
      descriptionKey: 'adminLabels.validationSlugPatternDescription',
    })

    expect(validateLabelFormState({
      slug: 'code-generation',
      type: 'RECOMMENDED',
      visibleInFilter: true,
      sortOrder: 0,
      translations: [
        { locale: 'en', displayName: 'Code Generation' },
        { locale: 'en', displayName: 'Code Generation Copy' },
      ],
    })).toEqual({
      titleKey: 'adminLabels.validationTranslationsTitle',
      descriptionKey: 'adminLabels.validationDuplicateLocaleDescription',
    })
  })

  it('rejects empty slug in validation', () => {
    expect(validateLabelFormState({
      slug: '',
      type: 'RECOMMENDED',
      visibleInFilter: true,
      sortOrder: 0,
      translations: [{ locale: 'en', displayName: 'Test' }],
    })).toEqual({
      titleKey: 'adminLabels.validationSlugTitle',
      descriptionKey: 'adminLabels.validationSlugDescription',
    })
  })

  it('rejects empty translations in validation', () => {
    expect(validateLabelFormState({
      slug: 'valid-slug',
      type: 'RECOMMENDED',
      visibleInFilter: true,
      sortOrder: 0,
      translations: [],
    })).toEqual({
      titleKey: 'adminLabels.validationTranslationsTitle',
      descriptionKey: 'adminLabels.validationTranslationsDescription',
    })
  })

  it('accepts valid form state in validation', () => {
    expect(validateLabelFormState({
      slug: 'valid-slug',
      type: 'RECOMMENDED',
      visibleInFilter: true,
      sortOrder: 0,
      translations: [{ locale: 'en', displayName: 'Valid Label' }],
    })).toBeNull()
  })

  it('rejects slug with consecutive hyphens', () => {
    expect(validateLabelFormState({
      slug: 'bad--slug',
      type: 'RECOMMENDED',
      visibleInFilter: true,
      sortOrder: 0,
      translations: [{ locale: 'en', displayName: 'Bad' }],
    })).toEqual({
      titleKey: 'adminLabels.validationSlugTitle',
      descriptionKey: 'adminLabels.validationSlugPatternDescription',
    })
  })
})
