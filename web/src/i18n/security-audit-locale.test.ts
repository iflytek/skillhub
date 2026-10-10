import { describe, expect, it } from 'vitest'
import en from './locales/en.json'
import zh from './locales/zh.json'
import ru from './locales/ru.json'

describe('security audit locales', () => {
  it('defines the scanning label in both locales', () => {
    expect(zh.securityAudit.statusScanning).toBe('扫描中')
    expect(en.securityAudit.statusScanning).toBe('Scanning')
  })

  it('uses the updated blocked wording in both locales', () => {
    expect(zh.securityAudit.verdict.BLOCKED).toBe('高风险')
    expect(en.securityAudit.verdict.BLOCKED).toBe('High Risk')
  })

  it('uses the precise safe verdict wording in all locales', () => {
    expect(en.securityAudit.verdict.SAFE).toBe('No high-risk findings')
    expect(zh.securityAudit.verdict.SAFE).toBe('未发现高风险问题')
    expect(ru.securityAudit.verdict.SAFE).toBe('Угроз высокого риска не обнаружено')
  })

  it('keeps verdict keys in parity across all locales', () => {
    expect(Object.keys(en.securityAudit.verdict).sort()).toEqual(
      Object.keys(zh.securityAudit.verdict).sort(),
    )
    expect(Object.keys(en.securityAudit.verdict).sort()).toEqual(
      Object.keys(ru.securityAudit.verdict).sort(),
    )
  })
})
