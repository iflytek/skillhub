export type SecurityVerdict = 'SAFE' | 'SUSPICIOUS' | 'DANGEROUS' | 'BLOCKED'
export type SecurityAuditDisplayState = SecurityVerdict | 'SCANNING'
export type FindingSeverity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | 'INFO'

export interface SecurityFinding {
  ruleId: string
  severity: FindingSeverity
  category: string
  title: string
  message: string | null
  filePath: string | null
  lineNumber: number | null
  codeSnippet: string | null
  remediation: string | null
  analyzer: string | null
  metadata: Record<string, unknown>
}

export interface SecurityAuditRecord {
  id: number
  scanId: string
  scannerType: string
  verdict: SecurityVerdict
  isSafe: boolean
  maxSeverity: string | null
  findingsCount: number
  findings: SecurityFinding[]
  scanDurationSeconds: number | null
  scannedAt: string | null
  createdAt: string
}
