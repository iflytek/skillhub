import { describe, expect, it } from 'vitest'
import en from './locales/en.json'
import zh from './locales/zh.json'

describe('security audit locales', () => {
  it('defines the scanning label in both locales', () => {
    expect(zh.securityAudit.statusScanning).toBe('扫描中')
    expect(en.securityAudit.statusScanning).toBe('Scanning')
  })

  it('uses the updated blocked wording in both locales', () => {
    expect(zh.securityAudit.verdict.BLOCKED).toBe('高风险')
    expect(en.securityAudit.verdict.BLOCKED).toBe('High Risk')
  })
})
