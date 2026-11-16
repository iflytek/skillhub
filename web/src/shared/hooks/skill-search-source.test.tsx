// @vitest-environment jsdom
import { createElement, type ReactNode } from 'react'
import { act, cleanup, renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, expect, it, vi } from 'vitest'
import type { SkillSource } from '@/api/types'
import { useSearchSkills } from './use-skill-queries'

const fetchJson = vi.hoisted(() => vi.fn())
vi.mock('@/api/client', () => ({ WEB_API_PREFIX: '/api/web', fetchJson }))
afterEach(cleanup)

it('does not paint the previous source while the next source is loading or fails', async () => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = ({ children }: { children: ReactNode }) => createElement(QueryClientProvider, { client }, children)
  let rejectGithub!: (error: Error) => void
  fetchJson.mockImplementation((url: string) => url.includes('source=github')
    ? new Promise((_resolve, reject) => { rejectGithub = reject })
    : Promise.resolve({ items: [{ id: 1, source: 'platform' }], total: 1, page: 0, size: 12 }))
  const hook = renderHook(({ source }: { source: SkillSource }) => {
    const result = useSearchSkills({ source, size: 12 })
    return { data: result.data, isError: result.isError, status: result.status, fetchStatus: result.fetchStatus }
  }, {
    wrapper, initialProps: { source: 'platform' as SkillSource },
  })
  try {
    await waitFor(() => expect(hook.result.current.data?.items[0].source).toBe('platform'))
    hook.rerender({ source: 'github' })
    expect(hook.result.current.data).toBeUndefined()
    await act(async () => rejectGithub(new Error('Offline')))
    await waitFor(() => expect(hook.result.current.isError).toBe(true))
    expect(hook.result.current.data).toBeUndefined()
    hook.rerender({ source: 'platform' })
    await waitFor(() => expect(hook.result.current.data?.items[0].source).toBe('platform'))
  } finally {
    hook.unmount()
    client.clear()
  }
})
