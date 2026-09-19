import { describe, expect, it } from 'vitest'
import { formatBytes, isBinaryDraftPath } from './binary-file'

describe('isBinaryDraftPath', () => {
  it('marks image and office extensions as binary', () => {
    expect(isBinaryDraftPath('assets/logo.png')).toBe(true)
    expect(isBinaryDraftPath('assets/logo.PNG')).toBe(true)
    expect(isBinaryDraftPath('docs/report.pdf')).toBe(true)
    expect(isBinaryDraftPath('assets/sheet.xlsx')).toBe(true)
  })

  it('keeps text formats editable', () => {
    expect(isBinaryDraftPath('SKILL.md')).toBe(false)
    expect(isBinaryDraftPath('references/metrics.md')).toBe(false)
    expect(isBinaryDraftPath('assets/diagram.svg')).toBe(false)
    expect(isBinaryDraftPath('scripts/greet.sh')).toBe(false)
    expect(isBinaryDraftPath('validation.yaml')).toBe(false)
  })
})

describe('formatBytes', () => {
  it('formats byte sizes for humans', () => {
    expect(formatBytes(512)).toBe('512 B')
    expect(formatBytes(2048)).toBe('2.0 KB')
    expect(formatBytes(3 * 1024 * 1024)).toBe('3.0 MB')
  })
})
