/** @vitest-environment jsdom */
import { cleanup, render, screen } from '@testing-library/react'
import type { ReactNode } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { CrossSourceTwinNotice } from './cross-source-twin-notice'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, options?: { namespace?: string }) =>
      options?.namespace ? `${key}:${options.namespace}` : key,
  }),
}))

vi.mock('@tanstack/react-router', () => ({
  Link: ({ children, params, to, ...rest }: {
    children: ReactNode
    params: { namespace: string; slug: string }
    to: string
    [key: string]: unknown
  }) => <a href={`${to}/${params.namespace}/${params.slug}`} {...rest}>{children}</a>,
}))

afterEach(() => cleanup())

describe('CrossSourceTwinNotice', () => {
  it('names the source and links to the twin', () => {
    render(<CrossSourceTwinNotice twin={{ namespace: 'github', slug: 'brainstorming', displayName: 'Brainstorming', source: 'github' }} />)

    expect(screen.getByRole('note').textContent).toContain('skillDetail.crossSourceTwinGithub:github')
    const link = screen.getByRole('link', { name: /crossSourceTwinOpen/ })
    expect(link.getAttribute('href')).toBe('/space/$namespace/$slug/github/brainstorming')
  })

  it('names the other namespace so the reader knows where to look', () => {
    render(<CrossSourceTwinNotice twin={{ namespace: 'software', slug: 'brainstorming', displayName: 'Brainstorming', source: 'platform' }} />)
    // The message interpolates the namespace instead of saying "inside the platform".
    expect(screen.getByRole('note').textContent).toContain('skillDetail.crossSourceTwinPlatform:software')
  })
})
