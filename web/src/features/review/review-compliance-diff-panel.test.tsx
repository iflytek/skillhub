import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it, vi } from 'vitest'
import type { SkillVersion } from '@/api/types'
import { ReviewComplianceDiffPanel } from './review-compliance-diff-panel'

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string, values?: Record<string, number | string>) => {
        if (key === 'review.complianceDiffAddedLabel' || key === 'review.complianceDiffRemovedLabel' || key === 'review.complianceDiffModifiedLabel') {
          return `${key}:${values?.count}`
        }
        if (key === 'review.complianceDiffDescription') {
          return `${key}:${values?.baseVersion}->${values?.pendingVersion}`
        }
        return key
      },
      i18n: { language: 'zh' },
    }),
  }
})

function createVersion(overrides: Partial<SkillVersion> = {}): SkillVersion {
  return {
    id: 1,
    version: '1.0.0',
    status: 'PUBLISHED',
    changelog: '',
    fileCount: 1,
    totalSize: 100,
    publishedAt: '2026-03-19T00:00:00Z',
    downloadAvailable: true,
    ...overrides,
  }
}

describe('ReviewComplianceDiffPanel', () => {
  it('renders a clickable diff summary and item details', () => {
    const html = renderToStaticMarkup(
      <ReviewComplianceDiffPanel
        baseVersion={createVersion({
          version: '1.0.0',
          complianceSnapshot: {
            schemaVersion: '1.0',
            digest: 'sha256:base-digest-value',
            items: [
              {
                standard: 'soc2',
                version: '2026',
                controlId: 'CC7.2',
                title: 'Monitoring activities',
                evidence: [{ type: 'packaged-file', path: 'evidence/soc2.md', sha256: 'sha-base' }],
              },
            ],
          },
        })}
        pendingVersion={createVersion({
          version: '1.1.0',
          status: 'PENDING_REVIEW',
          complianceSnapshot: {
            schemaVersion: '1.0',
            digest: 'sha256:pending-digest-value',
            items: [],
          },
        })}
      />,
    )

    expect(html).toContain('review.complianceDiffTitle')
    expect(html).toContain('review.complianceDiffRemovedLabel:1')
    expect(html).toContain('review.complianceDiffBaseDigest')
    expect(html).toContain('review.complianceDiffPendingDigest')
    expect(html).toContain('soc2')
    expect(html).toContain('CC7.2')
    expect(html).toContain('review.complianceDiffViewDetails')
  })

  it('renders nothing when there is no diff', () => {
    const html = renderToStaticMarkup(
      <ReviewComplianceDiffPanel
        baseVersion={createVersion({
          complianceSnapshot: {
            schemaVersion: '1.0',
            digest: 'sha256:same',
            items: [],
          },
        })}
        pendingVersion={createVersion({
          version: '1.1.0',
          status: 'PENDING_REVIEW',
          complianceSnapshot: {
            schemaVersion: '1.0',
            digest: 'sha256:same-2',
            items: [],
          },
        })}
      />,
    )

    expect(html).toBe('')
  })
})
