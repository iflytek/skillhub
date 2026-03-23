import { renderToStaticMarkup } from 'react-dom/server'
import { createElement, type ReactNode } from 'react'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import type { GovernanceNotification } from '@/api/types'

const { buttonProps, formatLocalDateTimeMock } = vi.hoisted(() => ({
  buttonProps: [] as Array<{ disabled?: boolean; onClick?: () => void }>,
  formatLocalDateTimeMock: vi.fn(() => '2026-03-23 11:00'),
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
  Button: (props: { children?: ReactNode; disabled?: boolean; onClick?: () => void }) => {
    buttonProps.push(props)
    return createElement('button', { disabled: props.disabled, onClick: props.onClick }, props.children)
  },
}))

import { GovernanceNotifications } from './governance-notifications'

function createItem(overrides: Partial<GovernanceNotification> = {}): GovernanceNotification {
  return {
    category: 'SYSTEM',
    entityType: 'SKILL',
    entityId: 99,
    title: 'Governance notification',
    status: 'UNREAD',
    id: 12,
    createdAt: '2026-03-23T03:00:00Z',
    bodyJson: '{"message":"Needs attention"}',
    ...overrides,
  }
}

describe('governance-notifications module', () => {
  beforeEach(() => {
    buttonProps.length = 0
    formatLocalDateTimeMock.mockClear()
  })

  it('renders the loading shimmer', () => {
    const html = renderToStaticMarkup(
      createElement(GovernanceNotifications, {
        isLoading: true,
        items: undefined,
        onMarkRead: vi.fn(),
        isMarkingRead: false,
      })
    )

    expect(html).toContain('animate-shimmer')
  })

  it('renders the empty state', () => {
    const html = renderToStaticMarkup(
      createElement(GovernanceNotifications, {
        isLoading: false,
        items: [],
        onMarkRead: vi.fn(),
        isMarkingRead: false,
      })
    )

    expect(html).toContain('governance.emptyNotifications')
  })

  it('renders unread notifications with metadata and a mark-read action', () => {
    const html = renderToStaticMarkup(
      createElement(GovernanceNotifications, {
        isLoading: false,
        items: [createItem()],
        onMarkRead: vi.fn(),
        isMarkingRead: false,
      })
    )

    expect(html).toContain('Governance notification')
    expect(html).toContain('UNREAD')
    expect(html).toContain('Needs attention')
    expect(html).toContain('2026-03-23 11:00')
    expect(formatLocalDateTimeMock).toHaveBeenCalledWith('2026-03-23T03:00:00Z', 'en')
    expect(buttonProps).toHaveLength(1)
  })

  it('disables the mark-read button while marking and invokes the callback with the notification id', () => {
    const onMarkRead = vi.fn()

    renderToStaticMarkup(
      createElement(GovernanceNotifications, {
        isLoading: false,
        items: [createItem()],
        onMarkRead,
        isMarkingRead: true,
      })
    )

    expect(buttonProps).toHaveLength(1)
    expect(buttonProps[0].disabled).toBe(true)
    buttonProps[0].onClick?.()

    expect(onMarkRead).toHaveBeenCalledWith(12)
  })

  it('omits the mark-read action for read notifications or when the id is missing', () => {
    const html = renderToStaticMarkup(
      createElement(GovernanceNotifications, {
        isLoading: false,
        items: [createItem({ status: 'READ', id: undefined }), createItem({ status: 'READ', id: 13, entityId: 13 })],
        onMarkRead: vi.fn(),
        isMarkingRead: false,
      })
    )

    expect(html).toContain('READ')
    expect(buttonProps).toHaveLength(0)
  })
})
