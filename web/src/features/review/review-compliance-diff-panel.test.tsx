/** @vitest-environment jsdom */
import { renderToStaticMarkup } from 'react-dom/server'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
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

afterEach(cleanup)

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

  it('does not turn an unavailable snapshot into additions or removals', () => {
    const snapshot = { schemaVersion: '1.0', digest: 'digest', items: [{ standard: 'soc2', controlId: 'CC1', evidence: [] }] }
    for (const missing of ['base', 'pending']) {
      const html = renderToStaticMarkup(<ReviewComplianceDiffPanel
        baseVersion={createVersion({ complianceSnapshot: missing === 'base' ? undefined : snapshot })}
        pendingVersion={createVersion({ version: '1.1.0', complianceSnapshot: missing === 'pending' ? undefined : snapshot })} />)
      expect(html).toContain('reviewWorkbench.complianceIncomplete')
      expect(html).toContain('CC1')
      expect(html).not.toContain('review.complianceDiffAddedLabel')
      expect(html).not.toContain('review.complianceDiffRemovedLabel')
    }
  })

  it('shows a first-publication declaration without a fabricated historical comparison', () => {
    const html = renderToStaticMarkup(<ReviewComplianceDiffPanel pendingVersion={createVersion({
      complianceSnapshot: { items: [{ standard: 'soc2', controlId: 'CC1' }] },
    })} />)
    expect(html).toContain('CC1')
    expect(html).not.toContain('reviewWorkbench.complianceIncomplete')
    expect(html).not.toContain('review.complianceDiffAddedLabel')
  })

  it('warns when the server names a baseline but its version metadata is unavailable', () => {
    const html = renderToStaticMarkup(<ReviewComplianceDiffPanel baselineExpected pendingVersion={createVersion({
      complianceSnapshot: { items: [{ standard: 'soc2', controlId: 'CC1' }] },
    })} />)
    expect(html).toContain('reviewWorkbench.complianceIncomplete')
  })

  it('keeps identical populated declarations as a lightweight unchanged message', () => {
    const version = createVersion({ complianceSnapshot: { items: [{ standard: 'soc2', controlId: 'CC1' }] } })
    const html = renderToStaticMarkup(<ReviewComplianceDiffPanel baseVersion={version} pendingVersion={version} />)
    expect(html).toContain('reviewWorkbench.complianceUnchanged')
    expect(html).not.toContain('<details')
  })

  it('hides unavailable or empty declarations when neither side has displayable items', () => {
    expect(renderToStaticMarkup(<ReviewComplianceDiffPanel baseVersion={createVersion()} pendingVersion={createVersion()} />)).toBe('')
  })

  it('labels the missing side neutrally after expanding additions and removals', () => {
    const empty = { items: [] }
    const populated = { items: [{ standard: 'soc2', controlId: 'CC1' }] }
    for (const kind of ['added', 'removed']) {
      const view = render(<ReviewComplianceDiffPanel
        baseVersion={createVersion({ complianceSnapshot: kind === 'added' ? empty : populated })}
        pendingVersion={createVersion({ version: '1.1.0', complianceSnapshot: kind === 'added' ? populated : empty })} />)
      const toggle = screen.getByText('review.complianceDiffViewDetails').closest('summary')!
      fireEvent.click(toggle)
      expect(toggle.closest('details')?.open).toBe(true)
      expect(screen.getByText(kind === 'added' ? 'reviewWorkbench.complianceAbsentBase' : 'reviewWorkbench.complianceAbsentPending')).toBeTruthy()
      expect(screen.queryByText('review.complianceDiffBaseRemoved')).toBeNull()
      expect(screen.queryByText('review.complianceDiffPendingAdded')).toBeNull()
      view.unmount()
    }
  })

  it('distinguishes a successfully read empty snapshot from an unavailable counterpart', () => {
    const emptyVersion = createVersion({ complianceSnapshot: { items: [] } })
    const missingSnapshot = createVersion()
    for (const [base, pending] of [[emptyVersion, missingSnapshot], [missingSnapshot, emptyVersion], [null, emptyVersion], [emptyVersion, null]]) {
      const html = renderToStaticMarkup(<ReviewComplianceDiffPanel baselineExpected baseVersion={base} pendingVersion={pending} />)
      expect(html).toContain('reviewWorkbench.complianceIncomplete')
      expect(html).not.toContain('<details')
      expect(html).not.toContain('review.complianceDiffAddedLabel')
      expect(html).not.toContain('review.complianceDiffRemovedLabel')
    }
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
