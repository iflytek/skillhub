/** @vitest-environment jsdom */
import { StrictMode } from 'react'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterAll, afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const navigate = vi.fn()
const copy = vi.fn()
const retryVersions = vi.fn()
const retryCompare = vi.fn()
const useVersions = vi.fn()
const useCompare = vi.fn()
vi.hoisted(() => { vi.stubEnv('BASE_URL', '/skillhub/') })
let skill: ReturnType<typeof makeSkill> & { ownerPreviewVersion?: { id: number; version: string; status: string } } = makeSkill()
let showEmpty = false
let superAdmin = false
let search: { filter?: string; q?: string; namespace?: string; page?: number } = {}

function makeSkill(managementState: { ownerReadOnly: boolean; promotionRole?: string; requestedVisibility?: string } = { ownerReadOnly: false }) {
  return {
    id: 1, namespace: 'team', slug: 'demo', displayName: 'Demo', summary: 'Summary',
    status: 'ACTIVE', visibility: 'PRIVATE', downloadCount: 3, canSubmitPromotion: false,
    managementState,
    publishedVersion: { id: 11, version: '1.1.0', status: 'PUBLISHED' },
  }
}

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => navigate,
  useLocation: () => ({ pathname: '/dashboard/skills', searchStr: `?${new URLSearchParams(Object.entries(search).map(([key, value]) => [key, String(value)]))}` }),
  useSearch: () => search,
}))
vi.mock('react-i18next', async () => ({
  ...await vi.importActual<typeof import('react-i18next')>('react-i18next'),
  useTranslation: () => ({ t: (key: string) => key }),
}))
vi.mock('@/features/auth/use-auth', () => ({ useAuth: () => ({ user: { userId: 'reader' }, hasRole: () => superAdmin }) }))
vi.mock('@/shared/hooks/use-user-queries', () => ({
  useMySkills: () => ({ data: { items: showEmpty ? [] : [skill], total: showEmpty ? 0 : 1, size: 10 }, isLoading: false }),
  useSubmitPromotion: () => ({ mutateAsync: vi.fn() }),
}))
vi.mock('@/shared/hooks/use-namespace-queries', () => ({ useMyNamespaces: () => ({ data: [] }) }))
vi.mock('@/shared/hooks/use-skill-queries', () => ({
  useArchiveSkill: () => ({ mutateAsync: vi.fn() }),
  useUnarchiveSkill: () => ({ mutateAsync: vi.fn() }),
  useWithdrawSkillReview: () => ({ mutateAsync: vi.fn() }),
  useSkillVersions: (...args: unknown[]) => useVersions(...args),
  useSkillVersionCompare: (...args: unknown[]) => useCompare(...args),
}))
vi.mock('@/features/admin/use-admin-skills', () => ({ useRestoreHiddenSkill: () => ({ mutateAsync: vi.fn() }) }))
vi.mock('@/features/skill/skill-avatar-manager', () => ({ SkillAvatarManager: () => null }))
vi.mock('@/features/skill/skill-visibility-dialog', () => ({ SkillVisibilityDialog: () => null }))
vi.mock('@/shared/lib/clipboard', () => ({ copyToClipboard: (...args: unknown[]) => copy(...args) }))
vi.mock('@/shared/lib/toast', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))

import { MySkillsPage } from './my-skills'

async function openMenu() {
  const trigger = screen.getByRole('button', { name: 'mySkills.moreActionsFor' })
  trigger.focus()
  fireEvent.keyDown(trigger, { key: 'ArrowDown' })
  await screen.findByRole('menu')
  return trigger
}

beforeEach(() => {
  skill = makeSkill()
  showEmpty = false
  search = {}
  superAdmin = false
  navigate.mockReset()
  copy.mockReset()
  retryVersions.mockReset()
  retryCompare.mockReset()
  useVersions.mockReturnValue({ data: [
    { id: 11, version: '1.1.0', status: 'PUBLISHED' },
    { id: 10, version: '1.0.0', status: 'PUBLISHED' },
    { id: 12, version: '1.2.0', status: 'PENDING_REVIEW' },
  ], isLoading: false, refetch: retryVersions })
  useCompare.mockReturnValue({ data: { from: '1.0.0', to: '1.1.0', summary: { totalFiles: 0, addedLines: 0, removedLines: 0 }, files: [] }, isLoading: false, refetch: retryCompare })
  HTMLElement.prototype.scrollIntoView = vi.fn()
  vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => setTimeout(() => callback(0), 0))
  vi.stubGlobal('cancelAnimationFrame', clearTimeout)
  vi.spyOn(window, 'scrollTo').mockImplementation(() => {})
})
afterEach(() => { cleanup(); vi.restoreAllMocks(); vi.unstubAllGlobals() })
afterAll(() => vi.unstubAllEnvs())

