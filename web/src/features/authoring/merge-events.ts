import type { ValidationEventInfo } from '@/api/types'

/**
 * Merges incoming run events into an existing, seq-ordered list.
 *
 * Events can arrive out of order (SSE replay vs. live push vs. polling
 * fallback), so every event is placed by its per-run sequence number and
 * duplicates are ignored. Returns the previous array reference when nothing
 * changed so React re-renders stay cheap.
 */
export function mergeEvents(
  existing: ValidationEventInfo[],
  incoming: ValidationEventInfo[],
): ValidationEventInfo[] {
  if (incoming.length === 0) {
    return existing
  }
  const bySeq = new Map<number, ValidationEventInfo>()
  for (const event of existing) {
    bySeq.set(event.seq, event)
  }
  let changed = false
  for (const event of incoming) {
    if (!bySeq.has(event.seq)) {
      changed = true
    }
    bySeq.set(event.seq, event)
  }
  if (!changed) {
    return existing
  }
  return [...bySeq.values()].sort((a, b) => a.seq - b.seq)
}

/**
 * Highest sequence number seen so far — the cursor for polling catch-up.
 */
export function lastSeqOf(events: ValidationEventInfo[]): number {
  return events.reduce((max, event) => Math.max(max, event.seq), 0)
}
