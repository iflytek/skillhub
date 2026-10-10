export type SuiteBundleProblemKind =
  | 'memberScanFailed'
  | 'memberExecutionFailed'
  | 'authorizationBlocked'
  | 'planChanged'
  | 'draftMissing'
  | 'blocked'

export function suiteBundleProblemKind(
  status: string | undefined,
  failureCode: string | undefined,
): SuiteBundleProblemKind | null {
  if (status !== 'BLOCKED_RETRYABLE' && status !== 'REPREVIEW_REQUIRED') return null

  switch (failureCode) {
    case 'MEMBER_SCAN_FAILED':
      return 'memberScanFailed'
    case 'MEMBER_EXECUTION_FAILED':
      return 'memberExecutionFailed'
    case 'AUTHORIZATION_OR_NAMESPACE_BLOCKED':
      return 'authorizationBlocked'
    case 'BUNDLE_PLAN_CHANGED':
      return 'planChanged'
    default:
      return status === 'REPREVIEW_REQUIRED' ? 'planChanged' : 'blocked'
  }
}