describe('My Skills interactions with real menu and dialog', () => {
  it('preserves long descriptions in the card and full-text title without slicing the source text', () => {
    const summary = '用于汇总跨服务任务的输入、执行条件和人工复核结果。'.repeat(20) + 'UnbrokenIdentifier'.repeat(15)
    skill.summary = summary
    render(<MySkillsPage />)
    const description = screen.getByText(summary)
    expect(description.getAttribute('title')).toBe(summary)
    expect(screen.getByRole('link', { name: 'Demo' })).toBeTruthy()
  })

  it('keeps ownership, promotion and requested visibility explanations in one supplemental group', () => {
    skill = makeSkill({ ownerReadOnly: true, promotionRole: 'GLOBAL_COPY', requestedVisibility: 'PUBLIC' })
    render(<MySkillsPage />)
    const readonly = screen.getByText('mySkills.ownerReadOnly')
    const promotion = screen.getByText('mySkills.promotionRoles.GLOBAL_COPY')
    const visibility = screen.getByText('skillVisibility.pendingList')
    expect(readonly.parentElement).toBe(promotion.parentElement)
    expect(readonly.parentElement).toBe(visibility.parentElement)
    expect(readonly.getAttribute('role')).toBe('status')
    expect(visibility.getAttribute('role')).toBe('status')
    expect(screen.queryByRole('button', { name: 'mySkills.update' })).toBeNull()
  })

  it('loads comparison on demand and returns focus after Escape without navigating', async () => {
    render(<MySkillsPage />)
    expect(useVersions).not.toHaveBeenCalled()
    const trigger = await openMenu()
    fireEvent.click(screen.getByRole('menuitem', { name: 'mySkills.compareVersions' }))
    await screen.findByRole('dialog', { name: 'mySkills.compareVersions' })
    expect(useVersions).toHaveBeenCalledWith('team', 'demo')
    expect(useCompare).toHaveBeenLastCalledWith('team', 'demo', '1.0.0', '1.1.0', true)
    screen.getByRole('button', { name: 'mySkills.update', hidden: true }).focus()
    expect(screen.getByRole('dialog').contains(document.activeElement)).toBe(true)
    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' })
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
    expect(document.activeElement).toBe(trigger)
    expect(navigate).not.toHaveBeenCalled()
  })

  it('copies the actual skill coordinate without opening details', async () => {
    render(<MySkillsPage />)
    await openMenu()
    fireEvent.click(screen.getByRole('menuitem', { name: 'mySkills.copyCoordinate' }))
    expect(copy).toHaveBeenCalledWith('@team/demo')
    expect(navigate).not.toHaveBeenCalled()
  })

  it('keeps owner write operations absent after leaving the namespace', async () => {
    skill.managementState.ownerReadOnly = true
    render(<MySkillsPage />)
    expect(screen.queryByRole('button', { name: 'mySkills.update' })).toBeNull()
    await openMenu()
    for (const name of ['mySkills.archive', 'skillVisibility.title', 'mySkills.withdrawReview', 'mySkills.promoteToGlobal']) {
      expect(screen.queryByRole('menuitem', { name })).toBeNull()
    }
    expect(screen.getByRole('menuitem', { name: 'mySkills.viewDetail' })).toBeTruthy()
  })

  it('preserves the separate hidden-governance restore action', async () => {
    search = { filter: 'HIDDEN' }
    superAdmin = true
    render(<MySkillsPage />)
    await openMenu()
    expect(screen.getByRole('menuitem', { name: 'mySkills.restoreHidden' })).toBeTruthy()
    expect(screen.queryByRole('menuitem', { name: 'mySkills.viewDetail' })).toBeNull()
    expect(screen.queryByRole('menuitem', { name: 'mySkills.archive' })).toBeNull()
    expect(screen.queryByRole('button', { name: 'mySkills.update' })).toBeNull()
  })

  it('only navigates to the existing upload-update flow when Update is clicked', () => {
    render(<MySkillsPage />)
    fireEvent.click(screen.getByRole('button', { name: 'mySkills.update' }))
    expect(navigate).toHaveBeenCalledTimes(1)
    expect(navigate).toHaveBeenCalledWith({ to: '/dashboard/publish', search: { namespace: 'team', updateSkill: 'demo', updateName: 'Demo', visibility: 'PRIVATE' } })
  })

  it('restores scroll after detail navigation under the same URL and identity', async () => {
    Object.defineProperty(window, 'scrollY', { configurable: true, value: 450 })
    search = { filter: 'PUBLISHED', page: 2 }
    const first = render(<MySkillsPage />)
    fireEvent.click(screen.getByRole('link', { name: 'Demo' }))
    expect(navigate.mock.calls[0][0].search.returnTo).toBe('/dashboard/skills?filter=PUBLISHED&page=2')
    first.unmount()
    render(<StrictMode><MySkillsPage /></StrictMode>)
    await waitFor(() => expect(window.scrollTo).toHaveBeenCalledWith({ top: 450, behavior: 'instant' }))
  })

  it('shows a retryable version-load failure instead of a false not-enough-versions result', async () => {
    useVersions.mockReturnValue({ isLoading: false, error: new Error('offline'), refetch: retryVersions })
    render(<MySkillsPage />)
    await openMenu()
    fireEvent.click(screen.getByRole('menuitem', { name: 'mySkills.compareVersions' }))
    await screen.findByRole('alert')
    expect(screen.queryByText('skillCompare.notEnoughPublishedVersions')).toBeNull()
    fireEvent.click(screen.getByRole('button', { name: 'mySkills.retry' }))
    expect(retryVersions).toHaveBeenCalledOnce()
  })

  it('does not compare when fewer than two published versions exist', async () => {
    useVersions.mockReturnValue({ data: [
      { id: 1, version: '1.0.0', status: 'PUBLISHED' },
      { id: 2, version: '2.0.0', status: 'PENDING_REVIEW' },
    ], isLoading: false, refetch: retryVersions })
    render(<MySkillsPage />)
    await openMenu()
    fireEvent.click(screen.getByRole('menuitem', { name: 'mySkills.compareVersions' }))
    await screen.findByText('skillCompare.notEnoughPublishedVersions')
    expect(useCompare).toHaveBeenLastCalledWith('team', 'demo', '', '', false)
  })

  it('keeps comparison errors retryable without closing or navigating', async () => {
    useCompare.mockReturnValue({ isLoading: false, error: new Error('offline'), refetch: retryCompare })
    render(<MySkillsPage />)
    await openMenu()
    fireEvent.click(screen.getByRole('menuitem', { name: 'mySkills.compareVersions' }))
    await screen.findByRole('alert')
    fireEvent.click(screen.getByRole('button', { name: 'mySkills.retry' }))
    expect(retryCompare).toHaveBeenCalledOnce()
    expect(screen.getByRole('dialog')).toBeTruthy()
    expect(navigate).not.toHaveBeenCalled()
  })

  it('preserves the loading and empty-files outcomes in the comparison dialog', async () => {
    useVersions.mockReturnValueOnce({ isLoading: true })
    render(<MySkillsPage />)
    await openMenu()
    fireEvent.click(screen.getByRole('menuitem', { name: 'mySkills.compareVersions' }))
    await screen.findByText('skillCompare.loading')
  })


  it.each(['ordinary', 'ctrlKey', 'metaKey'] as const)('retains deployment prefix and return context when opening with %s', (modifier) => {
    search = { filter: 'PUBLISHED', q: 'a skill', namespace: 'team', page: 2 }
    skill.ownerPreviewVersion = { id: 12, version: '1.2.0', status: 'PENDING_REVIEW' }
    render(<MySkillsPage />)
    const link = screen.getByRole('link', { name: 'Demo' })
    const href = new URL(link.getAttribute('href') ?? '', 'https://example.test')
    expect(href.pathname).toBe('/skillhub/space/team/demo')
    expect(href.searchParams.get('returnTo')).toBe('/dashboard/skills?filter=PUBLISHED&q=a+skill&namespace=team&page=2')
    expect(href.searchParams.get('version')).toBe('1.2.0')
    // Prevent jsdom's unsupported browser navigation after checking the native href.
    link.addEventListener('click', event => event.preventDefault(), { once: true })
    fireEvent.click(link, modifier === 'ordinary' ? {} : { [modifier]: true })
    if (modifier === 'ordinary') {
      expect(navigate).toHaveBeenCalledWith({
        to: '/space/team/demo',
        search: { returnTo: href.searchParams.get('returnTo'), version: '1.2.0' },
      })
    } else expect(navigate).not.toHaveBeenCalled()
  })

  it('clears all four URL conditions from the empty-state action', () => {
    search = { filter: 'PUBLISHED', q: 'missing', namespace: 'team', page: 2 }
    showEmpty = true
    render(<MySkillsPage />)
    const emptyStateClear = screen.getAllByRole('button', { name: 'mySkills.clearSearch' }).at(-1)
    if (!emptyStateClear) throw new Error('Missing empty-state clear action')
    fireEvent.click(emptyStateClear)
    expect(navigate).toHaveBeenCalledOnce()
    expect(navigate.mock.calls[0][0].search(search)).toEqual({ filter: undefined, q: undefined, namespace: undefined, page: 0 })
  })

  it.each([
    ['archive', 'archiveConfirmTitle'],
    ['unarchive', 'unarchiveConfirmTitle'],
    ['withdrawReview', 'withdrawConfirmTitle'],
    ['promoteToGlobal', 'promotionConfirmTitle'],
    ['restoreHidden', 'restoreHiddenConfirmTitle'],
  ])('restores the more trigger after Escape and Cancel on %s', async (action, title) => {
    if (action === 'unarchive') skill.status = 'ARCHIVED'
    if (action === 'withdrawReview') skill.ownerPreviewVersion = { id: 12, version: '1.2.0', status: 'PENDING_REVIEW' }
    if (action === 'promoteToGlobal') skill.canSubmitPromotion = true
    if (action === 'restoreHidden') { search = { filter: 'HIDDEN' }; superAdmin = true }
    render(<MySkillsPage />)
    for (const closeViaEscape of [true, false]) {
      const trigger = await openMenu()
      fireEvent.click(screen.getByRole('menuitem', { name: `mySkills.${action}` }))
      const dialog = await screen.findByRole('dialog', { name: `mySkills.${title}` })
      if (closeViaEscape) fireEvent.keyDown(dialog, { key: 'Escape' })
      else fireEvent.click(screen.getByRole('button', { name: 'dialog.cancel' }))
      await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
      await waitFor(() => expect(document.activeElement).toBe(trigger))
      expect(navigate).not.toHaveBeenCalled()
    }
  })


  it('returns focus to search when query results remove the row before confirmation closes', async () => {
    const view = render(<MySkillsPage />)
    await openMenu()
    fireEvent.click(screen.getByRole('menuitem', { name: 'mySkills.archive' }))
    const dialog = await screen.findByRole('dialog', { name: 'mySkills.archiveConfirmTitle' })
    showEmpty = true
    view.rerender(<MySkillsPage />)
    fireEvent.keyDown(dialog, { key: 'Escape' })
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
    await waitFor(() => expect(document.activeElement).toBe(screen.getByRole('searchbox')))
  })

  it.each(['retained', 'removed', 'user-moved'] as const)('keeps focus deliberate after successful archive with row %s', async (result) => {
    const view = render(<MySkillsPage />)
    const trigger = await openMenu()
    fireEvent.click(screen.getByRole('menuitem', { name: 'mySkills.archive' }))
    await screen.findByRole('dialog', { name: 'mySkills.archiveConfirmTitle' })
    fireEvent.click(screen.getByRole('button', { name: 'mySkills.archive' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
    await waitFor(() => expect(document.activeElement).toBe(trigger))
    const otherControl = screen.getByRole('button', { name: 'nav.search' })
    if (result === 'user-moved') otherControl.focus()
    // Change the hook's query-result fixture after the close, then rerender the page.
    // React removes the source row; no DOM node is manually deleted.
    if (result !== 'retained') showEmpty = true
    view.rerender(<MySkillsPage />)
    await waitFor(() => expect(document.activeElement).toBe(
      result === 'retained' ? trigger : result === 'user-moved' ? otherControl : screen.getByRole('searchbox')
    ))
    expect(document.activeElement?.isConnected).toBe(true)
  })


  it('opens the existing prefilled update form from the mobile menu action', async () => {
    render(<MySkillsPage />)
    await openMenu()
    fireEvent.click(screen.getByRole('menuitem', { name: 'mySkills.update' }))
    expect(navigate).toHaveBeenCalledOnce()
    expect(navigate).toHaveBeenCalledWith({
      to: '/dashboard/publish',
      search: { namespace: 'team', updateSkill: 'demo', updateName: 'Demo', visibility: 'PRIVATE' },
    })
  })

})
