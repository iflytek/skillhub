import { describe, expect, it } from 'vitest'
import { resolveSkillActionErrorTitle } from './skill-action-error'

describe('resolveSkillActionErrorTitle', () => {
  it('returns the download error title key for download failures', () => {
    expect(resolveSkillActionErrorTitle('download')).toBe('skillDetail.downloadErrorTitle')
  })

  it('returns the report error title key for report failures', () => {
    expect(resolveSkillActionErrorTitle('report')).toBe('skillDetail.reportErrorTitle')
  })
})
