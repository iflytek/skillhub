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

  it.each([
    ['REVIEW_SUBMITTED', '스킬 검토 제출'],
    ['REVIEW_APPROVED', '스킬 검토 승인'],
    ['REVIEW_REJECTED', '스킬 검토 반려'],
    ['PROMOTION_SUBMITTED', '스킬 승격 제출'],
    ['PROMOTION_APPROVED', '스킬 승격 승인'],
    ['PROMOTION_REJECTED', '스킬 승격 반려'],
    ['REPORT_SUBMITTED', '스킬 신고 접수'],
    ['REPORT_RESOLVED', '스킬 신고 처리 완료'],
    ['SKILL_PUBLISHED', '스킬 게시 완료'],
    ['SUBSCRIPTION_NEW_VERSION', '구독한 스킬 업데이트'],
    ['SUBSCRIPTION_VERSION_YANKED', '구독한 스킬 버전 배포 철회'],
  ])('localizes %s in Korean', (eventType, title) => {
    const display = resolveNotificationDisplay({
      id: 1, category: 'REVIEW', eventType, title: 'Server title', status: 'UNREAD',
      createdAt: '2026-03-20T00:00:00Z',
      bodyJson: JSON.stringify({ skillName: 'Calendar', version: '1.0.0' }),
    }, 'ko-KR')
    expect(display.title).toBe(title)
    expect(display.description).toContain('Calendar')
    expect(display.description).toMatch(/[가-힣]/)
    if (!eventType.startsWith('REPORT_')) expect(display.description).toContain('(1.0.0)')
  })

  it.each([undefined, 'invalid JSON', '{}', 'null'])('keeps a localized title with an absent or malformed body (%s)', (bodyJson) => {
    expect(resolveNotificationDisplay({
      id: 1, category: 'REVIEW', eventType: 'REVIEW_APPROVED', title: 'Server title',
      status: 'UNREAD', createdAt: '2026-03-20T00:00:00Z', bodyJson,
    }, 'ko')).toEqual({ title: '스킬 검토 승인', description: '' })
  })

  it('omits version parentheses when a notification has no version', () => {
    const display = resolveNotificationDisplay({
      id: 1, category: 'REVIEW', eventType: 'REVIEW_APPROVED', title: 'Server title',
      status: 'UNREAD', createdAt: '2026-03-20T00:00:00Z',
      bodyJson: JSON.stringify({ skillName: 'Calendar' }),
    }, 'ko')
    expect(display.description).toBe('Calendar의 검토가 승인되었습니다.')
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
