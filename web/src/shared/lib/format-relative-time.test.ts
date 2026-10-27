import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import i18n from '@/i18n/config'
import { formatRelativeTime } from './format-relative-time'

const now = new Date('2026-06-15T12:00:00Z')
const ago = (minutes: number) => new Date(now.getTime() - minutes * 60_000).toISOString()

describe('formatRelativeTime', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(now)
  })
  afterEach(() => vi.useRealTimers())

  it.each([
    [0, '방금'], [0.5, '방금'], [1, '1분 전'], [59, '59분 전'],
    [60, '1시간 전'], [1439, '23시간 전'], [1440, '1일 전'], [29 * 1440, '29일 전'],
  ])('formats %s minutes ago in Korean', (minutes, expected) => {
    expect(formatRelativeTime(ago(minutes), 'ko-KR')).toBe(expected)
  })

  it.each([['en', '5m'], ['zh-CN', '5分钟'], ['ru-RU', '5 мин']])('preserves compact %s output', (locale, expected) => {
    expect(formatRelativeTime(ago(5), locale)).toBe(expected)
  })

  it('uses the selected locale for older dates', () => {
    const date = ago(30 * 1440)
    expect(formatRelativeTime(date, 'ko')).toBe(new Date(date).toLocaleDateString('ko'))
  })

  it.each([0, 5, 60, 1440, 30 * 1440])('does not append a second suffix in Korean notifications (%s minutes)', (minutes) => {
    const time = formatRelativeTime(ago(minutes), 'ko')
    expect(i18n.getFixedT('ko')('notification.timeAgo', { time })).toBe(time)
  })
})
