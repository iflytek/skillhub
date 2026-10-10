import { describe, expect, it, vi } from 'vitest'

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => key,
    }),
  }
})

vi.mock('@/shared/ui/card', () => ({
  Card: ({ children }: { children: unknown }) => children,
}))

vi.mock('@/shared/ui/button', () => ({
  Button: ({ children }: { children: unknown }) => children,
}))

vi.mock('@/shared/ui/input', () => ({
  Input: () => null,
}))

vi.mock('@/shared/ui/label', () => ({
  Label: ({ children }: { children: unknown }) => children,
}))

vi.mock('@/api/client', () => ({
  fetchJson: vi.fn(),
  getCsrfHeaders: () => ({}),
}))

vi.mock('@/shared/lib/error-display', () => ({
  truncateErrorMessage: (m: string) => m,
}))

import { DeviceAuthPage } from './device'

describe('DeviceAuthPage', () => {
  it('exports a named component function', () => {
    expect(typeof DeviceAuthPage).toBe('function')
    expect(DeviceAuthPage.name).toBe('DeviceAuthPage')
  })
})
