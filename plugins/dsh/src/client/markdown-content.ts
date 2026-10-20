// Same complete leading fence contract as the web reader. Never parse this block
// as installation identity; Registry metadata is authoritative for that purpose.
const frontmatter = /^(?:---[\t ]*\r?\n[\s\S]*?\r?\n---[\t ]*(?:\r?\n|$)|\+\+\+[\t ]*\r?\n[\s\S]*?\r?\n\+\+\+[\t ]*(?:\r?\n|$))/u;
export function stripMarkdownFrontmatter(content: string): string {
  const normalized = content.replace(/^\uFEFF/u, '');
  const block = normalized.match(frontmatter)?.[0];
  if (!block) return normalized;
  // Two ordinary thematic breaks must not swallow a Markdown paragraph.
  const yaml = block.startsWith('---');
  const hasFields = block.split(/\r?\n/u).slice(1, -1).some(line => yaml
    ? /^[A-Za-z_][\w.-]*\s*:/u.test(line)
    : /^(?:[A-Za-z_][\w.-]*\s*=|\[[^\]]+\])/u.test(line));
  return hasFields ? normalized.slice(block.length) : normalized;
}
