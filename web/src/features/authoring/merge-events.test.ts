import { describe, expect, it } from 'vitest'
import type { ValidationEventInfo } from '@/api/types'
import { lastSeqOf, mergeEvents } from './merge-events'

function event(seq: number, type = 'LOG'): ValidationEventInfo {
  return { seq, type, phase: 'BEHAVIOR', payload: { line: `event-${seq}` }, createdAt: '2026-09-18T10:00:00Z' }
}

describe('mergeEvents', () => {
  it('appends incoming events in seq order', () => {
    const merged = mergeEvents([event(1), event(2)], [event(3)])
    expect(merged.map((item) => item.seq)).toEqual([1, 2, 3])
  })

  it('places out-of-order incoming events by seq', () => {
    const merged = mergeEvents([event(1), event(3)], [event(2)])
    expect(merged.map((item) => item.seq)).toEqual([1, 2, 3])
  })

  it('ignores duplicates', () => {
    const merged = mergeEvents([event(1), event(2)], [event(2), event(3), event(1)])
    expect(merged.map((item) => item.seq)).toEqual([1, 2, 3])
  })

  it('keeps the previous reference when nothing is new', () => {
    const existing = [event(1), event(2)]
    expect(mergeEvents(existing, [event(1)])).toBe(existing)
    expect(mergeEvents(existing, [])).toBe(existing)
  })

  it('accepts events older than the current cursor without losing new ones', () => {
    const merged = mergeEvents([event(5)], [event(2), event(6)])
    expect(merged.map((item) => item.seq)).toEqual([2, 5, 6])
  })
})

describe('lastSeqOf', () => {
  it('returns the highest seq', () => {
    expect(lastSeqOf([event(3), event(9), event(4)])).toBe(9)
  })

  it('returns zero for an empty list', () => {
    expect(lastSeqOf([])).toBe(0)
  })
})
