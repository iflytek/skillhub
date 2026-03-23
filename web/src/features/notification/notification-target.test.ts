import { describe, expect, it } from 'vitest'
import { resolveNotificationTarget } from './notification-target'

describe('resolveNotificationTarget', () => {
  it('prefers explicit targetRoute when provided', () => {
    expect(resolveNotificationTarget({
      id: 1,
      category: 'REVIEW',
      eventType: 'REVIEW_SUBMITTED',
      title: 'Review submitted',
      targetRoute: '/dashboard/reviews/12',
      status: 'UNREAD',
      createdAt: '2026-03-20T00:00:00Z',
    })).toBe('/dashboard/reviews/12')
  })

  it('ignores unsafe absolute targetRoute values', () => {
    expect(resolveNotificationTarget({
      id: 1,
      category: 'REVIEW',
      eventType: 'REVIEW_SUBMITTED',
      title: 'Review submitted',
      targetRoute: 'https://evil.example/steal',
      entityType: 'REVIEW',
      entityId: 12,
      status: 'UNREAD',
      createdAt: '2026-03-20T00:00:00Z',
    })).toBe('/dashboard/reviews/12')
  })

  it('ignores protocol-relative targetRoute values', () => {
    expect(resolveNotificationTarget({
      id: 1,
      category: 'PROMOTION',
      eventType: 'PROMOTION_SUBMITTED',
      title: 'Promotion submitted',
      targetRoute: '//evil.example/steal',
      entityType: 'PROMOTION',
      entityId: 44,
      status: 'UNREAD',
      createdAt: '2026-03-20T00:00:00Z',
    })).toBe('/dashboard/promotions')
  })

  it('falls back to legacy review detail route', () => {
    expect(resolveNotificationTarget({
      id: 1,
      category: 'REVIEW',
      eventType: 'REVIEW_SUBMITTED',
      title: 'Review submitted',
      entityType: 'REVIEW',
      entityId: 12,
      status: 'UNREAD',
      createdAt: '2026-03-20T00:00:00Z',
    })).toBe('/dashboard/reviews/12')
  })

  it('routes legacy report notifications to the reports page', () => {
    expect(resolveNotificationTarget({
      id: 1,
      category: 'REPORT',
      eventType: 'REPORT_SUBMITTED',
      title: 'Report submitted',
      entityType: 'REPORT',
      entityId: 33,
      status: 'UNREAD',
      createdAt: '2026-03-20T00:00:00Z',
    })).toBe('/dashboard/reports')
  })

  it('routes legacy promotion notifications to the promotions page', () => {
    expect(resolveNotificationTarget({
      id: 1,
      category: 'PROMOTION',
      eventType: 'PROMOTION_SUBMITTED',
      title: 'Promotion submitted',
      entityType: 'PROMOTION',
      entityId: 44,
      status: 'UNREAD',
      createdAt: '2026-03-20T00:00:00Z',
    })).toBe('/dashboard/promotions')
  })

  it('falls back to notifications page for unsupported items', () => {
    expect(resolveNotificationTarget({
      id: 1,
      category: 'PUBLISH',
      eventType: 'SKILL_PUBLISHED',
      title: 'Published',
      entityType: 'SKILL',
      entityId: 55,
      status: 'UNREAD',
      createdAt: '2026-03-20T00:00:00Z',
    })).toBe('/dashboard/notifications')
  })
})
