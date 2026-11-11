/** @vitest-environment jsdom */
import { fireEvent, render, screen, waitFor, cleanup, within, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const preview = vi.hoisted(() => vi.fn())
const readOverview = vi.hoisted(() => vi.fn())
const zipFolder = vi.hoisted(() => vi.fn())
vi.mock('@/features/publish/folder-zip', () => ({ packageFolderAsZip: zipFolder }))
vi.mock('@/features/publish/package-overview', () => ({ readPackageOverview: readOverview }))
const snapshots = vi.hoisted(() => ({ values: {} as Record<string, {version:string;parsedMetadataJson:string} | undefined> }))
const publish = vi.hoisted(() => vi.fn())
const permissions = vi.hoisted(() => ({ ownerReadOnly: false }))
const searchParams = vi.hoisted(() => ({ value: { namespace: 'team-ai', updateSkill: undefined as string | undefined } }))
vi.mock('@tanstack/react-router', () => ({ useNavigate: () => vi.fn(), useSearch: () => searchParams.value }))
vi.mock('react-i18next', async () => ({ ...await vi.importActual<typeof import('react-i18next')>('react-i18next'), useTranslation: () => ({ t: (key: string) => key }) }))
vi.mock('@/shared/hooks/use-skill-queries', () => ({ useSkillDetail: (_namespace: string, slug: string) => ({ data: slug ? {publishedVersion:{version:'1.0.0'},ownerReadOnly:permissions.ownerReadOnly} : undefined }), useSkillVersionDetail: (namespace: string, slug: string) => ({ data: snapshots.values[`${namespace}/${slug}`] ?? snapshots.values[slug], isLoading: false }), previewSkillPackage: preview, usePublishSkill: () => ({ mutateAsync: publish, isPending: false }) }))
vi.mock('@/shared/hooks/use-namespace-queries', () => ({ useMyNamespaces: () => ({ data: [{ id: 1, slug: 'team-ai', displayName: 'Team' }], isLoading: false }) }))
vi.mock('@/features/publish/upload-zone', () => ({ UploadZone: ({ onFileSelect, onFolderSelect, previewError }: { onFileSelect: (file: File) => void; onFolderSelect: (files: File[]) => void; previewError?: string }) => <><button onClick={() => onFolderSelect([new File(['folder'], 'SKILL.md')])}>select-folder</button><input aria-label="package" type="file" onChange={e => { if (e.target.files?.[0]) onFileSelect(e.target.files[0]) }} />{previewError && <p role="alert">{previewError}</p>}</> }))
vi.mock('@/features/skill/skill-avatar-editor', () => ({ KEEP_AVATAR: { action: 'KEEP', ready: true }, SkillAvatarEditor: () => null }))
vi.mock('@/features/skill/skill-avatar-manager', () => ({ SkillAvatarManager: () => null }))
vi.mock('@/api/client', async (importOriginal) => ({ ...await importOriginal<typeof import('@/api/client')>(), skillAvatarApi: { status: vi.fn().mockResolvedValue({ action: 'KEEP' }) } }))
vi.mock('@/shared/lib/navigate-after-overlays', () => ({ navigateAfterOverlays: (action: () => void) => action() }))
vi.mock('@/shared/lib/toast', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))
function submitPackage() {
  fireEvent.click(screen.getByRole('button', { name: 'publish.nextConfirm' }))
  const confirmation = screen.queryByRole('dialog', { name: 'publish.confirmDetails' })
  if (confirmation) fireEvent.click(Array.from(confirmation.querySelectorAll('button')).find(button => button.textContent === 'publish.confirm')!)
}
vi.mock('@/features/publish/presentation-editor', () => ({ PresentationEditor: ({ open, value, onApply, onCancel }: { open: boolean; value: string; onApply: (value: string) => void; onCancel: () => void }) => open ? <div><textarea aria-label="overview-draft" defaultValue={value} /><button onClick={() => onApply((document.querySelector('[aria-label="overview-draft"]') as HTMLTextAreaElement).value)}>apply-overview</button><button onClick={onCancel}>cancel-overview</button></div> : null }))
import { PublishPage } from './publish'

