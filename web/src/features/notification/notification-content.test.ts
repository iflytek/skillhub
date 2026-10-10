import { describe, expect, it } from 'vitest'
import { resolveNotificationDisplay } from './notification-content'

describe('resolveNotificationDisplay', () => {
  it('renders review submitted content in Chinese', () => {
    const display = resolveNotificationDisplay({
      category: 'REVIEW',
      eventType: 'REVIEW_SUBMITTED',
      title: 'New review submitted for: Calendar',
      bodyJson: JSON.stringify({ skillName: 'Calendar', version: '1.0.0' }),
      status: 'UNREAD',
      createdAt: '2026-03-20T00:00:00Z',
      id: 1,
    }, 'zh-CN')

    expect(display.title).toBe('技能审核提交')
    expect(display.description).toContain('Calendar')
    expect(display.description).toContain('1.0.0')
  })

  it('renders review submitted content in English', () => {
    const display = resolveNotificationDisplay({
      category: 'REVIEW',
      eventType: 'REVIEW_SUBMITTED',
      title: 'New review submitted for: Calendar',
      bodyJson: JSON.stringify({ skillName: 'Calendar', version: '1.0.0' }),
      status: 'UNREAD',
      createdAt: '2026-03-20T00:00:00Z',
      id: 1,
    }, 'en')

    expect(display.title).toBe('Review submitted')
    expect(display.description).toContain('Calendar')
    expect(display.description).toContain('1.0.0')
  })

  it('falls back to the backend title when the event type is unsupported', () => {
    const display = resolveNotificationDisplay({
      category: 'PUBLISH',
      eventType: 'CUSTOM_EVENT',
      title: 'Backend supplied title',
      status: 'UNREAD',
      createdAt: '2026-03-20T00:00:00Z',
      id: 2,
    }, 'en')

    expect(display.title).toBe('Backend supplied title')
    expect(display.description).toBe('')
  })
})
