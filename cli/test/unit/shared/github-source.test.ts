import { describe, expect, test } from 'bun:test'
import { formatUpstreamTag, type GithubSource } from '../../../src/shared/github-source'

function source(upstreamTag?: GithubSource['upstreamTag']): GithubSource {
  return { ownerLogin: 'o', repositoryName: 'r', skillPath: 's', commitSha: 'a'.repeat(40), syncRevision: 1,
    ...(upstreamTag ? { upstreamTag } : {}) }
}

describe('formatUpstreamTag', () => {
  test('labels each recorded relation', () => {
    expect(formatUpstreamTag(source({ status: 'EXACT', scope: 'REPOSITORY', tag: 'v7.0.1', commitsAfterTag: 0 })))
      .toBe('upstream v7.0.1')
    expect(formatUpstreamTag(source({ status: 'AFTER_TAG', scope: 'REPOSITORY', tag: 'v1.3.2', commitsAfterTag: 3 })))
      .toBe('upstream v1.3.2+3')
    expect(formatUpstreamTag(source({ status: 'NO_TAG', scope: 'REPOSITORY' }))).toBe('upstream: no repository tag')
    expect(formatUpstreamTag(source({ status: 'UNKNOWN', scope: 'REPOSITORY', reason: 'TIMEOUT' }))).toBe('upstream: unknown')
  })

  test('keeps output unchanged when nothing was recorded', () => {
    expect(formatUpstreamTag(undefined)).toBeUndefined()
    expect(formatUpstreamTag(source())).toBeUndefined()
    expect(formatUpstreamTag(source({ status: 'UNRECORDED', scope: 'REPOSITORY' }))).toBeUndefined()
  })
})
