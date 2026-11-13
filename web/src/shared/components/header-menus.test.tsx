/** @vitest-environment jsdom */
import type { ReactNode } from 'react'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import { UserMenu } from './user-menu'
import { NotificationBell } from '@/features/notification/notification-bell'

vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }))
vi.mock('@/api/client', () => ({ authApi: { logout: vi.fn() } }))
vi.mock('@tanstack/react-query', () => ({ useQueryClient: () => ({}) }))
vi.mock('@tanstack/react-router', () => ({ Link: ({ children, to, ...props }: { children: ReactNode; to: string }) => <a href={to} {...props}>{children}</a> }))
vi.mock('@/features/auth/use-auth', () => ({ useAuth: () => ({ user: { userId: 'local-admin' } }) }))
vi.mock('@/features/notification/use-notifications', () => ({ useUnreadCount: () => ({ data: { count: 0 } }) }))
vi.mock('@/features/notification/notification-dropdown', () => ({ NotificationDropdown: () => <div>Notification list</div> }))

afterEach(cleanup)

function setup() {
  render(<><UserMenu user={{ displayName: 'Local admin', platformRoles: ['SUPER_ADMIN'] }} /><NotificationBell /><button>Outside</button></>)
}

it('closes the account menu on Escape and returns focus to its trigger', () => {
  setup()
  const account = screen.getByRole('button', { name: 'Local admin' })
  fireEvent.click(account)
  screen.getByRole('menu')
  fireEvent.keyDown(document, { key: 'Escape' })
  expect(screen.queryByRole('menu')).toBeNull()
  expect(document.activeElement).toBe(account)
})

it('opens one clicked menu at a time and closes the notification menu on Escape', () => {
  setup()
  const account = screen.getByRole('button', { name: 'Local admin' })
  const bell = screen.getByRole('button', { name: 'notification.title' })
  fireEvent.click(account)
  fireEvent.mouseDown(bell)
  fireEvent.click(bell)
  expect(screen.queryByRole('menu')).toBeNull()
  screen.getByText('Notification list')
  expect(bell.getAttribute('aria-expanded')).toBe('true')
  fireEvent.keyDown(document, { key: 'Escape' })
  expect(screen.queryByText('Notification list')).toBeNull()
  expect(document.activeElement).toBe(bell)
  fireEvent.click(bell)
  fireEvent.mouseDown(screen.getByRole('button', { name: 'Outside' }))
  expect(screen.queryByText('Notification list')).toBeNull()
})
