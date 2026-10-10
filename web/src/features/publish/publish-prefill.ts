const VALID_VISIBILITIES = new Set(['PUBLIC', 'NAMESPACE_ONLY', 'PRIVATE'])

interface PublishPrefillSearch {
  namespace?: string
  visibility?: string
  resubmitSkill?: string
  resubmitVersion?: string
}

export interface PublishPrefillState {
  namespace: string
  visibility: string
  resubmitSkill: string
  resubmitVersion: string
}

export function normalizePublishPrefill(search: PublishPrefillSearch): PublishPrefillState {
  const namespace = typeof search.namespace === 'string' ? search.namespace.trim() : ''
  const normalizedVisibility = typeof search.visibility === 'string'
    ? search.visibility.trim().toUpperCase()
    : ''

  const resubmitSkill = typeof search.resubmitSkill === 'string'
    ? search.resubmitSkill.trim()
    : ''
  const resubmitVersion = typeof search.resubmitVersion === 'string'
    ? search.resubmitVersion.trim()
    : ''

  return {
    namespace,
    visibility: VALID_VISIBILITIES.has(normalizedVisibility) ? normalizedVisibility : 'PUBLIC',
    resubmitSkill,
    resubmitVersion,
  }
}
