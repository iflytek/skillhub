import type { SecurityAuditDisplayState, SecurityAuditRecord } from './types'

export function getSecurityAuditDisplayState(
  audit: Pick<SecurityAuditRecord, 'scannedAt' | 'verdict' | 'failureReason'>,
  versionStatus?: string
): SecurityAuditDisplayState {
  if (audit.failureReason || versionStatus === 'SCAN_FAILED') {
    return 'SCAN_FAILED'
  }
  if (audit.scannedAt) {
    return audit.verdict
  }
  return 'SCANNING'
}
