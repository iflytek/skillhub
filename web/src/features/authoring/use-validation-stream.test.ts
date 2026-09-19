// @vitest-environment jsdom

import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ValidationEventInfo, ValidationRunInfo } from '@/api/types'

const { listEvents, validationStreamUrl } = vi.hoisted(() => ({
  listEvents: vi.fn(),
  validationStreamUrl: vi.fn(() => '/api/web/authoring/runs/1/events/stream'),
}))

vi.mock('@/api/client', () => ({
  authoringApi: { listEvents, validationStreamUrl },
}))

import { useValidationStream } from './use-validation-stream'

interface FakeEventSource {
  onopen: (() => void) | null
  onmessage: ((event: { data: string }) => void) | null
  onerror: (() => void) | null
  close: ReturnType<typeof vi.fn>
}

let instances: FakeEventSource[] = []

class MockEventSource {
  onopen: (() => void) | null = null
  onmessage: ((event: { data: string }) => void) | null = null
  onerror: (() => void) | null = null
  close = vi.fn()

  constructor() {
    instances.push(this)
  }
}

function run(): ValidationRunInfo {
  return {
    id: 1,
    draftId: 10,
    draftRevision: 3,
    status: 'RUNNING',
    cancelRequested: false,
    active: true,
    terminal: false,
    errorCount: 0,
    warningCount: 0,
    triggeredBy: 'tester',
    createdAt: '2026-09-18T10:00:00Z',
    summary: {},
  }
}

function event(seq: number, type: string): ValidationEventInfo {
  return { seq, type, phase: 'STRUCTURE', payload: {}, createdAt: '2026-09-18T10:00:01Z' }
}

describe('useValidationStream', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    instances = []
    vi.stubGlobal('EventSource', MockEventSource)
    listEvents.mockReset()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.useRealTimers()
  })

  it('backfills history and appends live SSE events by seq', async () => {
    listEvents.mockResolvedValue([event(1, 'RUN_STARTED')])
    const { result } = renderHook(() => useValidationStream(run()))

    await act(async () => {
      await vi.advanceTimersByTimeAsync(0)
    })

    const source = instances[0]
    expect(source).toBeDefined()

    // Live event arrives before the backfill is visible — merge keeps seq order.
    act(() => {
      source.onmessage?.({ data: JSON.stringify(event(3, 'TOOL_CALL')) })
      source.onmessage?.({ data: JSON.stringify(event(2, 'LOG')) })
      source.onopen?.()
    })

    expect(result.current.events.map((item) => item.seq)).toEqual([1, 2, 3])
    expect(result.current.live).toBe(true)
  })

  it('closes the stream after the terminal event', async () => {
    listEvents.mockResolvedValue([])
    renderHook(() => useValidationStream(run()))

    await act(async () => {
      await vi.advanceTimersByTimeAsync(0)
    })
    const source = instances[0]

    act(() => {
      source.onmessage?.({ data: JSON.stringify(event(1, 'RUN_FINISHED')) })
    })

    expect(source.close).toHaveBeenCalled()
  })

  it('falls back to afterSeq polling while SSE is down', async () => {
    listEvents.mockResolvedValueOnce([]).mockResolvedValue([event(5, 'LOG')])
    const { result } = renderHook(() => useValidationStream(run()))

    await act(async () => {
      await vi.advanceTimersByTimeAsync(0)
    })
    const source = instances[0]

    act(() => {
      source.onerror?.()
    })
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2_000)
    })

    // Polling continues from the highest seq seen so far.
    expect(listEvents).toHaveBeenLastCalledWith(1, 0)
    expect(result.current.events.map((item) => item.seq)).toEqual([5])
    expect(result.current.live).toBe(false)
  })

  it('backfills the full history for a terminal run without streaming', async () => {
    // attaching to an already-finished run (e.g. opening a past run's detail
    // page) must still show its complete log — there is just nothing to stream
    const terminalRun = { ...run(), status: 'SUCCEEDED' as const, active: false, terminal: true }
    listEvents.mockResolvedValue([event(1, 'RUN_STARTED'), event(2, 'RUN_FINISHED')])
    const { result } = renderHook(() => useValidationStream(terminalRun))

    await act(async () => {
      await vi.advanceTimersByTimeAsync(0)
    })

    expect(listEvents).toHaveBeenCalledTimes(1)
    expect(listEvents).toHaveBeenLastCalledWith(1)
    expect(instances).toHaveLength(0)
    expect(result.current.events.map((item) => item.seq)).toEqual([1, 2])
    expect(result.current.live).toBe(false)
  })
})
