import { describe, expect, it } from 'vitest'
import { inferMarkdownCodeLanguage } from './code-language'

describe('inferMarkdownCodeLanguage', () => {
  it('infers python code blocks', () => {
    expect(inferMarkdownCodeLanguage('from pypdf import PdfReader\nprint("ok")')).toBe('python')
  })

  it('infers bash command blocks', () => {
    expect(inferMarkdownCodeLanguage('pip install pypdf\npython script.py')).toBe('bash')
  })

  it('infers json payloads', () => {
    expect(inferMarkdownCodeLanguage('{\n  "name": "skillhub"\n}')).toBe('json')
  })

  it('infers yaml frontmatter style snippets', () => {
    expect(inferMarkdownCodeLanguage('name: pdf\nversion: 1.0.0')).toBe('yaml')
  })

  it('returns undefined for prose-like content', () => {
    expect(inferMarkdownCodeLanguage('This extracts all images as output files.')).toBeUndefined()
  })
})
