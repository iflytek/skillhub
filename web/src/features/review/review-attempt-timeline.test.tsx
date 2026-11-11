import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it, vi } from 'vitest'
import type { ReviewTask } from '@/api/types'
import { ReviewAttemptTimeline } from './review-attempt-timeline'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, values?: Record<string, string | number>) => (
      values ? `${key}:${Object.values(values).join(':')}` : key
    ),
  }),
}))

describe('ReviewAttemptTimeline', () => {
  it('shows newest-first numbering plus submission and review metadata', () => {
    const attempts: ReviewTask[] = [
      {
        id: 2,
        skillVersionId: 20,
        namespace: 'team-a',
        skillSlug: 'demo',
        version: '1.0.0',
        status: 'PENDING',
        submittedBy: 'author',
        submittedAt: '2026-09-01T02:00:00Z',
      },
      {
        id: 1,
        skillVersionId: null,
        namespace: 'team-a',
        skillSlug: 'demo',
        version: '1.0.0',
        status: 'REJECTED',
        submittedBy: 'author',
        reviewedBy: 'reviewer',
        reviewedByName: 'Reviewer One',
        reviewComment: 'Add tests',
        submittedAt: '2026-09-01T00:00:00Z',
        reviewedAt: '2026-09-01T01:00:00Z',
      },
    ]

    const html = renderToStaticMarkup(
      <ReviewAttemptTimeline attempts={attempts} locale="en" />,
    )

    expect(html).toContain('reviewProgress.attemptNumber:2')
    expect(html).toContain('reviewProgress.attemptNumber:1')
    expect(html).toContain('reviewProgress.submittedAt:')
    expect(html).toContain('reviewProgress.reviewedAt:')
    expect(html).toContain('reviewProgress.reviewedBy:Reviewer One')
    expect(html).toContain('Add tests')
  })

  it('labels system automatic approval without naming the submitter as reviewer', () => {
    const attempts: ReviewTask[] = [
      {
        id: 3,
        skillVersionId: 30,
        namespace: 'team-a',
        skillSlug: 'demo',
        version: '1.1.0',
        status: 'APPROVED',
        submittedBy: 'author',
        decisionMethod: 'SYSTEM_AUTOMATIC',
        decisionScanTaskId: 'scan-1',
        submittedAt: '2026-09-02T00:00:00Z',
        reviewedAt: '2026-09-02T00:01:00Z',
      },
    ]

    const html = renderToStaticMarkup(
      <ReviewAttemptTimeline attempts={attempts} locale="en" />,
    )

    expect(html).toContain('reviewProgress.systemAutomaticDecision')
    expect(html).not.toContain('reviewProgress.reviewedBy')
    expect(html).not.toContain('reviewProgress.reviewedBy:author')
  })
})
