// @vitest-environment jsdom

import { createElement, type ReactNode } from 'react'
import { focusManager, QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, cleanup, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { SkillLifecycleVersion, SkillVersion } from '@/api/types'

const { fetchJson } = vi.hoisted(() => ({ fetchJson: vi.fn() }))
vi.mock('@/api/client', async (original) => ({
  ...await original<typeof import('@/api/client')>(),
  fetchJson,
}))

import { useSkillDetail, useSkillVersionDetail, useSkillVersions } from './use-skill-queries'

function version(status: string): SkillVersion {
  return { id: 1, version: '1.0.0', status, fileCount: 1, totalSize: 100,
    publishedAt: '', downloadAvailable: status === 'PUBLISHED' }
}

function lifecycle(status: string): SkillLifecycleVersion {
  return { id: 1, version: '1.0.0', status }
}

const clients: QueryClient[] = []
function wrapper() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: Infinity } } })
  clients.push(client)
  return function Wrapper({ children }: { children: ReactNode }) {
    return createElement(QueryClientProvider, { client }, children)
  }
}

describe('skill scan polling lifecycle', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    focusManager.setFocused(true)
    fetchJson.mockReset()
  })
  afterEach(() => {
    cleanup()
    for (const client of clients.splice(0)) client.clear()
    focusManager.setFocused(undefined)
    vi.useRealTimers()
  })

  it.each(['UPLOADED', 'PENDING_REVIEW', 'SCAN_FAILED'])('refreshes scanning versions to %s without navigation, then stops', async (terminal) => {
    fetchJson.mockResolvedValueOnce({ items: [version('SCANNING')] })
      .mockResolvedValue({ items: [version(terminal)] })
    const hook = renderHook(() => useSkillVersions('team', 'demo'), { wrapper: wrapper() })
    await vi.waitFor(() => expect(hook.result.current.data?.[0].status).toBe('SCANNING'))

    await act(() => vi.advanceTimersByTimeAsync(5_000))
    await vi.waitFor(() => expect(hook.result.current.data?.[0].status).toBe(terminal))
    expect(fetchJson).toHaveBeenLastCalledWith('/api/web/skills/team/demo/versions')
    const calls = fetchJson.mock.calls.length
    await act(() => vi.advanceTimersByTimeAsync(15_000))
    expect(fetchJson).toHaveBeenCalledTimes(calls)
  })

  it('observes a scanning owner preview even while the published headline is stable', async () => {
    fetchJson.mockResolvedValueOnce({ headlineVersion: lifecycle('PUBLISHED'), ownerPreviewVersion: lifecycle('SCANNING') })
      .mockResolvedValue({ headlineVersion: lifecycle('PUBLISHED'), ownerPreviewVersion: lifecycle('UPLOADED') })
    const hook = renderHook(() => useSkillDetail('team', 'demo'), { wrapper: wrapper() })
    await vi.waitFor(() => expect(hook.result.current.data?.ownerPreviewVersion?.status).toBe('SCANNING'))
    await act(() => vi.advanceTimersByTimeAsync(5_000))
    await vi.waitFor(() => expect(hook.result.current.data?.ownerPreviewVersion?.status).toBe('UPLOADED'))
    const calls = fetchJson.mock.calls.length
    await act(() => vi.advanceTimersByTimeAsync(15_000))
    expect(fetchJson).toHaveBeenCalledTimes(calls)
  })

  it('refreshes a scanning headline and an explicitly selected version detail', async () => {
    fetchJson.mockImplementation(async (path: string) => path.endsWith('/1.0.0')
      ? version('SCANNING') : { headlineVersion: lifecycle('SCANNING') })
    const hook = renderHook(() => ({
      detail: useSkillDetail('team', 'demo'),
      version: useSkillVersionDetail('team', 'demo', '1.0.0'),
    }), { wrapper: wrapper() })
    await vi.waitFor(() => expect(hook.result.current.version.data?.status).toBe('SCANNING'))
    fetchJson.mockImplementation(async (path: string) => path.endsWith('/1.0.0')
      ? version('UPLOADED') : { headlineVersion: lifecycle('UPLOADED') })
    await act(() => vi.advanceTimersByTimeAsync(5_000))
    await vi.waitFor(() => {
      expect(hook.result.current.detail.data?.headlineVersion?.status).toBe('UPLOADED')
      expect(hook.result.current.version.data?.status).toBe('UPLOADED')
    })
  })

  it('stops after disabling or unmounting instead of continuing private background reads', async () => {
    fetchJson.mockResolvedValue({ items: [version('SCANNING')] })
    const hook = renderHook(({ enabled }) => useSkillVersions('team', 'demo', enabled), {
      initialProps: { enabled: true }, wrapper: wrapper(),
    })
    await vi.waitFor(() => expect(hook.result.current.data).toHaveLength(1))
    hook.rerender({ enabled: false })
    const calls = fetchJson.mock.calls.length
    await act(() => vi.advanceTimersByTimeAsync(15_000))
    expect(fetchJson).toHaveBeenCalledTimes(calls)
    hook.unmount()
    await act(() => vi.advanceTimersByTimeAsync(15_000))
    expect(fetchJson).toHaveBeenCalledTimes(calls)
  })

  it('does not poll a stable published version or an empty list', async () => {
    fetchJson.mockResolvedValueOnce({ items: [version('PUBLISHED')] }).mockResolvedValue({ items: [] })
    renderHook(() => useSkillVersions('team', 'stable'), { wrapper: wrapper() })
    renderHook(() => useSkillVersions('team', 'empty'), { wrapper: wrapper() })
    await vi.waitFor(() => expect(fetchJson).toHaveBeenCalledTimes(2))
    await act(() => vi.advanceTimersByTimeAsync(15_000))
    expect(fetchJson).toHaveBeenCalledTimes(2)
  })

  it('stops polling cached scanning data after a read fails', async () => {
    fetchJson.mockResolvedValueOnce({ items: [version('SCANNING')] })
      .mockRejectedValue(new Error('No longer visible'))
    const hook = renderHook(() => {
      const query = useSkillVersions('team', 'demo')
      return { data: query.data, isError: query.isError }
    }, { wrapper: wrapper() })
    await vi.waitFor(() => expect(hook.result.current.data).toHaveLength(1))
    await act(() => vi.advanceTimersByTimeAsync(5_000))
    await vi.waitFor(() => expect(hook.result.current.isError).toBe(true))
    const calls = fetchJson.mock.calls.length
    await act(() => vi.advanceTimersByTimeAsync(15_000))
    expect(fetchJson).toHaveBeenCalledTimes(calls)
  })
})
