import { describe, expect, it } from 'vitest'
import { REPORT_TEXT_WRAP_CLASS_NAME } from './report-text'

describe('REPORT_TEXT_WRAP_CLASS_NAME', () => {
  it('preserves line breaks and forces long content to wrap', () => {
    expect(REPORT_TEXT_WRAP_CLASS_NAME).toContain('whitespace-pre-wrap')
    expect(REPORT_TEXT_WRAP_CLASS_NAME).toContain('break-words')
    expect(REPORT_TEXT_WRAP_CLASS_NAME).toContain('[overflow-wrap:anywhere]')
  })
})
