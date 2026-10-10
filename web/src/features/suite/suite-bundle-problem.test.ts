import { describe, expect, it } from 'vitest'
import { suiteBundleProblemKind } from './suite-bundle-problem'

describe('suiteBundleProblemKind', () => {
  it.each([
    ['BLOCKED_RETRYABLE', 'MEMBER_SCAN_FAILED', 'memberScanFailed'],
    ['BLOCKED_RETRYABLE', 'MEMBER_EXECUTION_FAILED', 'memberExecutionFailed'],
    ['BLOCKED_RETRYABLE', 'AUTHORIZATION_OR_NAMESPACE_BLOCKED', 'authorizationBlocked'],
    ['REPREVIEW_REQUIRED', 'BUNDLE_PLAN_CHANGED', 'planChanged'],
    ['BLOCKED_RETRYABLE', 'UNKNOWN_INTERNAL_CODE', 'blocked'],
    ['REPREVIEW_REQUIRED', undefined, 'planChanged'],
  ])('maps %s / %s to %s', (status, failureCode, expected) => {
    expect(suiteBundleProblemKind(status, failureCode)).toBe(expected)
  })

  it.each(['RUNNING', 'WAITING_FOR_MEMBERS', 'SUITE_DRAFT_CREATED', 'CANCELLED']) (
    'does not show a problem for %s',
    (status) => expect(suiteBundleProblemKind(status, 'MEMBER_EXECUTION_FAILED')).toBeNull(),
  )
})
