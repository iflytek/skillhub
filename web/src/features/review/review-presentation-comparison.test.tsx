import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it, vi } from 'vitest'
import type { ReviewSkillDetail } from '@/api/types'
import { ReviewPresentationComparison } from './review-presentation-comparison'

vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }))
vi.mock('@/features/skill/markdown-renderer', () => ({ MarkdownRenderer: ({ content }: { content: string }) => <div>{content}</div> }))

function detail(): ReviewSkillDetail {
  return {
    skill: { id: 1, slug: 'meeting-notes', namespace: 'team', displayName: 'Meeting notes', visibility: 'PUBLIC', status: 'ACTIVE', downloadCount: 0, starCount: 0, ratingCount: 0, hidden: false, canManageLifecycle: false, canSubmitPromotion: false, canInteract: false, canReport: false, resolutionMode: 'REVIEW_TASK' },
    versions: [], files: [], downloadUrl: null, activeVersion: '1.1.0',
    presentation: { displayName: 'Meeting notes', summary: 'New summary', overview: 'New overview', overviewSource: 'WEB' },
    comparison: {
      baseVersion: '1.0.0',
      baseline: { displayName: 'Meeting notes', summary: 'Original summary', overview: 'Original overview', overviewSource: 'WEB' },
      current: { displayName: 'Meeting notes', summary: 'New summary', overview: 'New overview', overviewSource: 'WEB' },
      files: [{ path: 'SKILL.md', changeType: 'MODIFIED' }, { path: 'README.md', changeType: 'UNCHANGED' }, { path: 'removed.txt', changeType: 'REMOVED' }],
    },
  }
}

describe('ReviewPresentationComparison', () => {
  it('compares the server-selected published baseline with the bound review snapshot and file manifest', () => {
    const html = renderToStaticMarkup(<ReviewPresentationComparison detail={detail()} />)
    expect(html).toContain('1.0.0')
    expect(html).toContain('1.1.0')
    expect(html).toContain('Original summary')
    expect(html).toContain('New summary')
    expect(html).toContain('Original overview')
    expect(html).toContain('New overview')
    expect(html).toContain('reviewWorkbench.fileMODIFIED')
    expect(html).toContain('reviewWorkbench.fileUNCHANGED')
    expect(html).toContain('reviewWorkbench.fileREMOVED')
  })

  it('does not describe unread fallback documentation as unchanged', () => {
    const value = detail()
    value.comparison!.baseline.overview = null
    value.comparison!.baseline.overviewSource = 'README'
    value.comparison!.current.overview = null
    value.comparison!.current.overviewSource = 'README'
    const html = renderToStaticMarkup(<ReviewPresentationComparison detail={value} />)
    expect(html).toContain('reviewWorkbench.unavailableComparison')
    expect(html.match(/reviewWorkbench.overviewUnavailable/g)).toHaveLength(2)
  })

  it('does not invent a baseline from other pending versions', () => {
    const value = detail()
    value.comparison = null
    const html = renderToStaticMarkup(<ReviewPresentationComparison detail={value} />)
    expect(html).toContain('reviewWorkbench.noBaseline')
    expect(html).not.toContain('1.0.0')
  })
})
