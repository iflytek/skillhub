const FRONTMATTER_PATTERN = /^(---[\t ]*\r?\n[\s\S]*?\r?\n---[\t ]*(?:\r?\n|$)|\+\+\+[\t ]*\r?\n[\s\S]*?\r?\n\+\+\+[\t ]*(?:\r?\n|$))/

export function stripMarkdownFrontmatter(content: string) {
  return content.replace(FRONTMATTER_PATTERN, '')
}
