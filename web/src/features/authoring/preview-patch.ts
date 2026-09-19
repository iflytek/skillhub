import type { FilePatch } from '@/api/types'

export interface PatchLine {
  kind: 'context' | 'removed' | 'added'
  text: string
}

/**
 * Builds a simple unified-diff-like preview for one patch.
 *
 * The goal is a trustworthy "what will change" confirmation for the author, not
 * a minimal diff: unchanged lines around edits are shown for context, removed
 * lines are prefixed with '-', and added lines with '+'.
 */
export function previewPatch(patch: FilePatch): PatchLine[] {
  // A trailing newline is a file property, not an extra empty line.
  const splitLines = (value: string): string[] =>
    value.endsWith('\n') ? value.slice(0, -1).split('\n') : value.split('\n')
  const oldLines = splitLines(patch.oldValue ?? '')
  const newLines = splitLines(patch.newValue ?? '')
  const lines: PatchLine[] = []

  if (patch.oldValue == null) {
    for (const line of newLines) {
      lines.push({ kind: 'added', text: line })
    }
    return lines
  }

  // Longest-common-subsequence table for line-level diffing.
  const table: number[][] = Array.from({ length: oldLines.length + 1 }, () =>
    new Array<number>(newLines.length + 1).fill(0),
  )
  for (let i = oldLines.length - 1; i >= 0; i--) {
    for (let j = newLines.length - 1; j >= 0; j--) {
      table[i][j] = oldLines[i] === newLines[j]
        ? table[i + 1][j + 1] + 1
        : Math.max(table[i + 1][j], table[i][j + 1])
    }
  }

  let i = 0
  let j = 0
  while (i < oldLines.length && j < newLines.length) {
    if (oldLines[i] === newLines[j]) {
      lines.push({ kind: 'context', text: oldLines[i] })
      i++
      j++
    } else if (table[i + 1][j] >= table[i][j + 1]) {
      lines.push({ kind: 'removed', text: oldLines[i] })
      i++
    } else {
      lines.push({ kind: 'added', text: newLines[j] })
      j++
    }
  }
  while (i < oldLines.length) {
    lines.push({ kind: 'removed', text: oldLines[i] })
    i++
  }
  while (j < newLines.length) {
    lines.push({ kind: 'added', text: newLines[j] })
    j++
  }
  return lines
}
