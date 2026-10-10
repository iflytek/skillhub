/** Removes only a leading Markdown H1 that duplicates the page-level Suite title. */
export function withoutDuplicateSuiteTitle(content: string, displayName: string): string {
  const lines = content.split(/\r?\n/)
  const firstContentLine = lines.findIndex(line => line.trim().length > 0)
  if (firstContentLine < 0) return content

  const heading = lines[firstContentLine].trim().match(/^#\s+(.+?)\s*#*$/)
  if (!heading || heading[1].trim() !== displayName.trim()) return content

  lines.splice(firstContentLine, 1)
  while (lines[firstContentLine]?.trim() === '') lines.splice(firstContentLine, 1)
  return lines.join('\n')
}
