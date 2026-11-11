// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render } from '@testing-library/react'
import { PresentationDocument } from './presentation-document'
const state = vi.hoisted(() => ({ settings: { siteName: null as string | null, faviconUrl: null as string | null } }))
vi.mock('@/shared/hooks/use-site-presentation', () => ({ useSitePresentation: () => state }))
vi.mock('@/api/site-presentation', () => ({ presentationAssetUrl: (path: string | null) => path ? `/skillhub${path}` : undefined }))
vi.mock('@/shared/lib/base-path', () => ({ withBasePath: (path: string) => `/skillhub${path}` }))
afterEach(() => { cleanup(); document.head.innerHTML = ''; state.settings = { siteName: null, faviconUrl: null } })
describe('site document metadata', () => {
  it('applies brand metadata and restores defaults without retaining a custom icon MIME type', () => {
    document.head.innerHTML = '<link rel="icon" href="/favicon.svg" type="image/svg+xml">'
    state.settings = { siteName: 'Team Library', faviconUrl: '/api/v1/site-presentation/assets/logo-123' }
    const { rerender } = render(<PresentationDocument />)
    expect(document.title).toBe('Team Library')
    const icon = document.querySelector<HTMLLinkElement>('link[rel="icon"]')!
    expect(icon.getAttribute('href')).toBe('/skillhub/api/v1/site-presentation/assets/logo-123')
    expect(icon.type).toBe('')
    state.settings = { siteName: null, faviconUrl: null }
    rerender(<PresentationDocument />)
    expect(document.title).toBe('SkillHub')
    expect(icon.getAttribute('href')).toBe('/skillhub/favicon.svg')
    expect(icon.type).toBe('image/svg+xml')
  })
})
