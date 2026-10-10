import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
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