function restoreField(index: number) {
  fireEvent.click(screen.getAllByRole('button', { name: 'publish.form.restore' })[index])
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'publish.form.restore' }))
}

function mount() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={client}><PublishPage /></QueryClientProvider>)
  return client
}
function select(name: string) {
  fireEvent.change(screen.getByLabelText('package'), { target: { files: [new File(['zip'], name)] } })
}
const metadata = (name: string) => ({ name: 'stable-slug', displayName: name, version: '1.0.0', description: 'summary' })

afterEach(cleanup)
beforeEach(() => { permissions.ownerReadOnly = false; searchParams.value.namespace = 'team-ai'; zipFolder.mockReset(); snapshots.values = {}; readOverview.mockReset(); readOverview.mockResolvedValue({ content: '# Default package documentation', source: 'skill' }); searchParams.value.updateSkill = undefined; preview.mockReset(); publish.mockReset(); publish.mockResolvedValue({ namespace: 'team-ai', slug: 'stable-slug', version: '1.0.0', status: 'PENDING_REVIEW' }) })

describe('web presentation publishing', () => {
  it('prefills package presentation without sending unedited defaults', async () => {
    preview.mockResolvedValue({ ...metadata('Package title'), shortDescription: 'Package short', overview: '# Package overview' })
    mount(); select('presentation.zip')
    await waitFor(() => expect((screen.getByLabelText('publish.shortDescription') as HTMLInputElement).value).toBe('Package short'))
    expect(screen.getByRole('heading', { name: 'Package overview' })).toBeTruthy()
    expect(screen.queryByRole('textbox', { name: 'publish.overview' })).toBeNull()
    submitPackage()
    await waitFor(() => expect(publish).toHaveBeenCalledWith(expect.objectContaining({
      shortDescription: undefined, overview: undefined, resetPresentation: true,
    })))
  })

  it('keeps the form overview read-only and applies no changes when editing is cancelled', async () => {
    preview.mockResolvedValue({ ...metadata('Title'), overview: '# Original overview' })
    mount(); select('presentation.zip')
    await screen.findByRole('heading', { name: 'Original overview' })
    expect(screen.queryByRole('textbox', { name: 'publish.overview' })).toBeNull()
    fireEvent.click(screen.getByRole('button', { name: 'publish.editOverview' }))
    fireEvent.change(screen.getByLabelText('overview-draft'), { target: { value: '# Discard this' } })
    fireEvent.click(screen.getByText('cancel-overview'))
    expect(screen.getByRole('heading', { name: 'Original overview' })).toBeTruthy()
    expect(publish).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: 'publish.nextConfirm' }))
    expect(publish).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: 'publish.backToEdit' }))
    expect(publish).not.toHaveBeenCalled()
  })

  it('blocks over-limit presentation data returned by package preview', async () => {
    preview.mockResolvedValue({ ...metadata('Title'), shortDescription: 'x'.repeat(201), overview: 'x'.repeat(20001) })
    mount(); select('long.zip')
    await waitFor(() => expect((screen.getByLabelText('publish.shortDescription') as HTMLTextAreaElement).value.length).toBe(201))
    fireEvent.click(screen.getByRole('button', { name: 'publish.nextConfirm' })); expect(publish).not.toHaveBeenCalled()
    expect(publish).not.toHaveBeenCalled()
  })

  it('reads README asynchronously and does not submit an unchanged fallback as an override', async () => {
    let finish!: (value: { content: string; source: string }) => void
    readOverview.mockReturnValue(new Promise(resolve => { finish = resolve }))
    preview.mockResolvedValue(metadata('Title'))
    mount(); select('fallback.zip')
    await screen.findByText('publish.readingOverview')
    expect(screen.getByRole('button', { name: 'publish.editOverview' }).hasAttribute('disabled')).toBe(true)
    finish({ content: '# README introduction', source: 'readme' })
    await screen.findByRole('heading', { name: 'README introduction' })
    submitPackage()
    await waitFor(() => expect(publish).toHaveBeenCalledWith(expect.objectContaining({ overview: undefined })))
  })

  it('requires custom content when fallback preview is unavailable', async () => {
    readOverview.mockResolvedValue({ content: '', source: null, error: 'tooLarge' })
    preview.mockResolvedValue(metadata('Title'))
    mount(); select('preview-failure.zip')
    await screen.findByText('publish.overviewPreviewUnavailable')
    expect(screen.queryByText('publish.emptyOverview')).toBeNull()
    submitPackage()
    expect(publish).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: 'publish.editOverview' }))
    fireEvent.change(screen.getByLabelText('overview-draft'), { target: { value: '# Custom explanation' } })
    fireEvent.click(screen.getByText('apply-overview'))
    submitPackage()
    await waitFor(() => expect(publish).toHaveBeenCalledWith(expect.objectContaining({ overview: '# Custom explanation' })))
  })

  it('places the next action after namespace and visibility settings in document order', async () => {
    preview.mockResolvedValue(metadata('Title'))
    mount(); select('package.zip')
    await waitFor(() => expect(screen.getByRole('button', { name: 'publish.nextConfirm' }).hasAttribute('disabled')).toBe(false))
    const next = screen.getByRole('button', { name: 'publish.nextConfirm' })
    for (const id of ['namespace', 'visibility']) {
      const control = document.getElementById(id)!
      expect(Boolean(control.compareDocumentPosition(next) & Node.DOCUMENT_POSITION_FOLLOWING)).toBe(true)
    }
  })

  it('sends edited presentation and reset intent independently of form defaults', async () => {
    preview.mockResolvedValue({ ...metadata('Package title'), shortDescription: 'Package short', overview: '# Package overview' })
    mount(); select('presentation.zip')
    await waitFor(() => expect((screen.getByLabelText('publish.shortDescription') as HTMLInputElement).value).toBe('Package short'))
    fireEvent.change(screen.getByLabelText('publish.shortDescription'), { target: { value: 'Web short' } })
    fireEvent.click(screen.getByRole('button', { name: 'publish.editOverview' }))
    fireEvent.change(screen.getByLabelText('overview-draft'), { target: { value: '# Web overview' } })
    fireEvent.click(screen.getByText('apply-overview'))
    submitPackage()
    await waitFor(() => expect(publish).toHaveBeenCalledWith(expect.objectContaining({
      shortDescription: 'Web short', overview: '# Web overview', resetPresentation: false,
    })))
  })
  it('restores only the confirmed field without re-sending package defaults', async () => {
    preview.mockResolvedValue({ ...metadata('Package title'), shortDescription: 'Package short', overview: '# Package overview' })
    mount(); select('presentation.zip')
    await screen.findByRole('heading', { name: 'Package overview' })
    fireEvent.change(screen.getByLabelText('publish.shortDescription'), { target: { value: 'Discard me' } })
    restoreField(0)
    expect((screen.getByLabelText('publish.shortDescription') as HTMLTextAreaElement).value).toBe('Package short')
    submitPackage()
    await waitFor(() => expect(publish).toHaveBeenCalledWith(expect.objectContaining({ shortDescription: undefined, overview: undefined, resetPresentation: true })))
  })

})

