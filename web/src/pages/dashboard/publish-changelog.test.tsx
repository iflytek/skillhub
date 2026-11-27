/** @vitest-environment jsdom */
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const preview = vi.hoisted(() => vi.fn())
const readOverview = vi.hoisted(() => vi.fn())
const publish = vi.hoisted(() => vi.fn())
const searchParams = vi.hoisted(() => ({ value: { namespace: 'team-ai', updateSkill: 'stable-slug' as string | undefined } }))

vi.mock('@/features/publish/folder-zip', () => ({ packageFolderAsZip: vi.fn() }))
vi.mock('@/features/publish/package-overview', () => ({ readPackageOverview: readOverview }))
vi.mock('@tanstack/react-router', () => ({ useNavigate: () => vi.fn(), useSearch: () => searchParams.value }))
vi.mock('react-i18next', async () => ({
  ...await vi.importActual<typeof import('react-i18next')>('react-i18next'),
  useTranslation: () => ({ t: (key: string) => key }),
}))
vi.mock('@/shared/hooks/use-skill-queries', () => ({
  useSkillDetail: () => ({ data: { publishedVersion: { version: '1.0.0' }, ownerReadOnly: false } }),
  useSkillVersionDetail: () => ({ data: { version: '1.0.0', parsedMetadataJson: '{}' }, isLoading: false }),
  previewSkillPackage: preview,
  usePublishSkill: () => ({ mutateAsync: publish, isPending: false }),
}))
vi.mock('@/shared/hooks/use-namespace-queries', () => ({ useMyNamespaces: () => ({ data: [{ id: 1, slug: 'team-ai', displayName: 'Team' }], isLoading: false }) }))
vi.mock('@/features/publish/upload-zone', () => ({
  UploadZone: ({ onFileSelect }: { onFileSelect: (file: File) => void }) =>
    <input aria-label="package" type="file" onChange={e => { if (e.target.files?.[0]) onFileSelect(e.target.files[0]) }} />,
}))
vi.mock('@/features/skill/skill-avatar-editor', () => ({ KEEP_AVATAR: { action: 'KEEP', ready: true }, SkillAvatarEditor: () => null }))
vi.mock('@/features/skill/skill-avatar-manager', () => ({ SkillAvatarManager: () => null }))
vi.mock('@/api/client', async (importOriginal) => ({
  ...await importOriginal<typeof import('@/api/client')>(),
  skillAvatarApi: { status: vi.fn().mockResolvedValue({ action: 'KEEP' }) },
}))
vi.mock('@/shared/lib/navigate-after-overlays', () => ({ navigateAfterOverlays: (action: () => void) => action() }))
vi.mock('@/shared/lib/toast', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))
vi.mock('@/features/publish/presentation-editor', () => ({ PresentationEditor: () => null }))

import { PublishPage } from './publish'

const metadata = { name: 'stable-slug', displayName: 'Stable', version: '1.2.0', description: 'summary' }

function mount() {
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><PublishPage /></QueryClientProvider>)
}

beforeEach(() => {
  readOverview.mockReset()
  readOverview.mockResolvedValue({ content: '# docs', source: 'skill' })
  preview.mockReset()
  preview.mockResolvedValue({ ...metadata, shortDescription: 'Package short', overview: '# Package overview' })
  publish.mockReset()
  publish.mockResolvedValue({ namespace: 'team-ai', slug: 'stable-slug', version: '1.2.0', status: 'PENDING_REVIEW' })
  searchParams.value = { namespace: 'team-ai', updateSkill: 'stable-slug' }
})
afterEach(cleanup)

describe('publish release notes', () => {
  it('shows the field when updating and sends what the publisher typed', async () => {
    mount()
    fireEvent.change(screen.getByLabelText('package'), { target: { files: [new File(['zip'], 'stable.zip')] } })

    const field = await screen.findByLabelText('publish.changelog')
    fireEvent.change(field, { target: { value: '  Fixed the parser and added a test  ' } })
    fireEvent.click(screen.getByRole('button', { name: 'publish.nextConfirm' }))
    const confirmation = screen.queryByRole('dialog', { name: 'publish.confirmDetails' })
    if (confirmation) {
      fireEvent.click(Array.from(confirmation.querySelectorAll('button')).find(b => b.textContent === 'publish.confirm')!)
    }

    await waitFor(() => expect(publish).toHaveBeenCalled())
    expect(publish.mock.calls[0][0].changelog).toBe('Fixed the parser and added a test')
  })

  it('does not show the field on a first publish', async () => {
    searchParams.value = { namespace: 'team-ai', updateSkill: undefined }
    mount()
    fireEvent.change(screen.getByLabelText('package'), { target: { files: [new File(['zip'], 'stable.zip')] } })
    await waitFor(() => expect(preview).toHaveBeenCalled())
    expect(screen.queryByLabelText('publish.changelog')).toBeNull()
  })
})
