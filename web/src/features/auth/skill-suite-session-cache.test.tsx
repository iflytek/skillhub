// @vitest-environment jsdom

import { createElement, type ReactNode } from 'react'
import { act, cleanup, renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useSearchSkills } from '@/shared/hooks/use-skill-queries'
import { useMySubscriptionsPage } from '@/shared/hooks/use-user-queries'
import { usePasswordLogin } from './use-password-login'

const state = vi.hoisted(() => ({
  viewer: 'guest',
  nextViewer: 'member',
  direct: false,
  search: vi.fn(),
  subscriptions: vi.fn(),
  localLogin: vi.fn(),
  directLogin: vi.fn(),
}))

vi.mock('@/api/client', () => ({
  WEB_API_PREFIX: '/api/web',
  fetchJson: state.search,
  meApi: { getSubscriptionsPage: state.subscriptions },
  authApi: { localLogin: state.localLogin, directLogin: state.directLogin },
  getDirectAuthRuntimeConfig: () => ({ enabled: state.direct, provider: state.direct ? 'local' : '' }),
}))

beforeEach(() => {
  vi.clearAllMocks()
  state.direct = false
  state.search.mockImplementation(async () => ({ items: [{ hasVisibleSuites: state.viewer === 'member' }] }))
  state.subscriptions.mockImplementation(async () => ({ items: [{ hasVisibleSuites: state.viewer === 'member' }] }))
  const login = async () => {
    state.viewer = state.nextViewer
    return { userId: state.viewer }
  }
  state.localLogin.mockImplementation(login)
  state.directLogin.mockImplementation(login)
})

afterEach(cleanup)

describe('Suite membership summaries across successful authentication', () => {
  it.each([
    { from: 'guest', to: 'member', direct: false, expected: true },
    { from: 'member', to: 'other-user', direct: true, expected: false },
  ])('reloads the same mounted lists after $from logs in as $to', async ({ from, to, direct, expected }) => {
    state.viewer = from
    state.nextViewer = to
    state.direct = direct
    const client = new QueryClient({ defaultOptions: { queries: { staleTime: 30_000, retry: false } } })
    const wrapper = ({ children }: { children: ReactNode }) => createElement(QueryClientProvider, { client }, children)
    const hook = renderHook(() => ({
      search: useSearchSkills({ q: 'workflow' }),
      subscriptions: useMySubscriptionsPage({ page: 0 }),
      login: usePasswordLogin(),
    }), { wrapper })
    try {
      await waitFor(() => {
        expect(hook.result.current.search.data?.items[0].hasVisibleSuites).toBe(from === 'member')
        expect(hook.result.current.subscriptions.data?.items[0].hasVisibleSuites).toBe(from === 'member')
      })
      expect(state.search).toHaveBeenCalledTimes(1)
      expect(state.subscriptions).toHaveBeenCalledTimes(1)

      await act(async () => {
        await hook.result.current.login.mutateAsync({ username: to, password: 'test-password' })
      })
      await waitFor(() => {
        expect(hook.result.current.search.data?.items[0].hasVisibleSuites).toBe(expected)
        expect(hook.result.current.subscriptions.data?.items[0].hasVisibleSuites).toBe(expected)
      })
      expect(state.search).toHaveBeenCalledTimes(2)
      expect(state.subscriptions).toHaveBeenCalledTimes(2)
      expect(direct ? state.directLogin : state.localLogin).toHaveBeenCalledTimes(1)
      expect(client.getQueryData(['auth', 'me'])).toEqual({ userId: to })
    } finally {
      hook.unmount()
      client.clear()
    }
  })
})
