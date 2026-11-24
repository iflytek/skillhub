/** Read-only GitHub provenance returned by the registry for synced versions. */
export interface GithubUpstreamTag {
  status: 'EXACT' | 'AFTER_TAG' | 'NO_TAG' | 'UNKNOWN' | 'UNRECORDED'
  scope: 'REPOSITORY'
  tag?: string | null
  tagCommitSha?: string | null
  commitsAfterTag?: number | null
  reason?: string | null
}

export interface GithubSource {
  ownerLogin: string
  repositoryName: string
  skillPath: string
  commitSha: string
  syncRevision: number
  upstreamTag?: GithubUpstreamTag
  declaredVersion?: string | null
}

/**
 * Short human label for the repository tag relation, e.g. `upstream v1.3.2+3`.
 * Returns undefined when nothing was recorded so existing output stays unchanged.
 */
export function formatUpstreamTag(source: GithubSource | null | undefined): string | undefined {
  const upstream = source?.upstreamTag
  if (!upstream) return undefined
  switch (upstream.status) {
    case 'EXACT':
      return upstream.tag ? `upstream ${upstream.tag}` : undefined
    case 'AFTER_TAG':
      return upstream.tag ? `upstream ${upstream.tag}+${upstream.commitsAfterTag ?? '?'}` : undefined
    case 'NO_TAG':
      return 'upstream: no repository tag'
    case 'UNKNOWN':
      return 'upstream: unknown'
    default:
      return undefined
  }
}
