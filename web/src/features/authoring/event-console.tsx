import { useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import type { ValidationEventInfo } from '@/api/types'

const TYPE_CLASS: Record<string, string> = {
  RUN_STARTED: 'text-blue-600 dark:text-blue-400 font-medium',
  PHASE_STARTED: 'text-blue-600 dark:text-blue-400 font-medium',
  PHASE_FINISHED: 'text-blue-600 dark:text-blue-400',
  TOOL_CALL: 'text-purple-600 dark:text-purple-400',
  LOG: 'text-muted-foreground',
  FINDING: 'text-amber-600 dark:text-amber-400',
  RUN_FINISHED: 'text-green-600 dark:text-green-400 font-medium',
}

/**
 * Human-readable line for one event. Tool calls and findings render their
 * payload summary; LOG events render their stream line.
 */
export function eventLine(event: ValidationEventInfo): string {
  const payload = event.payload ?? {}
  if (event.type === 'TOOL_CALL') {
    const tool = typeof payload.tool === 'string' ? payload.tool : ''
    const detail = typeof payload.detail === 'string' ? payload.detail : ''
    return [tool, detail].filter(Boolean).join(' — ')
  }
  for (const key of ['line', 'message', 'summary']) {
    const value = payload[key]
    if (typeof value === 'string' && value.length > 0) {
      return value
    }
  }
  if (event.type === 'RUN_FINISHED') {
    const status = typeof payload.status === 'string' ? payload.status : ''
    return `status=${status}`
  }
  return ''
}

/** Console-like live log of validation events; auto-scrolls to the newest entry. */
export function EventConsole({ events, live }: { events: ValidationEventInfo[], live: boolean }) {
  const { t } = useTranslation()
  const bottomRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ block: 'end' })
  }, [events.length])

  return (
    <div className="rounded-md border bg-black/95 p-3 font-mono text-xs leading-5 text-zinc-100 dark:bg-zinc-950">
      <div className="mb-2 flex items-center justify-between text-[11px] uppercase tracking-wide text-zinc-400">
        <span>{t('authoring.run.eventConsole')}</span>
        <span data-testid="stream-mode">
          {live ? t('authoring.run.streaming') : t('authoring.run.polling')}
        </span>
      </div>
      <div className="max-h-80 space-y-0.5 overflow-y-auto">
        {events.length === 0 && <div className="text-zinc-500">{t('authoring.run.noEvents')}</div>}
        {events.map((event) => (
          <div key={event.seq} className="flex gap-2">
            <span className="w-8 shrink-0 text-right text-zinc-500">{event.seq}</span>
            <span className="w-28 shrink-0 text-zinc-500">{event.phase || ''}</span>
            <span className={`shrink-0 w-24 ${TYPE_CLASS[event.type] ?? 'text-zinc-300'}`}>
              {event.type}
            </span>
            <span className="break-all whitespace-pre-wrap">{eventLine(event)}</span>
          </div>
        ))}
        <div ref={bottomRef} />
      </div>
    </div>
  )
}
