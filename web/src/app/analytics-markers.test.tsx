/** @vitest-environment jsdom */
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { SearchBar } from '@/features/search/search-bar'
import { SkillCard } from '@/features/skill/skill-card'
import { InstallCommand } from '@/features/skill/install-command'
import { InstallForAgentButton } from '@/features/skill/install-for-agent-button'
import { ConfirmDialog } from '@/shared/components/confirm-dialog'
import { Pagination } from '@/shared/components/pagination'
import { UploadZone } from '@/features/publish/upload-zone'
import type { SkillSummary } from '@/api/types'

const mocks = vi.hoisted(() => ({ copy: vi.fn(), language: 'en' }))
vi.mock('react-i18next', async () => ({ ...await vi.importActual<typeof import('react-i18next')>('react-i18next'), useTranslation: () => ({ t: (key: string) => key, i18n: { language: mocks.language } }) }))
vi.mock('@/features/auth/use-auth', () => ({ useAuth: () => ({ isAuthenticated: false }) }))
vi.mock('@/features/social/use-star', () => ({ useStarredIdSet: () => ({ starredIds: new Set() }) }))
vi.mock('@/shared/lib/clipboard', () => ({ useCopyToClipboard: () => [false, mocks.copy] }))

const skill = { id: 1, namespace: 'fixture', slug: 'private-fixture', displayName: 'Private fixture', starCount: 0, downloadCount: 0, ratingCount: 0 } as SkillSummary
const attr = (element: Element) => element.getAttribute('data-analytics-id')
afterEach(() => { cleanup(); vi.clearAllMocks(); mocks.language = 'en' })