describe('web display name publishing', () => {
  it('prefills from the package and sends the explicit edited name with the original file', async () => {
    preview.mockResolvedValue(metadata('包内名称'))
    mount(); select('one.zip')
    await waitFor(() => expect((screen.getByLabelText('publish.displayName') as HTMLInputElement).value).toBe('包内名称'))
    fireEvent.change(screen.getByLabelText('publish.displayName'), { target: { value: '网页名称' } })
    submitPackage()
    await waitFor(() => expect(publish).toHaveBeenCalledWith(expect.objectContaining({ displayName: '网页名称', file: expect.objectContaining({ name: 'one.zip' }) })))
  })

  it('keeps user input when a replacement package finishes parsing', async () => {
    preview.mockResolvedValue(metadata('初始建议'))
    mount(); select('one.zip')
    await waitFor(() => expect((screen.getByLabelText('publish.displayName') as HTMLInputElement).value).toBe('初始建议'))
    fireEvent.change(screen.getByLabelText('publish.displayName'), { target: { value: '我填写的名称' } })
    preview.mockResolvedValue(metadata('另一个建议')); select('two.zip')
    await waitFor(() => expect(preview).toHaveBeenCalledTimes(2))
    expect((screen.getByLabelText('publish.displayName') as HTMLInputElement).value).toBe('我填写的名称')
  })

  it('ignores a stale package response and blocks submit until the current preview succeeds', async () => {
    let finishOld!: (value: ReturnType<typeof metadata>) => void
    preview.mockReturnValueOnce(new Promise(resolve => { finishOld = resolve })).mockResolvedValueOnce(metadata('当前包名称'))
    mount(); select('old.zip')
    fireEvent.click(screen.getByRole('button', { name: 'publish.nextConfirm' })); expect(publish).not.toHaveBeenCalled()
    await waitFor(() => expect(preview).toHaveBeenCalledTimes(1))
    select('new.zip')
    await waitFor(() => expect((screen.getByLabelText('publish.displayName') as HTMLInputElement).value).toBe('当前包名称'))
    finishOld(metadata('过期包名称'))
    await new Promise(resolve => setTimeout(resolve, 20))
    expect((screen.getByLabelText('publish.displayName') as HTMLInputElement).value).toBe('当前包名称')
  })

  it('shows a retry and cannot publish a package with invalid metadata', async () => {
    preview.mockRejectedValueOnce(new Error('invalid package')).mockResolvedValueOnce(metadata('重试后的名称'))
    mount(); select('invalid.zip')
    await screen.findByRole('alert')
    fireEvent.click(screen.getByRole('button', { name: 'publish.nextConfirm' })); expect(publish).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: 'publish.retryPackagePreview' }))
    await waitFor(() => expect((screen.getByLabelText('publish.displayName') as HTMLInputElement).value).toBe('重试后的名称'))
  })
})


