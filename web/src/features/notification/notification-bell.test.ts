import { describe, expect, it } from 'vitest'
import { resolveNotificationUserId } from './notification-bell'

describe('resolveNotificationUserId', () => {
  it('returns the current authenticated user id for notification-scoped queries', () => {
    expect(resolveNotificationUserId({ userId: 'user-b' })).toBe('user-b')
  })

  it('returns undefined when there is no authenticated user', () => {
    expect(resolveNotificationUserId(null)).toBeUndefined()
    expect(resolveNotificationUserId(undefined)).toBeUndefined()
  })
})
