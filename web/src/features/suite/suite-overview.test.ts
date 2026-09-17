import { describe, expect, it } from 'vitest'
import { withoutDuplicateSuiteTitle } from './suite-overview'

describe('withoutDuplicateSuiteTitle', () => {
  it('removes a leading H1 that repeats the Suite name', () => {
    expect(withoutDuplicateSuiteTitle('# Care Workflow\n\nUse the entry skill.', 'Care Workflow'))
      .toBe('Use the entry skill.')
  })

  it('keeps a different heading and headings that are not first', () => {
    expect(withoutDuplicateSuiteTitle('# Usage\n\nInstructions', 'Care Workflow'))
      .toBe('# Usage\n\nInstructions')
    expect(withoutDuplicateSuiteTitle('Introduction\n\n# Care Workflow', 'Care Workflow'))
      .toBe('Introduction\n\n# Care Workflow')
  })
})
