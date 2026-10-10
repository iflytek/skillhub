/**
 * Helpers for constructing and validating navigation state around skill-detail pages.
 */
export function getSkillSquareSearch() {
  return {
    q: '',
    sort: 'relevance' as const,
    page: 0,
    starredOnly: false,
  }
}

/** Search params for browsing skills that share a given label (from detail chips). */
export function getSkillLabelSearch(label: string) {
  return {
    q: '',
    label,
    sort: 'newest' as const,
    page: 0,
    starredOnly: false,
  }
}

export function normalizeSkillDetailReturnTo(returnTo?: string) {
  return returnTo && returnTo.startsWith('/') ? returnTo : undefined
}
