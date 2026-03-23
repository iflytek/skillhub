import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it, vi } from 'vitest'

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => key,
    }),
  }
})

vi.mock('@/features/token/token-list', () => ({
  TokenList: () => <div>token-list</div>,
}))

vi.mock('@/shared/components/dashboard-page-header', () => ({
  DashboardPageHeader: ({ title, subtitle }: { title: string; subtitle: string }) => (
    <div>
      <h1>{title}</h1>
      <p>{subtitle}</p>
    </div>
  ),
}))

import { TokensPage } from './tokens'

describe('TokensPage', () => {
  it('exports a named component function', () => {
    expect(typeof TokensPage).toBe('function')
  })

  it('renders the page title and the token list component', () => {
    const html = renderToStaticMarkup(<TokensPage />)

    expect(html).toContain('tokens.pageTitle')
    expect(html).toContain('tokens.pageSubtitle')
    expect(html).toContain('token-list')
  })
})
