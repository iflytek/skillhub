import { describe, expect, it, vi } from 'vitest'
import { formatLocalDateTime } from './date-time'

describe('formatLocalDateTime', () => {
  it('returns an em dash for empty values', () => {
    expect(formatLocalDateTime(undefined, 'en-US')).toBe('—')
    expect(formatLocalDateTime(null, 'en-US')).toBe('—')
    expect(formatLocalDateTime('', 'en-US')).toBe('—')
  })

  it('passes timezone-qualified timestamps through to Date parsing', () => {
    const spy = vi.spyOn(Intl, 'DateTimeFormat').mockImplementation(() => ({
      format: () => 'formatted-zoned',
    } as Intl.DateTimeFormat))

    expect(formatLocalDateTime('2026-03-16T10:20:30Z', 'en-US')).toBe('formatted-zoned')

    const [, options] = spy.mock.calls[0]
    expect(options).toEqual({ dateStyle: 'medium', timeStyle: 'short' })
    spy.mockRestore()
  })

  it('parses server local timestamps without forcing UTC conversion', () => {
    const spy = vi.spyOn(Intl, 'DateTimeFormat').mockImplementation(() => ({
      format: (value: Date | number) => {
        const date = value instanceof Date ? value : new Date(value)
        return JSON.stringify({
          year: date.getFullYear(),
          month: date.getMonth(),
          day: date.getDate(),
          hours: date.getHours(),
          minutes: date.getMinutes(),
          seconds: date.getSeconds(),
          milliseconds: date.getMilliseconds(),
        })
      },
    } as Intl.DateTimeFormat))

    const formatted = formatLocalDateTime('2026-03-16T10:20:30.456', 'en-US')
    expect(JSON.parse(formatted)).toEqual({
      year: 2026,
      month: 2,
      day: 16,
      hours: 10,
      minutes: 20,
      seconds: 30,
      milliseconds: 456,
    })

    spy.mockRestore()
  })
})
