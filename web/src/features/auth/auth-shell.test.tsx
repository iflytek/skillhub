vi.mock('@/shared/hooks/use-site-presentation', async (original) => {
  const actual = await original<typeof import('@/shared/hooks/use-site-presentation')>()
  return { ...actual, useSitePresentation: () => ({ settings: actual.defaultSitePresentation() }) }
})
import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it, vi } from 'vitest'
vi.mock('react-i18next', async (original) => ({ ...await original<typeof import('react-i18next')>(), useTranslation: () => ({ t: (key: string) => key }) }))

import { AuthShell } from './auth-shell'

describe('AuthShell', () => {
  it('renders theme-aware product content beside the form', () => {
    const html = renderToStaticMarkup(<AuthShell><div>Login form</div></AuthShell>)

    expect(html).toContain('dark:bg-slate-950')
    expect(html).toContain('sticky top-0 hidden h-svh')
    expect(html).toContain('authShell.heroTitle')
    expect(html).toContain('authShell.shortTagline')
    expect(html).toContain('login-skill-art-light.png')
    expect(html).toContain('login-skill-art-dark.png')
    expect(html).toContain('authShell.backHome')
    expect(html).toContain('xl:hidden')
    expect(html).toContain('pt-20')
    expect(html).toContain('Login form')
  })
})
