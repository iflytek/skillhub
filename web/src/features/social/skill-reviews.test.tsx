/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import en from '@/i18n/locales/en.json'
import ru from '@/i18n/locales/ru.json'
import zh from '@/i18n/locales/zh.json'

const mocks = vi.hoisted(() => ({
  pages: new Map<number, { items: Array<Record<string, unknown>>; total: number; page: number; size: number }>(),
  requestedPages: [] as number[],
  saveMutate: vi.fn(),
  clearMutate: vi.fn(),
  moderateMutate: vi.fn(),
  mineEnabled: [] as boolean[],
}))

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  const translations: Record<string, string> = {
    'skillReviews.title': 'User reviews',
    'skillReviews.count': '{{count}} reviews',
    'skillReviews.write': 'Write a review',
    'skillReviews.edit': 'Edit my review',
    'skillReviews.scoreLabel': 'Your score',
    'skillReviews.ratingDisplay': 'Rating: {{score}} out of 5 stars',
    'skillReviews.ratingOption': 'Rate {{score}} out of 5 stars',
    'skillReviews.reviewTextLabel': 'Review text',
    'skillReviews.placeholder': 'Share your experience',
    'skillReviews.save': 'Save review',
    'skillReviews.cancel': 'Cancel',
    'skillReviews.delete': 'Delete review',
    'skillReviews.empty': 'No reviews',
    'skillReviews.previous': 'Previous',
    'skillReviews.next': 'Next',
  }
  return {
    ...actual,
    useTranslation: () => ({
      i18n: { language: 'en' },
      t: (key: string, values?: Record<string, unknown>) => Object.entries(values ?? {}).reduce(
        (text, [name, value]) => text.replace(`{{${name}}}`, String(value)),
        translations[key] ?? key,
      ),
    }),
  }
})

vi.mock('@/features/auth/use-auth', () => ({
  useAuth: () => ({ isAuthenticated: true, hasRole: () => false }),
}))

vi.mock('@/shared/lib/date-time', () => ({
  formatLocalDateTime: (value: string) => value,
}))

vi.mock('@/shared/lib/toast', () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}))

vi.mock('./use-skill-reviews', () => ({
  useSkillReviews: (_skillId: number, page: number) => {
    mocks.requestedPages.push(page)
    return { data: mocks.pages.get(page), isLoading: false, isError: false }
  },
  useMySkillReview: (_skillId: number, enabled: boolean) => {
    mocks.mineEnabled.push(enabled)
    return { data: {
      rated: true,
      score: 4,
      reviewed: true,
      reviewId: 7,
      reviewText: 'Useful review',
      status: 'VISIBLE',
      updatedAt: '2026-09-01T00:00:00Z',
    } }
  },
  useUpsertSkillReview: () => ({ mutate: mocks.saveMutate, isPending: false }),
  useClearSkillReview: () => ({ mutate: mocks.clearMutate, isPending: false }),
  useModerateSkillReview: () => ({ mutate: mocks.moderateMutate, isPending: false }),
}))

import { SkillReviews } from './skill-reviews'

describe('skill reviews', () => {
  beforeEach(() => {
    mocks.pages.clear()
    mocks.pages.set(0, { items: [], total: 0, page: 0, size: 20 })
    mocks.requestedPages.length = 0
    mocks.mineEnabled.length = 0
  })

  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
  })

  it('exposes an accessible rating group and labelled review editor', () => {
    render(<SkillReviews skillId={10} canInteract onRequireLogin={vi.fn()} />)

    fireEvent.click(screen.getByRole('button', { name: 'Edit my review' }))

    const rating = screen.getByRole('radiogroup', { name: 'Your score' })
    expect(rating).toBeTruthy()
    expect(screen.getByRole('radio', { name: 'Rate 4 out of 5 stars' }))
      .toHaveProperty('checked', true)
    expect(screen.getByRole('textbox', { name: 'Review text' })).toHaveProperty('value', 'Useful review')
  })

  it('keeps a way back when a refetch empties the last page', () => {
    mocks.pages.set(0, { items: [], total: 21, page: 0, size: 20 })
    mocks.pages.set(1, {
      items: [{
        id: 8,
        displayName: 'Alice',
        score: 5,
        reviewText: 'Great',
        status: 'VISIBLE',
        authoredByViewer: false,
        createdAt: '2026-09-01T00:00:00Z',
        updatedAt: '2026-09-01T00:00:00Z',
      }],
      total: 21,
      page: 1,
      size: 20,
    })
    const view = render(<SkillReviews skillId={10} canInteract onRequireLogin={vi.fn()} />)

    fireEvent.click(screen.getByRole('button', { name: 'Next' }))
    expect(mocks.requestedPages).toContain(1)

    mocks.pages.set(1, { items: [], total: 20, page: 1, size: 20 })
    view.rerender(<SkillReviews skillId={10} canInteract onRequireLogin={vi.fn()} />)
    fireEvent.click(screen.getByRole('button', { name: 'Previous' }))
    expect(mocks.requestedPages[mocks.requestedPages.length - 1]).toBe(0)
  })

  it('wraps long reviewer names and review text', () => {
    const reviewer = 'review_author_1788284593_353294'
    const reviewText = 'x'.repeat(200)
    mocks.pages.set(0, {
      items: [{
        id: 8,
        displayName: reviewer,
        score: 5,
        reviewText,
        status: 'VISIBLE',
        authoredByViewer: false,
        createdAt: '2026-09-01T00:00:00Z',
        updatedAt: '2026-09-01T00:00:00Z',
      }],
      total: 1,
      page: 0,
      size: 20,
    })

    render(<SkillReviews skillId={10} canInteract onRequireLogin={vi.fn()} />)

    expect(screen.getByText(reviewer).className).toContain('[overflow-wrap:anywhere]')
    expect(screen.getByText(reviewText).className).toContain('[overflow-wrap:anywhere]')
  })

  it('keeps review interaction copy in every supported locale', () => {
    for (const locale of [en, zh, ru]) {
      expect(locale.skillReviews.ratingDisplay).toBeTruthy()
      expect(locale.skillReviews.ratingOption).toBeTruthy()
      expect(locale.skillReviews.reviewTextLabel).toBeTruthy()
      expect(locale.skillReviews.hide).toBeTruthy()
      expect(locale.skillReviews.restore).toBeTruthy()
    }
    expect(en.skillReviews.count_one).toBe('{{count}} review')
    expect(en.skillReviews.count_other).toBe('{{count}} reviews')
    expect(ru.skillReviews.count_one).toBeTruthy()
    expect(ru.skillReviews.count_few).toBeTruthy()
    expect(ru.skillReviews.count_many).toBeTruthy()
    expect(ru.skillReviews.count_other).toBeTruthy()
  })

  it('lets an authenticated author clear existing text when the skill is not interactable', () => {
    render(<SkillReviews skillId={10} canInteract={false} onRequireLogin={vi.fn()} />)

    expect(mocks.mineEnabled).toContain(true)
    fireEvent.click(screen.getByRole('button', { name: 'Delete review' }))
    expect(mocks.clearMutate).toHaveBeenCalledOnce()
    expect(screen.queryByRole('button', { name: 'Edit my review' })).toBeNull()
  })
})