describe('examples preflight remains non-blocking', () => {
  for (const status of ['ready', 'none', 'invalid'] as const) {
    it(`shows ${status} recognition and still allows publishing`, async () => {
      preview.mockResolvedValue({ ...metadata('案例技能'), examplesStatus: status, ...(status === 'invalid' ? {} : { examplesCount: status === 'ready' ? 2 : 0 }) })
      mount(); select('examples.zip')
      await screen.findByText(`publish.examples${status[0].toUpperCase()}${status.slice(1)}`)
      await waitFor(() => expect(screen.getByRole('button', { name: 'publish.nextConfirm' }).hasAttribute('disabled')).toBe(false))
    })
  }
  it('tolerates old preview responses without examples fields', async () => {
    preview.mockResolvedValue(metadata('旧技能'))
    mount(); select('old.zip')
    await waitFor(() => expect((screen.getByLabelText('publish.displayName') as HTMLInputElement).value).toBe('旧技能'))
    expect(screen.queryByText(/publish.examples/)).toBeNull()
    await waitFor(() => expect(screen.getByRole('button', { name: 'publish.nextConfirm' }).hasAttribute('disabled')).toBe(false))
  })
})

describe('implicit presentation editing and resource isolation', () => {
  it.each([['package','package'],['package','custom'],['custom','package'],['custom','custom']] as const)('submits %s summary with %s overview', async (short, overview) => {
    preview.mockResolvedValue({...metadata('Title'),shortDescription:'Package summary',overview:'# Package overview'})
    mount(); select('choices.zip')
    await screen.findByRole('heading',{name:'Package overview'})
    if (short === 'custom') fireEvent.change(screen.getByLabelText('publish.shortDescription'),{target:{value:'Edited summary'}})
    if (overview === 'custom') {
      fireEvent.click(screen.getByRole('button',{name:'publish.editOverview'}))
      fireEvent.change(screen.getByLabelText('overview-draft'),{target:{value:'# Edited overview'}})
      fireEvent.click(screen.getByText('apply-overview'))
    }
    submitPackage()
    await waitFor(() => expect(publish).toHaveBeenCalledWith(expect.objectContaining({shortDescription:short==='custom'?'Edited summary':undefined,overview:overview==='custom'?'# Edited overview':undefined,resetPresentation:short==='package'&&overview==='package'})))
  })

  it('opening and applying the unchanged package overview does not create an override', async () => {
    preview.mockResolvedValue({...metadata('Title'),overview:'# Package overview'})
    mount(); select('unchanged.zip')
    await screen.findByRole('heading',{name:'Package overview'})
    fireEvent.click(screen.getByRole('button',{name:'publish.editOverview'}))
    fireEvent.click(screen.getByText('apply-overview'))
    expect(screen.queryByRole('button',{name:'publish.form.restore'})).toBeNull()
    submitPackage()
    await waitFor(() => expect(publish).toHaveBeenCalledWith(expect.objectContaining({overview:undefined,resetPresentation:true})))
  })

  it('resets custom drafts when switching to another cached published resource', async () => {
    snapshots.values.a={version:'1.0.0',parsedMetadataJson:JSON.stringify({shortDescriptionOverride:'A summary',overviewOverride:'# A overview'})}
    snapshots.values.b={version:'1.0.0',parsedMetadataJson:JSON.stringify({shortDescriptionOverride:'B summary',overviewOverride:'# B overview'})}
    searchParams.value.updateSkill='a'
    preview.mockResolvedValue({...metadata('Title'),overview:'# Package overview'})
    const client=new QueryClient({defaultOptions:{queries:{retry:false}}})
    const view=render(<QueryClientProvider client={client}><PublishPage /></QueryClientProvider>)
    await waitFor(() => expect((screen.getByLabelText('publish.shortDescription') as HTMLTextAreaElement).value).toBe('A summary'))
    fireEvent.change(screen.getByLabelText('publish.shortDescription'),{target:{value:'A edited draft'}})
    searchParams.value.updateSkill='b'
    view.rerender(<QueryClientProvider client={client}><PublishPage /></QueryClientProvider>)
    await waitFor(() => expect((screen.getByLabelText('publish.shortDescription') as HTMLTextAreaElement).value).toBe('B summary'))
    expect(screen.queryByDisplayValue('A edited draft')).toBeNull()
  })

  it('does not replace explicit user edits when the published snapshot arrives late', async () => {
    searchParams.value.updateSkill='late'
    preview.mockResolvedValue({...metadata('Title'),shortDescription:'Package summary',overview:'# Package overview'})
    const client=new QueryClient({defaultOptions:{queries:{retry:false}}})
    const view=render(<QueryClientProvider client={client}><PublishPage /></QueryClientProvider>)
    select('late.zip')
    await screen.findByRole('heading',{name:'Package overview'})
    fireEvent.change(screen.getByLabelText('publish.shortDescription'),{target:{value:'My draft'}})
    snapshots.values.late={version:'1.0.0',parsedMetadataJson:JSON.stringify({shortDescriptionOverride:'Late custom',overviewOverride:'# Late overview'})}
    view.rerender(<QueryClientProvider client={client}><PublishPage /></QueryClientProvider>)
    expect((screen.getByLabelText('publish.shortDescription') as HTMLTextAreaElement).value).toBe('My draft')
  })

  it('cancels restoration without losing drafts, then follows the replacement package after confirmed restore', async () => {
    searchParams.value.updateSkill='draft'
    snapshots.values.draft={version:'1.0.0',parsedMetadataJson:JSON.stringify({shortDescriptionOverride:'Saved custom',overviewOverride:'# Saved overview'})}
    preview.mockResolvedValue({...metadata('Title'),shortDescription:'Package summary',overview:'# Package overview'})
    mount(); select('draft.zip')
    await waitFor(() => expect((screen.getByLabelText('publish.shortDescription') as HTMLTextAreaElement).value).toBe('Saved custom'))
    fireEvent.change(screen.getByLabelText('publish.shortDescription'),{target:{value:'Edited custom'}})
    fireEvent.click(screen.getAllByRole('button',{name:'publish.form.restore'})[0])
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button',{name:'dialog.cancel'}))
    expect((screen.getByLabelText('publish.shortDescription') as HTMLTextAreaElement).value).toBe('Edited custom')
    restoreField(0)
    expect((screen.getByLabelText('publish.shortDescription') as HTMLTextAreaElement).value).toBe('Package summary')
    expect(screen.getByRole('heading',{name:'Saved overview'})).toBeTruthy()
    preview.mockResolvedValue({...metadata('Next'),shortDescription:'New package summary',overview:'# New package overview'})
    select('next.zip')
    await waitFor(() => expect((screen.getByLabelText('publish.shortDescription') as HTMLTextAreaElement).value).toBe('New package summary'))
    expect(screen.getByRole('heading',{name:'Saved overview'})).toBeTruthy()
  })
})


