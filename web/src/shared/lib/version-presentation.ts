/** Read legacy version snapshots without rewriting the package or trusting arbitrary JSON. */
export function getVersionPresentation(snapshot?: string): { displayName?: string; summary?: string; overview?: string } {
  if (!snapshot) return {}
  try {
    const value: unknown = JSON.parse(snapshot)
    if (!isObject(value)) return {}
    const frontmatter = isObject(value.frontmatter) ? value.frontmatter : {}
    const metadata = isObject(frontmatter.metadata) ? frontmatter.metadata : {}
    const displayName = validName(value.displayNameOverride) ?? validName(metadata['display-name'])
      ?? (typeof value.name === 'string' ? value.name : undefined)
    const overview = typeof value.overviewOverride === 'string' && value.overviewOverride.trim().length > 0
      ? value.overviewOverride.trim()
      : typeof metadata.overview === 'string' && metadata.overview.trim().length > 0 ? metadata.overview.trim() : undefined
    const summary = typeof value.shortDescriptionOverride === 'string' && value.shortDescriptionOverride.trim().length > 0
      ? value.shortDescriptionOverride.trim()
      : typeof metadata['short-description'] === 'string' && metadata['short-description'].trim().length > 0
        ? metadata['short-description'].trim()
        : typeof value.description === 'string' ? value.description : undefined
    return overview ? { displayName, summary, overview } : { displayName, summary }
  } catch {
    return {}
  }
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}
function validName(value: unknown): string | undefined {
  return typeof value === 'string' && value.trim().length > 0 && value.trim().length <= 200 ? value.trim() : undefined
}
