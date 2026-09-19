import { describe, expect, it } from 'vitest'
import { previewPatch } from './preview-patch'

describe('previewPatch', () => {
  it('marks a creation patch as all added lines', () => {
    const lines = previewPatch({ filePath: 'SKILL.md', oldValue: undefined, newValue: '---\nname: x\n' })
    expect(lines).toEqual([
      { kind: 'added', text: '---' },
      { kind: 'added', text: 'name: x' },
    ])
  })

  it('emits removed and added lines around an edit', () => {
    const lines = previewPatch({
      filePath: 'SKILL.md',
      oldValue: '---\nname: broken\n---\n',
      newValue: '---\nname: broken\ndescription: fixed\n---\n',
    })
    expect(lines).toEqual([
      { kind: 'context', text: '---' },
      { kind: 'context', text: 'name: broken' },
      { kind: 'added', text: 'description: fixed' },
      { kind: 'context', text: '---' },
    ])
  })

  it('marks a line change as one removal plus one addition', () => {
    const lines = previewPatch({
      filePath: 'scripts/run.sh',
      oldValue: 'echo old',
      newValue: 'echo new',
    })
    expect(lines).toEqual([
      { kind: 'removed', text: 'echo old' },
      { kind: 'added', text: 'echo new' },
    ])
  })
})
