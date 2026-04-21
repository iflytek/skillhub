import { describe, expect, it } from 'vitest'
import {
  buildGlobalReviewsPath,
  buildNamespaceReviewDetailPath,
  buildNamespaceReviewsPath,
  canAccessGlobalReviewCenter,
  canAccessReviewCenter,
  canManageNamespaceReviews,
  getPreferredNamespaceReviewEntry,
} from './review-paths'

describe('review-paths', () => {
  it('builds the global reviews path', () => {
    expect(buildGlobalReviewsPath()).toBe('/dashboard/reviews')
  })

  it('builds namespace review paths', () => {
    expect(buildNamespaceReviewsPath('team alpha')).toBe('/dashboard/namespaces/team%20alpha/reviews')
    expect(buildNamespaceReviewDetailPath('team alpha', 12)).toBe('/dashboard/namespaces/team%20alpha/reviews/12')
  })

  it('detects global review access from platform roles', () => {
    expect(canAccessGlobalReviewCenter(['SKILL_ADMIN'])).toBe(true)
    expect(canAccessGlobalReviewCenter(['USER_ADMIN'])).toBe(true)
    expect(canAccessGlobalReviewCenter(['SUPER_ADMIN'])).toBe(true)
    expect(canAccessGlobalReviewCenter(['USER'])).toBe(false)
  })

  it('recognizes namespace review managers', () => {
    expect(canManageNamespaceReviews('OWNER')).toBe(true)
    expect(canManageNamespaceReviews('ADMIN')).toBe(true)
    expect(canManageNamespaceReviews('MEMBER')).toBe(false)
  })

  it('prefers active team namespaces for namespace review entry', () => {
    expect(getPreferredNamespaceReviewEntry([
      {
        id: 1,
        slug: 'archived-team',
        displayName: 'Archived Team',
        type: 'TEAM',
        status: 'ARCHIVED',
        immutable: false,
        canFreeze: false,
        canUnfreeze: false,
        canArchive: false,
        canRestore: false,
        currentUserRole: 'ADMIN',
        createdAt: '',
      },
      {
        id: 2,
        slug: 'active-team',
        displayName: 'Active Team',
        type: 'TEAM',
        status: 'ACTIVE',
        immutable: false,
        canFreeze: false,
        canUnfreeze: false,
        canArchive: false,
        canRestore: false,
        currentUserRole: 'OWNER',
        createdAt: '',
      },
    ])?.slug).toBe('active-team')
  })

  it('returns null when no manageable namespace exists', () => {
    expect(getPreferredNamespaceReviewEntry([
      {
        id: 1,
        slug: 'member-team',
        displayName: 'Member Team',
        type: 'TEAM',
        status: 'ACTIVE',
        immutable: false,
        canFreeze: false,
        canUnfreeze: false,
        canArchive: false,
        canRestore: false,
        currentUserRole: 'MEMBER',
        createdAt: '',
      },
    ])).toBeNull()
  })

  it('detects review center access from either platform roles or namespace roles', () => {
    expect(canAccessReviewCenter(['SKILL_ADMIN'], [])).toBe(true)
    expect(canAccessReviewCenter([], [
      {
        id: 2,
        slug: 'team-admin',
        displayName: 'Team Admin',
        type: 'TEAM',
        status: 'ACTIVE',
        immutable: false,
        canFreeze: false,
        canUnfreeze: false,
        canArchive: false,
        canRestore: false,
        currentUserRole: 'ADMIN',
        createdAt: '',
      },
    ])).toBe(true)
    expect(canAccessReviewCenter([], [])).toBe(false)
  })
})