describe('folder packaging resource boundary', () => {
  it('ignores a package produced after changing the update target', async () => {
    let finish!: (value: File) => void
    zipFolder.mockReturnValue(new Promise(resolve => { finish = resolve }))
    searchParams.value.updateSkill = 'old'
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const view = render(<QueryClientProvider client={client}><PublishPage /></QueryClientProvider>)
    fireEvent.click(screen.getByText('select-folder'))
    await waitFor(() => expect(zipFolder).toHaveBeenCalledTimes(1))
    searchParams.value.updateSkill = 'new'
    view.rerender(<QueryClientProvider client={client}><PublishPage /></QueryClientProvider>)
    finish(new File(['zip'], 'old-resource.zip'))
    await new Promise(resolve => setTimeout(resolve, 20))
    expect(preview).not.toHaveBeenCalled()
    expect((screen.getByLabelText('publish.displayName') as HTMLInputElement).value).toBe('')
  })

  it('shows a packaging failure beside the upload field without clearing custom introduction', async () => {
    preview.mockResolvedValue({ ...metadata('Title'), overview: '# Package overview' })
    zipFolder.mockRejectedValue(new Error('bad folder'))
    mount(); select('valid.zip')
    await screen.findByRole('heading', { name: 'Package overview' })
    fireEvent.change(screen.getByLabelText('publish.shortDescription'), { target: { value: 'Preserved draft' } })
    fireEvent.click(screen.getByText('select-folder'))
    await screen.findByText('publish.folderPackagingFailed')
    expect((screen.getByLabelText('publish.shortDescription') as HTMLTextAreaElement).value).toBe('Preserved draft')
    expect(preview).toHaveBeenCalledTimes(1)
  })
})


