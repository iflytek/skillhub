import { useEffect, useRef, useState } from 'react'
import { authoringApi } from '@/api/client'
import type { ValidationEventInfo, ValidationRunInfo } from '@/api/types'
import { lastSeqOf, mergeEvents } from './merge-events'

const POLL_INTERVAL_MS = 2_000

export interface ValidationStream {
  events: ValidationEventInfo[]
  /** False while the SSE connection is down and the console is degraded to polling. */
  live: boolean
}

/**
 * Live event feed for one validation run.
 *
 * Prefers SSE (the server pushes each persisted event with its seq as the SSE
 * id, so reconnects resume from Last-Event-ID without gaps). Falls back to
 * `afterSeq` polling whenever SSE cannot connect, and stops once the run
 * reaches a terminal state. Initial history is backfilled through the REST
 * endpoint so the console is complete even when the browser attaches mid-run.
 */
export function useValidationStream(run: ValidationRunInfo | undefined): ValidationStream {
  const [events, setEvents] = useState<ValidationEventInfo[]>([])
  const [live, setLive] = useState(false)
  const eventsRef = useRef<ValidationEventInfo[]>([])
  const liveRef = useRef(false)
  const runId = run?.id
  const terminal = run?.terminal ?? false

  useEffect(() => {
    eventsRef.current = events
  }, [events])

  useEffect(() => {
    if (runId == null) {
      return
    }
    let cancelled = false
    let source: EventSource | null = null
    let pollTimer: ReturnType<typeof setInterval> | null = null

    const startPolling = () => {
      if (pollTimer || cancelled) {
        return
      }
      pollTimer = setInterval(async () => {
        try {
          const after = lastSeqOf(eventsRef.current)
          const batch = await authoringApi.listEvents(runId, after)
          if (cancelled) {
            return
          }
          setEvents((current) => mergeEvents(current, batch))
        } catch {
          // transient network problems are retried by the next tick
        }
      }, POLL_INTERVAL_MS)
    }

    const connect = () => {
      if (cancelled) {
        return
      }
      source = new EventSource(authoringApi.validationStreamUrl(runId))
      source.onopen = () => {
        if (cancelled) {
          return
        }
        if (pollTimer) {
          clearInterval(pollTimer)
          pollTimer = null
        }
        liveRef.current = true
        setLive(true)
      }
      source.onmessage = (message: MessageEvent<string>) => {
        try {
          const parsed = JSON.parse(message.data) as ValidationEventInfo
          setEvents((current) => mergeEvents(current, [parsed]))
          if (parsed.type === 'RUN_FINISHED') {
            source?.close()
            source = null
          }
        } catch {
          // malformed frames are skipped; the REST backfill keeps the log complete
        }
      }
      source.onerror = () => {
        // EventSource retries automatically on its own; poll in parallel until it recovers.
        startPolling()
      }
    }

    // Backfill history first so live events append onto a complete list. This
    // also covers attaching to an already-finished run: the log is complete,
    // there is just nothing left to stream.
    authoringApi
      .listEvents(runId)
      .then((batch) => {
        if (cancelled) {
          return
        }
        setEvents((current) => mergeEvents(current, batch))
        if (!terminal) {
          connect()
        }
      })
      .catch(() => {
        if (!cancelled && !terminal) {
          startPolling()
        }
      })

    return () => {
      cancelled = true
      source?.close()
      if (pollTimer) {
        clearInterval(pollTimer)
      }
    }
    // The run id is the stable identity of the stream; a new run gets a new
    // subscription. Terminal runs re-run the backfill once and skip the
    // live stream.
  }, [runId, terminal])

  return { events, live }
}
