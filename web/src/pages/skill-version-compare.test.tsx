/** @vitest-environment jsdom */

import { renderToStaticMarkup } from 'react-dom/server'
import { render, screen, fireEvent } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const useSkillVersionCompareMock = vi.fn()
const useSkillVersionsMock = vi.fn()
const navigateMock = vi.fn()

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => navigateMock,
  useParams: () => ({ namespace: 'global', slug: 'demo-skill' }),
  useSearch: () => ({ from: '1.0.0', to: '1.1.0' }),
}))

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => key,
    }),
  }
})

vi.mock('@/shared/hooks/use-skill-queries', () => ({
  useSkillVersionCompare: (...args: unknown[]) => useSkillVersionCompareMock(...args),
  useSkillVersions: (...args: unknown[]) => useSkillVersionsMock(...args),
}))

import { SkillVersionComparePage } from './skill-version-compare'

describe('SkillVersionComparePage', () => {
  beforeEach(() => {
    navigateMock.mockReset()

    useSkillVersionsMock.mockReturnValue({
      data: [
        { id: 10, version: '1.0.0', status: 'PUBLISHED', changelog: '', fileCount: 1, totalSize: 10, publishedAt: '2026-01-01T00:00:00Z', downloadAvailable: true },
        { id: 11, version: '1.1.0', status: 'PUBLISHED', changelog: '', fileCount: 1, totalSize: 12, publishedAt: '2026-01-02T00:00:00Z', downloadAvailable: true },
        { id: 12, version: '1.2.0-rc.1', status: 'UPLOADED', changelog: '', fileCount: 2, totalSize: 22, publishedAt: '2026-01-03T00:00:00Z', downloadAvailable: false },
      ],
      isLoading: false,
    })

    useSkillVersionCompareMock.mockReturnValue({
      data: {
        from: '1.0.0',
        to: '1.1.0',
        summary: { totalFiles: 2, addedFiles: 1, modifiedFiles: 1, removedFiles: 0, addedLines: 4, removedLines: 2 },
        files: [
          {
            path: 'README.md',
            changeType: 'MODIFIED',
            oldSize: 10,
            newSize: 12,
            binary: false,
            truncated: false,
            hunks: [
              {
                oldStart: 1,
                oldLines: 1,
                newStart: 1,
                newLines: 1,
                lines: [
                  { type: 'DELETE', content: 'old', oldLineNumber: 1, newLineNumber: null },
                  { type: 'ADD', content: 'new', oldLineNumber: null, newLineNumber: 1 },
                ],
              },
            ],
          },
          {
            path: 'src/index.ts',
            changeType: 'ADDED',
            oldSize: null,
            newSize: 50,
            binary: false,
            truncated: false,
            hunks: [
              {
                oldStart: 0,
                oldLines: 0,
                newStart: 1,
                newLines: 1,
                lines: [{ type: 'ADD', content: 'console.log(1)', oldLineNumber: null, newLineNumber: 1 }],
              },
            ],
          },
        ],
      },
      isLoading: false,
      error: null,
    })
  })

  it('renders compare summary and files', () => {
    const html = renderToStaticMarkup(<SkillVersionComparePage />)

    expect(html).toContain('skillCompare.totalFiles')
    expect(html).toContain('+4')
    expect(html).toContain('-2')
    expect(html).toContain('README.md')
    expect(html).toContain('src/index.ts')
  })

  it('renders search input and version selectors from published versions', () => {
    const html = renderToStaticMarkup(<SkillVersionComparePage />)

    expect(html).toContain('aria-label="skillCompare.searchFiles"')
    expect(html).toContain('aria-label="skillCompare.baseVersion"')
    expect(html).toContain('aria-label="skillCompare.headVersion"')
    expect(html).toContain('v1.0.0')
    expect(html).toContain('v1.1.0')
    expect(html).not.toContain('v1.2.0-rc.1')
  })

  it('switches active file marker when a file item is clicked', () => {
    render(<SkillVersionComparePage />)

    const readmeLink = screen.getByRole('link', { name: 'README.md' })
    const sourceLink = screen.getByRole('link', { name: 'src/index.ts' })

    expect(readmeLink.getAttribute('aria-current')).toBe('true')
    expect(sourceLink.getAttribute('aria-current')).toBeNull()

    fireEvent.click(sourceLink)

    expect(sourceLink.getAttribute('aria-current')).toBe('true')
    expect(readmeLink.getAttribute('aria-current')).toBeNull()
  })

  it('shows an empty state when there are not enough published versions', () => {
    useSkillVersionsMock.mockReturnValueOnce({
      data: [
        { id: 10, version: '1.0.0', status: 'PUBLISHED', changelog: '', fileCount: 1, totalSize: 10, publishedAt: '2026-01-01T00:00:00Z', downloadAvailable: true },
      ],
      isLoading: false,
    })

    const html = renderToStaticMarkup(<SkillVersionComparePage />)

    expect(html).toContain('skillCompare.notEnoughPublishedVersions')
  })
})