describe('cached and in-flight package resource identity', () => {
  it('hydrates the new namespace when the previous namespace has a same-slug cached snapshot', async () => {
    snapshots.values['ns-a/old'] = { version: '1.0.0', parsedMetadataJson: JSON.stringify({ shortDescriptionOverride: 'A old summary', overviewOverride: '# A old overview' }) }
    snapshots.values['ns-a/shared'] = { version: '1.0.0', parsedMetadataJson: JSON.stringify({ shortDescriptionOverride: 'Wrong namespace summary', overviewOverride: '# Wrong namespace overview' }) }
    snapshots.values['ns-b/shared'] = { version: '1.0.0', parsedMetadataJson: JSON.stringify({ shortDescriptionOverride: 'B shared summary', overviewOverride: '# B shared overview' }) }
    searchParams.value.namespace = 'ns-a'
    searchParams.value.updateSkill = 'old'
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const view = render(<QueryClientProvider client={client}><PublishPage /></QueryClientProvider>)
    await waitFor(() => expect((screen.getByLabelText('publish.shortDescription') as HTMLTextAreaElement).value).toBe('A old summary'))
    searchParams.value.namespace = 'ns-b'
    searchParams.value.updateSkill = 'shared'
    view.rerender(<QueryClientProvider client={client}><PublishPage /></QueryClientProvider>)
    await waitFor(() => expect((screen.getByLabelText('publish.shortDescription') as HTMLTextAreaElement).value).toBe('B shared summary'))
    expect(screen.getByRole('heading', { name: 'B shared overview' })).toBeTruthy()
    expect(screen.queryByRole('heading', { name: 'Wrong namespace overview' })).toBeNull()
    preview.mockResolvedValue({ ...metadata('B package title'), overview: '# B package overview' })
    select('b-package.zip')
    await waitFor(() => expect((screen.getByLabelText('publish.displayName') as HTMLInputElement).value).toBe('B package title'))
    submitPackage()
    await waitFor(() => expect(publish).toHaveBeenCalledWith(expect.objectContaining({ namespace: 'ns-b', shortDescription: 'B shared summary', overview: '# B shared overview' })))
  })

  it('does not fill a new update target with a late response from the previous package', async () => {
    let finish!: (value: ReturnType<typeof metadata>) => void
    preview.mockReturnValue(new Promise(resolve => { finish = resolve }))
    searchParams.value.updateSkill = 'old'
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const view = render(<QueryClientProvider client={client}><PublishPage /></QueryClientProvider>)
    select('old-pending.zip')
    await waitFor(() => expect(preview).toHaveBeenCalledTimes(1))
    searchParams.value.updateSkill = 'new'
    view.rerender(<QueryClientProvider client={client}><PublishPage /></QueryClientProvider>)
    await act(async () => { finish(metadata('Late old package title')) })
    expect((screen.getByLabelText('publish.displayName') as HTMLInputElement).value).toBe('')
    expect((screen.getByLabelText('publish.shortDescription') as HTMLTextAreaElement).value).toBe('')
    expect(screen.queryByText('Late old package title')).toBeNull()
    expect(publish).not.toHaveBeenCalled()
  })
})


