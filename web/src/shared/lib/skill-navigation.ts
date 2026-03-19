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

export function normalizeSkillDetailReturnTo(returnTo?: string) {
  return returnTo && returnTo.startsWith('/') ? returnTo : undefined
}
