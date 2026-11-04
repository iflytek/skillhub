// Read only route metadata, never match.pathname, params, location or search.
export function currentPageType(router: {
  state: { matches: ReadonlyArray<{ routeId: string; globalNotFound?: boolean; status: string }> }
  routesById: Record<string, { fullPath: string }>
}): string {
  const matches = router.state.matches
  if (matches.some(match => match.globalNotFound || match.status === 'notFound')) return 'other'
  const leaf = matches[matches.length - 1]
  if (!leaf || leaf.routeId === '__root__') return 'other'
  return router.routesById[leaf.routeId]?.fullPath ?? 'other'
}
