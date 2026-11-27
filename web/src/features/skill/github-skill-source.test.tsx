import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it, vi } from 'vitest'
import type { GithubSkillSource } from '@/api/types'
import { GithubSkillSourceCard } from './github-skill-source'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) => {
      if (!options) return key
      const values = Object.entries(options)
        .filter(([name]) => name !== 'defaultValue')
        .map(([name, value]) => `${name}=${String(value)}`)
      return values.length ? `${key}[${values.join(',')}]` : key
    },
  }),
}))

const commitSha = '4ae2317ddb22b046e26455e4f9c9b3aaaf62212d'

function source(overrides: Partial<GithubSkillSource> = {}): GithubSkillSource {
  return {
    ownerLogin: 'upstream',
    repositoryName: 'skills',
    skillPath: 'agents/demo',
    commitSha,
    syncRevision: 2,
    licenseStatus: 'PRESENT_UNCLASSIFIED',
    licenseDocuments: [
      { scope: 'REPOSITORY_ROOT', path: 'LICENSE', bundled: true },
      { scope: 'SKILL_ROOT', path: 'NOTICE.md', bundled: false },
    ],
    ...overrides,
  }
}

function render(value: GithubSkillSource) {
  return renderToStaticMarkup(createElement(GithubSkillSourceCard, { version: '1.2.0', source: value }))
}

describe('GithubSkillSourceCard', () => {
  it('links the published commit and both license scopes to fixed GitHub paths', () => {
    const html = render(source())

    expect(html).toContain(`/commit/${commitSha}`)
    expect(html).toContain(`/blob/${commitSha}/LICENSE`)
    expect(html).toContain(`/blob/${commitSha}/agents/demo/NOTICE.md`)
    expect(html).toContain('review.githubRevisionValue')
    expect(html).not.toContain('ingestionStatus')
  })

  it('shows the repository tag relation and the separately declared version', () => {
    const html = render(source({
      upstreamTag: {
        status: 'AFTER_TAG', scope: 'REPOSITORY', tag: 'v1.3.2',
        tagCommitSha: 'd1478fcb140db0b93c0c8df17bcfd311844749d0', commitsAfterTag: 3,
      },
      declaredVersion: '2.1.0',
    }))

    expect(html).toContain('skillDetail.githubUpstreamTagScope')
    expect(html).toContain('skillDetail.githubUpstreamTagAFTER_TAG[tag=v1.3.2,count=3,commit=4ae2317d]')
    expect(html).toContain('2.1.0')
    expect(html).not.toContain('skillDetail.githubDeclaredVersionNone')
  })

  it.each([
    ['EXACT', 'skillDetail.githubUpstreamTagEXACT[tag=v7.0.1,count=0,'],
    ['NO_TAG', 'skillDetail.githubUpstreamTagNO_TAG['],
    ['UNKNOWN', 'skillDetail.githubUpstreamTagUNKNOWN['],
    ['UNRECORDED', 'skillDetail.githubUpstreamTagUNRECORDED['],
  ] as const)('renders %s without inventing a version', (status, expected) => {
    const html = render(source({
      upstreamTag: status === 'EXACT'
        ? { status, scope: 'REPOSITORY', tag: 'v7.0.1', tagCommitSha: commitSha, commitsAfterTag: 0 }
        : { status, scope: 'REPOSITORY' },
      declaredVersion: null,
    }))

    expect(html).toContain(expected)
    expect(html).toContain('skillDetail.githubDeclaredVersionNone')
  })

  it('treats a server response without source tag fields as unrecorded', () => {
    expect(render(source())).toContain('skillDetail.githubUpstreamTagUNRECORDED[')
  })
})
