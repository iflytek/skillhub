/** @vitest-environment jsdom */
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { SkillInstructions } from './skill-instructions'
const query = vi.hoisted(() => ({ data: '# Original instructions', error: null as unknown, isLoading: false, refetch: vi.fn() }))
vi.mock('@/shared/hooks/use-skill-queries', () => ({ useSkillFile: () => query }))
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }))
vi.mock('./markdown-renderer', () => ({ MarkdownRenderer: ({ content }: {content: string}) => <article>{content}</article> }))
const props = { namespace: 'team', slug: 'example', version: '1.0.0', available: true, loading: false, error: null, retryFiles: vi.fn(), onLinkClick: vi.fn() }
afterEach(() => { cleanup(); query.error = null; query.isLoading = false; vi.clearAllMocks() })
describe('skill instruction reading', () => {
  it('reads original content and toggles source without modifying it', () => {
    render(<SkillInstructions {...props} />)
    expect(screen.getByRole('article').textContent).toBe(query.data)
    fireEvent.click(screen.getByRole('button', { name: 'skillDetail.instructionsSource' }))
    expect(screen.getByText(query.data).closest('pre')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'skillDetail.instructionsRendered' }))
    expect(screen.getByRole('article')).toBeTruthy()
  })
  it('reports load failure and retries the file query', () => {
    query.error = new Error('failed')
    render(<SkillInstructions {...props} />)
    expect(screen.getByRole('alert')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'skillDetail.instructionsRetry' }))
    expect(query.refetch).toHaveBeenCalledOnce()
  })
  it('retries directory failure rather than substituting README', () => {
    render(<SkillInstructions {...props} error={new Error('files failed')} />)
    fireEvent.click(screen.getByRole('button', { name: 'skillDetail.instructionsRetry' }))
    expect(props.retryFiles).toHaveBeenCalledOnce()
    expect(screen.queryByRole('article')).toBeNull()
  })
  it('distinguishes missing files from loading', () => {
    const { rerender } = render(<SkillInstructions {...props} available={false} loading />)
    expect(screen.getByRole('status').textContent).toBe('skillDetail.instructionsLoading')
    rerender(<SkillInstructions {...props} available={false} />)
    expect(screen.getByText('skillDetail.instructionsMissing')).toBeTruthy()
    expect(screen.queryByRole('article')).toBeNull()
  })
})
