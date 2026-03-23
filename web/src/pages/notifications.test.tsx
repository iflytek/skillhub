import { renderToStaticMarkup } from 'react-dom/server'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const useAuthMock = vi.fn()
const useNotificationListMock = vi.fn()
const useMarkAllReadMock = vi.fn()
const useMarkReadMock = vi.fn()
const useDeleteReadNotificationMock = vi.fn()

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

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => vi.fn(),
}))

vi.mock('@/features/auth/use-auth', () => ({
  useAuth: () => useAuthMock(),
}))

vi.mock('@/features/notification/use-notifications', () => ({
  useNotificationList: (...args: unknown[]) => useNotificationListMock(...args),
  useMarkAllRead: (...args: unknown[]) => useMarkAllReadMock(...args),
  useMarkRead: (...args: unknown[]) => useMarkReadMock(...args),
  useDeleteReadNotification: (...args: unknown[]) => useDeleteReadNotificationMock(...args),
}))

vi.mock('@/features/notification/notification-content', () => ({
  resolveNotificationDisplay: (item: { title: string }) => ({ title: item.title, description: '' }),
}))

vi.mock('@/features/notification/notification-target', () => ({
  resolveNotificationTarget: () => '/dashboard/notifications',
}))

import { NotificationsPage } from './notifications'

describe('NotificationsPage', () => {
  beforeEach(() => {
    useAuthMock.mockReturnValue({ user: { userId: 'user-1' } })
    useNotificationListMock.mockReturnValue({
      data: { items: [], total: 0, page: 0, size: 20 },
      isLoading: false,
    })
    useMarkAllReadMock.mockReturnValue({ mutate: vi.fn(), isPending: false })
    useMarkReadMock.mockReturnValue({ mutate: vi.fn(), isPending: false })
    useDeleteReadNotificationMock.mockReturnValue({ mutate: vi.fn(), isPending: false })
  })

  it('shows delete action only for read notifications', () => {
    useNotificationListMock.mockReturnValue({
      data: {
        items: [
          {
            id: 1,
            category: 'REVIEW',
            eventType: 'REVIEW_APPROVED',
            title: 'Read notification',
            status: 'READ',
            createdAt: '2026-03-23T00:00:00Z',
          },
          {
            id: 2,
            category: 'REVIEW',
            eventType: 'REVIEW_SUBMITTED',
            title: 'Unread notification',
            status: 'UNREAD',
            createdAt: '2026-03-23T00:00:00Z',
          },
        ],
        total: 2,
        page: 0,
        size: 20,
      },
      isLoading: false,
    })

    const html = renderToStaticMarkup(<NotificationsPage />)

    expect(html).toContain('notification.deleteRead')
    expect(html).toContain('Read notification')
    expect(html).toContain('Unread notification')
  })
})
