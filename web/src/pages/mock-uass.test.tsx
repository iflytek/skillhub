import type { ReactNode } from 'react'
import { describe, expect, it, vi } from 'vitest'

vi.mock('@tanstack/react-router', () => ({
  useSearch: () => ({
    state: 'state-1',
    callbackUrl: 'http://localhost:3000/api/v1/auth/uass/callback',
  }),
}))

vi.mock('@/api/client', () => ({
  fetchJson: vi.fn(),
}))

vi.mock('@/shared/ui/button', () => ({
  Button: ({ children }: { children: ReactNode }) => <button type="button">{children}</button>,
}))

vi.mock('@/shared/ui/input', () => ({
  Input: () => <input />,
}))

import { renderToStaticMarkup } from 'react-dom/server'
import { MockUassPage } from './mock-uass'

describe('MockUassPage', () => {
  it('renders the mock third-party login form', () => {
    const html = renderToStaticMarkup(<MockUassPage />)

    expect(html).toContain('模拟第三方登录页')
    expect(html).toContain('ussId')
    expect(html).toContain('用户名')
    expect(html).toContain('手机号码')
    expect(html).toContain('邮箱')
  })
})
