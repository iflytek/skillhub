import { renderToStaticMarkup } from 'react-dom/server'
import { createElement } from 'react'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import type { GovernanceActivityItem } from '@/api/types'

const { formatLocalDateTimeMock } = vi.hoisted(() => ({
  formatLocalDateTimeMock: vi.fn(() => '2026-03-23 12:00'),
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

vi.mock('@/shared/lib/date-time', () => ({
  formatLocalDateTime: formatLocalDateTimeMock,
}))

import { GovernanceActivity } from './governance-activity'

function createItem(overrides: Partial<GovernanceActivityItem> = {}): GovernanceActivityItem {
  return {
    id: 1,
    action: 'Skill approved',
    actorUserId: 'user-1',
    actorDisplayName: 'Alice',
    targetType: 'SKILL',
    targetId: 'skill-1',
    details: 'Approved by governance team',
    timestamp: '2026-03-23T04:00:00Z',
    ...overrides,
  }
}

describe('governance-activity module', () => {
  beforeEach(() => {
    formatLocalDateTimeMock.mockClear()
  })

  it('renders the loading shimmer', () => {
    const html = renderToStaticMarkup(createElement(GovernanceActivity, { isLoading: true }))

    expect(html).toContain('animate-shimmer')
  })

  it('renders the empty state', () => {
    const html = renderToStaticMarkup(createElement(GovernanceActivity, { isLoading: false, items: [] }))

    expect(html).toContain('governance.emptyActivity')
  })

  it('renders the activity item with formatted time, actor display name, and details', () => {
    const html = renderToStaticMarkup(
      createElement(GovernanceActivity, { isLoading: false, items: [createItem()] })
    )

    expect(html).toContain('Skill approved')
    expect(html).toContain('Alice')
    expect(html).toContain('Approved by governance team')
    expect(html).toContain('2026-03-23 12:00')
    expect(formatLocalDateTimeMock).toHaveBeenCalledWith('2026-03-23T04:00:00Z', 'en')
  })

  it('falls back to actor user id and unknown actor text when needed', () => {
    const html = renderToStaticMarkup(
      createElement(GovernanceActivity, {
        isLoading: false,
        items: [
          createItem({ id: 2, actorDisplayName: undefined, actorUserId: 'user-2', details: undefined }),
          createItem({ id: 3, actorDisplayName: undefined, actorUserId: undefined, details: undefined }),
        ],
      })
    )

    expect(html).toContain('user-2')
    expect(html).toContain('governance.unknownActor')
  })

  it('omits the details block when details are missing', () => {
    const html = renderToStaticMarkup(
      createElement(GovernanceActivity, { isLoading: false, items: [createItem({ details: undefined })] })
    )

    expect(html).not.toContain('Approved by governance team')
  })
})
