import { renderToStaticMarkup } from 'react-dom/server'
import { createElement, type ReactNode } from 'react'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import type { GovernanceInboxItem } from '@/api/types'

const { navigateMock, buttonProps, formatLocalDateTimeMock } = vi.hoisted(() => ({
  navigateMock: vi.fn(),
  buttonProps: [] as Array<{ onClick?: () => void }>,
  formatLocalDateTimeMock: vi.fn(() => '2026-03-23 10:00'),
}))

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => navigateMock,
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

vi.mock('@/shared/ui/button', () => ({
  Button: (props: { children?: ReactNode; onClick?: () => void }) => {
    buttonProps.push(props)
    return createElement('button', { onClick: props.onClick }, props.children)
  },
}))

import { GovernanceInbox } from './governance-inbox'

function createItem(overrides: Partial<GovernanceInboxItem> = {}): GovernanceInboxItem {
  return {
    type: 'REVIEW',
    id: 42,
    title: 'Review pending skill',
    subtitle: 'Needs approval',
    timestamp: '2026-03-23T02:00:00Z',
    namespace: 'team-a',
    skillSlug: 'demo-skill',
    ...overrides,
  }
}

describe('governance-inbox module', () => {
  beforeEach(() => {
    navigateMock.mockReset()
    buttonProps.length = 0
    formatLocalDateTimeMock.mockClear()
  })

  it('renders the loading shimmer', () => {
    const html = renderToStaticMarkup(createElement(GovernanceInbox, { isLoading: true }))

    expect(html).toContain('animate-shimmer')
  })

  it('renders the empty state', () => {
    const html = renderToStaticMarkup(createElement(GovernanceInbox, { isLoading: false, items: [] }))

    expect(html).toContain('governance.emptyInbox')
    expect(html).toContain('text-muted-foreground')
  })

  it('renders inbox items with formatted timestamps and subtitles', () => {
    const html = renderToStaticMarkup(
      createElement(GovernanceInbox, { isLoading: false, items: [createItem()] })
    )

    expect(html).toContain('Review pending skill')
    expect(html).toContain('Needs approval')
    expect(html).toContain('REVIEW')
    expect(html).toContain('2026-03-23 10:00')
    expect(formatLocalDateTimeMock).toHaveBeenCalledWith('2026-03-23T02:00:00Z', 'en')
  })

  it('navigates to the review route when the open button is activated', () => {
    renderToStaticMarkup(
      createElement(GovernanceInbox, { isLoading: false, items: [createItem({ type: 'REVIEW' })] })
    )

    expect(buttonProps).toHaveLength(1)
    buttonProps[0].onClick?.()

    expect(navigateMock).toHaveBeenCalledWith({ to: '/dashboard/reviews/42' })
  })

  it('routes promotion, report, and namespace items to the expected destinations', () => {
    renderToStaticMarkup(
      createElement(GovernanceInbox, {
        isLoading: false,
        items: [
          createItem({ type: 'PROMOTION', id: 7, title: 'Promotion ready' }),
          createItem({ type: 'REPORT', id: 8, title: 'Report ready' }),
          createItem({ type: 'OTHER', id: 9, title: 'Space item', namespace: 'team-b', skillSlug: 'skill-x' }),
        ],
      })
    )

    expect(buttonProps).toHaveLength(3)
    buttonProps[0].onClick?.()
    buttonProps[1].onClick?.()
    buttonProps[2].onClick?.()

    expect(navigateMock).toHaveBeenNthCalledWith(1, { to: '/dashboard/promotions' })
    expect(navigateMock).toHaveBeenNthCalledWith(2, { to: '/dashboard/reports' })
    expect(navigateMock).toHaveBeenNthCalledWith(3, { to: '/space/team-b/skill-x' })
  })
})
