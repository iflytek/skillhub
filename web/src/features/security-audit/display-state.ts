import type { SecurityAuditDisplayState, SecurityAuditRecord } from './types'

export function getSecurityAuditDisplayState(
  audit: Pick<SecurityAuditRecord, 'scannedAt' | 'verdict'>
): SecurityAuditDisplayState {
  return audit.scannedAt ? audit.verdict : 'SCANNING'
}