describe('restore confirmation focus and resource lifecycle', () => {
  it('returns cancel focus to the restore action and confirm focus to the summary', async () => {
    preview.mockResolvedValue({ ...metadata('Title'), overview: '# Package overview' })
    mount(); select('focus.zip')
    await screen.findByRole('heading', { name: 'Package overview' })
    fireEvent.change(screen.getByLabelText('publish.shortDescription'), { target: { value: 'Draft summary' } })
    const trigger = screen.getByRole('button', { name: 'publish.form.restore' })
    fireEvent.click(trigger)
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'dialog.cancel' }))
    await waitFor(() => expect(document.activeElement).toBe(trigger))
    expect((screen.getByLabelText('publish.shortDescription') as HTMLTextAreaElement).value).toBe('Draft summary')
    restoreField(0)
    await waitFor(() => expect(document.activeElement).toBe(screen.getByLabelText('publish.shortDescription')))
  })

  it('returns confirmed overview restoration focus to its fixed label', async () => {
    preview.mockResolvedValue({ ...metadata('Title'), overview: '# Package overview' })
    mount(); select('overview-focus.zip')
    await screen.findByRole('heading', { name: 'Package overview' })
    fireEvent.click(screen.getByRole('button', { name: 'publish.editOverview' }))
    fireEvent.change(screen.getByLabelText('overview-draft'), { target: { value: '# Custom overview' } })
    fireEvent.click(screen.getByText('apply-overview'))
    restoreField(0)
    await waitFor(() => expect(document.activeElement).toBe(document.getElementById('skill-overview-label')))
    expect(screen.getByRole('heading', { name: 'Package overview' })).toBeTruthy()
  })

  it.each(['readonly', 'route'])('closes restoration when %s changes', async mode => {
    searchParams.value.updateSkill = 'before'
    preview.mockResolvedValue({ ...metadata('Title'), overview: '# Package overview' })
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const view = render(<QueryClientProvider client={client}><PublishPage /></QueryClientProvider>)
    select('closing.zip')
    await screen.findByRole('heading', { name: 'Package overview' })
    fireEvent.change(screen.getByLabelText('publish.shortDescription'), { target: { value: 'Before draft' } })
    fireEvent.click(screen.getByRole('button', { name: 'publish.form.restore' }))
    expect(screen.getByRole('dialog')).toBeTruthy()
    if (mode === 'readonly') permissions.ownerReadOnly = true
    else searchParams.value.updateSkill = 'after'
    view.rerender(<QueryClientProvider client={client}><PublishPage /></QueryClientProvider>)
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
    expect(publish).not.toHaveBeenCalled()
  })
})
