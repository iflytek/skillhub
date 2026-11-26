/** @vitest-environment jsdom */
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import type { ReactNode } from 'react'
import { afterEach, expect, it, vi } from 'vitest'
import { SuiteExplanation } from './suite-explanation'
import type { SkillSuite } from '@/api/types'

const fetchJson = vi.hoisted(() => vi.fn())
vi.mock('@/api/client', () => ({ fetchJson, WEB_API_PREFIX: '/api/web', getCsrfHeaders: () => ({}) }))
vi.mock('@/features/auth/use-auth', () => ({ useAuth: () => ({ user: { userId: 'reader' } }) }))
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (k: string) => k, i18n: { language: 'en' } }) }))
vi.mock('@tanstack/react-router', () => ({
  Link: ({ children, search }: { children: ReactNode; search: { version: string } }) =>
    <a href={'?version=' + search.version}>{children}</a>,
}))
afterEach(cleanup)

it('requests the selected version on click and clears the result when the version changes', async () => {
  const client = new QueryClient()
  const show = (version: string, versionId: number) => (
    <QueryClientProvider client={client}>
      <SuiteExplanation suite={{ namespace: 'global', slug: 'research', version, versionId } as SkillSuite} />
    </QueryClientProvider>
  )
  fetchJson.mockResolvedValue({ statements: [{ text: 'Read the pinned entry.', sources: [
    { namespace: 'global', slug: 'intake', version: '1.1', suite: false },
  ] }] })
  const view = render(show('1.0', 1))
  expect(fetchJson).not.toHaveBeenCalled()
  fireEvent.click(screen.getByRole('button'))
  expect(await screen.findByText('Read the pinned entry.')).toBeTruthy()
  expect(screen.getByRole('link').getAttribute('href')).toBe('?version=1.1')
  expect(fetchJson).toHaveBeenCalledWith('/api/web/suites/global/research/explanation?version=1.0',
    expect.objectContaining({ method: 'POST' }))
  view.rerender(show('2.0', 2))
  expect(screen.queryByText('Read the pinned entry.')).toBeNull()
})
