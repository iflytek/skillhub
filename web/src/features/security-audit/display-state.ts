import type { SecurityAuditDisplayState, SecurityAuditRecord } from './types'

export function getSecurityAuditDisplayState(
  audit: Pick<SecurityAuditRecord, 'scannedAt' | 'verdict'>,
  versionStatus?: string
): SecurityAuditDisplayState {
  if (audit.scannedAt) {
    return audit.verdict
  }
  if (versionStatus === 'SCAN_FAILED') {
    return 'SCAN_FAILED'
  }
  return 'SCANNING'
}
