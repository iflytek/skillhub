// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { PublishResult } from '@/api/types'
import { PublishResultPanel } from './publish-result-panel'
import type { PublishSubmissionState } from './publish-result'

const translation = vi.hoisted(() => ({ t: vi.fn((key: string, parameters?: Record<string, unknown>) => key === 'publish.result.submitting.description' ? `${parameters?.skill} ${parameters?.version}` : key) }))
vi.mock('react-i18next', async (importOriginal) => ({ ...await importOriginal<typeof import('react-i18next')>(), useTranslation: () => ({ t: translation.t }) }))
afterEach(cleanup)
const result: PublishResult = { skillId: 12, namespace: 'private-team', slug: 'fixture', version: '1.0.0', status: 'SCANNING', fileCount: 2, totalSize: 100 }
function setup(state: PublishSubmissionState) {
  const callbacks = { onReturnToForm: vi.fn(), onReplacePackage: vi.fn(), onViewMySkills: vi.fn(), onViewVersion: vi.fn() }
  const view = render(<PublishResultPanel state={state} {...callbacks} />)
  return { ...callbacks, ...view }
}

describe('one submission result at a time', () => {
  it('presents a busy state with no active resubmit control', () => {
    setup({ kind: 'submitting', label: 'fixture 1.0.0' })
    expect(screen.getByRole('status').getAttribute('aria-busy')).toBe('true')
    expect(screen.getByRole('button').hasAttribute('disabled')).toBe(true)
  })
  it('interpolates both the skill and selected package version while submitting', () => {
    setup({ kind: 'submitting', label: 'Meeting notes', version: '2.3.4' })
    expect(screen.getByText('Meeting notes 2.3.4')).toBeDefined()
    expect(translation.t).toHaveBeenCalledWith('publish.result.submitting.description', { skill: 'Meeting notes', version: '2.3.4' })
  })
  it('supplies an empty version fallback instead of leaving an interpolation token', () => {
    setup({ kind: 'submitting', label: 'Meeting notes' })
    expect(translation.t).toHaveBeenCalledWith('publish.result.submitting.description', { skill: 'Meeting notes', version: '' })
  })
  it.each(['SCANNING', 'PENDING_REVIEW', 'UPLOADED', 'PUBLISHED', 'SCAN_FAILED', 'REJECTED', 'YANKED', 'DRAFT', 'unrecognized'])('presents %s truthfully with a read/navigation callback', status => {
    const phase = status === 'unrecognized' ? 'FUTURE' : status
    const receipt = { ...result, status: phase }
    const test = setup({ kind: 'accepted', result: receipt, hadPublishedVersion: true })
    expect(screen.getByRole('heading').textContent).toBe(`publish.result.accepted.${status}.title`)
    expect(screen.queryByText('publish.result.previousPublished') !== null).toBe(status !== 'PUBLISHED')
    fireEvent.click(screen.getByRole('button', { name: 'publish.result.viewVersion' }))
    expect(test.onViewVersion).toHaveBeenCalledWith(receipt)
    expect(test.onReturnToForm).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: 'publish.result.mySkills' }))
    expect(test.onViewMySkills).toHaveBeenCalledOnce()
  })
  it('returns a version conflict to package replacement without changing the result', () => {
    const state: PublishSubmissionState = { kind: 'version-conflict', version: '1.0.0' }
    const test = setup(state)
    expect(screen.getByRole('alert')).toBeDefined()
    fireEvent.click(screen.getByRole('button', { name: 'publish.result.replacePackage' }))
    expect(test.onReplacePackage).toHaveBeenCalledOnce()
    expect(state).toEqual({ kind: 'version-conflict', version: '1.0.0' })
  })
  it('offers verification for an unknown outcome rather than an automatic retry', () => {
    const test = setup({ kind: 'unknown' })
    fireEvent.click(screen.getByRole('button', { name: 'publish.result.checkMySkills' }))
    expect(test.onViewMySkills).toHaveBeenCalledOnce()
    expect(screen.queryByRole('button', { name: /retry/i })).toBeNull()
    fireEvent.click(screen.getByRole('button', { name: 'publish.result.returnToForm' }))
    expect(test.onReturnToForm).toHaveBeenCalledOnce()
  })
  it('replaces a prior pending state instead of rendering multiple result cards', () => {
    const test = setup({ kind: 'submitting', label: 'fixture' })
    test.rerender(<PublishResultPanel state={{ kind: 'accepted', result }} {...test} />)
    expect(screen.getAllByRole('heading')).toHaveLength(1)
    expect(screen.queryByText('publish.result.submitting.title')).toBeNull()
  })
})