describe('passive, caller-scoped analytics attributes', () => {
  it('omits disabled dropzone intents without changing dropzone or native folder guards', () => {
    const select = vi.fn()
    const view = render(<UploadZone analyticsId="skill.package.select" folderAnalyticsId="skill.package.folder.select" onFileSelect={select} onFolderSelect={vi.fn()} />)
    const drop = view.container.querySelector('.upload-zone')!
    expect(attr(drop)).toBe('skill.package.select')
    view.rerender(<UploadZone analyticsId="skill.package.select" folderAnalyticsId="skill.package.folder.select" onFileSelect={select} onFolderSelect={vi.fn()} disabled />)
    expect(attr(drop)).toBeNull()
    const file = view.container.querySelector('.upload-zone input')!
    expect(attr(file)).toBeNull()
    expect(view.container.querySelector('[data-analytics-id="skill.package.folder.select"]')?.hasAttribute('disabled')).toBe(true)
    fireEvent.change(file, { target: { files: [new File(['synthetic'], 'synthetic.zip', { type: 'application/zip' })] } })
    fireEvent.click(drop)
    expect(select).not.toHaveBeenCalled()
    view.rerender(<UploadZone onFileSelect={select} />)
    expect(view.container.querySelector('[data-analytics-id]')).toBeNull()
  })

  it('marks only opted-in search forms and keeps typing/clear separate from submit', () => {
    const search = vi.fn()
    const view = render(<SearchBar analyticsId="search.query.submit" onSearch={search} />)
    const form = screen.getByRole('textbox').closest('form')!
    expect(attr(form)).toBe('search.query.submit')
    expect(attr(screen.getByRole('button', { name: 'searchBar.button' }))).toBeNull()
    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'not-an-analytics-field' } })
    fireEvent.click(screen.getByRole('button', { name: 'searchBar.clear' }))
    expect(search).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: 'searchBar.button' }))
    expect(search).toHaveBeenCalledTimes(1)
    fireEvent.submit(form)
    expect(search).toHaveBeenCalledTimes(2)
    view.rerender(<SearchBar onSearch={search} />)
    expect(attr(form)).toBeNull()
    view.rerender(<SearchBar analyticsId="search.query.submit" />)
    expect(attr(form)).toBeNull()
  })

  it('retains card click/Enter/Space handlers and omits inactive/unopted markers', () => {
    const open = vi.fn()
    const view = render(<SkillCard skill={skill} onClick={open} analyticsId="skill.card.open" />)
    const card = screen.getByRole('link')
    expect(attr(card)).toBe('skill.card.open')
    fireEvent.click(card)
    fireEvent.keyDown(card, { key: 'Enter' })
    fireEvent.keyDown(card, { key: ' ' })
    fireEvent.keyDown(card, { key: 'Escape' })
    expect(open).toHaveBeenCalledTimes(3)
    view.rerender(<SkillCard skill={skill} analyticsId="skill.card.open" />)
    expect(view.container.querySelector('[data-analytics-id]')).toBeNull()
    view.rerender(<SkillCard skill={skill} onClick={open} />)
    expect(view.container.querySelector('[data-analytics-id]')).toBeNull()
  })

  it.each(['en', 'zh'])('marks copy intents in %s without resource/command/prompt values', async language => {
    mocks.language = language
    mocks.copy.mockResolvedValue(undefined)
    render(<InstallCommand namespace="fixture" slug="private-fixture" version="1.0" analyticsId="skill.command.copy" skillhubAnalyticsId="skill.command.skillhub.select" clawhubAnalyticsId="skill.command.clawhub.select" />)
    expect(attr(screen.getByRole('tab', { name: 'skillDetail.installMethodSkillhub' }))).toBe('skill.command.skillhub.select')
    expect(attr(screen.getByRole('tab', { name: 'skillDetail.installMethodClawhub' }))).toBe('skill.command.clawhub.select')
    const copy = screen.getByRole('button', { name: 'copyButton.copy' })
    expect(attr(copy)).toBe('skill.command.copy')
    fireEvent.click(copy)
    await waitFor(() => expect(mocks.copy).toHaveBeenCalledTimes(1))
    fireEvent.click(screen.getByRole('tab', { name: 'skillDetail.installMethodClawhub' }))
    expect(attr(screen.getByRole('button', { name: 'copyButton.copy' }))).toBe('skill.command.copy')
    fireEvent.click(screen.getByRole('button', { name: 'copyButton.copy' }))
    await waitFor(() => expect(mocks.copy).toHaveBeenCalledTimes(2))
    cleanup()
    render(<InstallForAgentButton namespace="fixture" slug="private-fixture" version="1.0" analyticsId="skill.prompt.copy" />)
    const prompt = screen.getByTestId('install-for-agent-button')
    expect(attr(prompt)).toBe('skill.prompt.copy')
    fireEvent.click(prompt)
    await waitFor(() => expect(mocks.copy).toHaveBeenCalledTimes(3))
    for (const node of document.querySelectorAll('[data-analytics-id]')) {
      expect(attr(node)).not.toMatch(/fixture|1\.0|https|npx/)
    }
  })

  it('keeps disabled and unsafe-version prompt copy disabled', () => {
    const view = render(<InstallForAgentButton namespace="fixture" slug="fixture" version="unsafe version" analyticsId="skill.prompt.copy" />)
    const prompt = screen.getByTestId('install-for-agent-button')
    expect(prompt.hasAttribute('disabled')).toBe(true)
    fireEvent.click(prompt)
    expect(mocks.copy).not.toHaveBeenCalled()
    view.rerender(<InstallForAgentButton namespace="fixture" slug="fixture" version="1.0" disabled analyticsId="skill.prompt.copy" />)
    fireEvent.click(prompt)
    expect(mocks.copy).not.toHaveBeenCalled()
    view.rerender(<InstallForAgentButton namespace="fixture" slug="fixture" version="1.0" />)
    expect(attr(prompt)).toBeNull()
  })

  it('does not turn clipboard rejection into success or another activation', async () => {
    mocks.copy.mockRejectedValue(new Error('synthetic clipboard failure'))
    const error = vi.spyOn(console, 'error').mockImplementation(() => {})
    render(<InstallCommand namespace="fixture" slug="fixture" analyticsId="skill.command.copy" />)
    fireEvent.click(screen.getByRole('button', { name: 'copyButton.copy' }))
    await waitFor(() => expect(error).toHaveBeenCalledTimes(1))
    expect(mocks.copy).toHaveBeenCalledTimes(1)
    expect(attr(screen.getByRole('button', { name: 'copyButton.copy' }))).toBe('skill.command.copy')
    error.mockRestore()
  })

  it('forwards confirmation only to the requested confirm button and retains pending guard', async () => {
    let finish!: () => void
    const confirm = vi.fn(() => new Promise<void>(resolve => { finish = resolve }))
    const close = vi.fn()
    const view = render(<ConfirmDialog open onOpenChange={close} title="Fixture warning" confirmText="continue" cancelText="cancel" onConfirm={confirm} confirmAnalyticsId="skill.publish.confirm" />)
    const button = screen.getByRole('button', { name: 'continue' })
    expect(attr(button)).toBe('skill.publish.confirm')
    expect(attr(screen.getByRole('button', { name: 'cancel' }))).toBeNull()
    fireEvent.click(button)
    fireEvent.click(button)
    expect(confirm).toHaveBeenCalledTimes(1)
    expect(button.hasAttribute('disabled')).toBe(true)
    finish()
    await waitFor(() => expect(close).toHaveBeenCalledWith(false))
    view.rerender(<ConfirmDialog open onOpenChange={close} title="Other dialog" confirmText="continue" onConfirm={confirm} />)
    expect(attr(screen.getByRole('button', { name: 'continue' }))).toBeNull()
  })

  it('pagination forwards intent to native controls, not its structural nav or disabled activation', () => {
    const change = vi.fn()
    const view = render(<Pagination page={0} totalPages={3} onPageChange={change} analyticsId="search.results.paginate" />)
    expect(attr(screen.getByRole('navigation'))).toBeNull()
    fireEvent.click(screen.getByRole('button', { name: 'pagination.prev' }))
    expect(change).not.toHaveBeenCalled()
    const next = screen.getByRole('button', { name: 'pagination.next' })
    expect(attr(next)).toBe('search.results.paginate')
    fireEvent.click(next)
    expect(change).toHaveBeenCalledExactlyOnceWith(1)
    view.rerender(<Pagination page={0} totalPages={3} onPageChange={change} />)
    expect(view.container.querySelector('[data-analytics-id]')).toBeNull()
  })

  it('opts destructive/configuration cancellation in per caller, including the close button', () => {
    const close = vi.fn()
    const confirm = vi.fn()
    const view = render(<ConfirmDialog open onOpenChange={close} title="Delete fixture" confirmText="delete" cancelText="cancel" onConfirm={confirm} confirmAnalyticsId="skill.delete.confirm" cancelAnalyticsId="skill.delete.cancel" />)
    expect(attr(screen.getByRole('button', { name: 'delete' }))).toBe('skill.delete.confirm')
    expect(attr(screen.getByRole('button', { name: 'cancel' }))).toBe('skill.delete.cancel')
    const dismiss = screen.getByRole('button', { name: 'Close' })
    expect(attr(dismiss)).toBe('skill.delete.cancel')
    fireEvent.click(dismiss)
    expect(close).toHaveBeenCalledExactlyOnceWith(false)
    expect(confirm).not.toHaveBeenCalled()
    view.rerender(<ConfirmDialog open onOpenChange={close} title="Other configuration" confirmText="save" cancelText="cancel" onConfirm={confirm} cancelAnalyticsId="github.admission.cancel" />)
    expect(attr(screen.getByRole('button', { name: 'cancel' }))).toBe('github.admission.cancel')
    expect(attr(screen.getByRole('button', { name: 'save' }))).toBeNull()
    view.rerender(<ConfirmDialog open onOpenChange={close} title="Uninstrumented" onConfirm={confirm} />)
    expect(document.querySelector('[data-analytics-id]')).toBeNull()
  })
})
