/** @vitest-environment jsdom */
import { renderToStaticMarkup } from 'react-dom/server'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import type { ReviewSkillDetail } from '@/api/types'
import { ReviewPresentationComparison } from './review-presentation-comparison'

vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }))
vi.mock('@/features/skill/markdown-renderer', () => ({ MarkdownRenderer: ({ content, className }: { content: string; className?: string }) => <div data-testid="comparison-markdown" className={className}>{content}</div> }))

afterEach(cleanup)

function detail(): ReviewSkillDetail {
  return {
    skill: { id: 1, slug: 'meeting-notes', namespace: 'team', displayName: 'Meeting notes', visibility: 'PUBLIC', status: 'ACTIVE', downloadCount: 0, starCount: 0, ratingCount: 0, hidden: false, canManageLifecycle: false, canSubmitPromotion: false, canInteract: false, canReport: false, resolutionMode: 'REVIEW_TASK' },
    versions: [], files: [], downloadUrl: null, activeVersion: '1.1.0',
    presentation: { displayName: 'Meeting notes', summary: 'New summary', overview: 'New overview', overviewSource: 'WEB' },
    comparison: {
      baseVersion: '1.0.0',
      baseline: { displayName: 'Meeting notes', summary: 'Original summary', overview: 'Original overview', overviewSource: 'WEB' },
      current: { displayName: 'Meeting notes', summary: 'New summary', overview: 'New overview', overviewSource: 'WEB' },
      files: [{ path: 'added.md', changeType: 'ADDED' }, { path: 'SKILL.md', changeType: 'MODIFIED' }, { path: 'README.md', changeType: 'UNCHANGED' }, { path: 'removed.txt', changeType: 'REMOVED' }],
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

  it('opens changed fields and leaves unchanged fields available behind a keyboard-operable disclosure', () => {
    render(<ReviewPresentationComparison detail={detail()} />)
    const name = screen.getByText('publish.displayName').closest('details')!
    const summary = screen.getByText('reviewWorkbench.summary').closest('details')!
    const overview = screen.getByText('reviewWorkbench.overview').closest('details')!
    expect(name.open).toBe(false)
    expect(summary.open).toBe(true)
    expect(overview.open).toBe(true)
    fireEvent.click(name.querySelector('summary')!)
    expect(name.open).toBe(true)
    fireEvent.click(summary.querySelector('summary')!)
    expect(summary.open).toBe(false)
  })

  it('keeps unavailable overview expanded and explicitly distinct from unchanged content', () => {
    const value = detail()
    value.comparison!.baseline.overview = null
    value.comparison!.current.overview = null
    value.comparison!.baseline.overviewSource = 'README'
    value.comparison!.current.overviewSource = 'README'
    render(<ReviewPresentationComparison detail={value} />)
    const overview = screen.getByText('reviewWorkbench.overview').closest('details')!
    expect(overview.open).toBe(true)
    expect(overview.querySelector('summary')?.textContent).toContain('reviewWorkbench.unavailableComparison')
    expect(overview.querySelector('summary')?.textContent).not.toContain('reviewWorkbench.unchanged')
  })

  it('uses container-sized columns, scopes compact markdown and emphasizes the modified snapshot', () => {
    render(<ReviewPresentationComparison detail={detail()} />)
    const oldSummary = screen.getByText('Original summary').parentElement!
    const newSummary = screen.getByText('New summary').parentElement!
    expect(oldSummary.parentElement).toBe(newSummary.parentElement)
    expect(newSummary.parentElement?.className).toContain('grid-cols-[repeat(auto-fit,minmax(min(100%,28rem),1fr))]')
    expect(newSummary.parentElement?.className).not.toContain('md:grid-cols-2')
    expect(newSummary.className).toContain('bg-amber-500/5')
    expect(newSummary.className).toContain('dark:border-amber-400/30')
    expect(oldSummary.className).not.toContain('bg-amber-500/5')
    expect(screen.getAllByTestId('comparison-markdown')).toHaveLength(2)
    for (const markdown of screen.getAllByTestId('comparison-markdown')) {
      expect(markdown.className).toContain('[&_h1]:text-xl')
      expect(markdown.className).toContain('[&_h2]:text-lg')
      expect(markdown.className).toContain('[&_p]:text-sm')
      expect(markdown.className).toContain('[&_p]:leading-6')
    }
  })

  it('retains labels alongside distinct semantic file-change colors in both themes', () => {
    render(<ReviewPresentationComparison detail={detail()} />)
    for (const [kind, color] of [['ADDED', 'emerald'], ['MODIFIED', 'amber'], ['REMOVED', 'red']]) {
      const badge = screen.getByText(`reviewWorkbench.file${kind}`)
      expect(badge.className).toContain(`bg-${color}-500/10`)
      expect(badge.className).toContain(`dark:text-${color}-200`)
    }
    expect(screen.getByText('reviewWorkbench.fileUNCHANGED').className).toContain('text-muted-foreground')
    expect(screen.getAllByText('reviewWorkbench.modified').every((badge) => badge.className.includes('bg-amber-500/10'))).toBe(true)
  })

})
