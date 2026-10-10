import { describe, expect, it } from 'vitest'
import { stripMarkdownFrontmatter } from './markdown-frontmatter'

describe('stripMarkdownFrontmatter', () => {
  it('removes yaml frontmatter from markdown content', () => {
    const markdown = `---
name: demo
description: sample
---

# Title
`

    expect(stripMarkdownFrontmatter(markdown)).toBe('\n# Title\n')
  })

  it('removes toml frontmatter from markdown content', () => {
    const markdown = `+++
name = "demo"
+++

Body`

    expect(stripMarkdownFrontmatter(markdown)).toBe('\nBody')
  })

  it('returns the original content when no frontmatter exists', () => {
    expect(stripMarkdownFrontmatter('# Title')).toBe('# Title')
  })
})
