import { renderToStaticMarkup } from 'react-dom/server'
import { createElement, type ReactNode } from 'react'
import { describe, expect, it, vi } from 'vitest'
import * as mod from './skill-card'
import { SkillCard } from './skill-card'

vi.mock('@/features/auth/use-auth', () => ({
  useAuth: () => ({
    isAuthenticated: false,
  }),
}))

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { language: 'en' },
  }),
}))

vi.mock('@/features/social/use-star', () => ({
  useStarredIdSet: () => ({
    starredIds: new Set<number>(),
  }),
}))

vi.mock('@/shared/ui/card', () => ({
  Card: ({ children, className }: { children?: ReactNode; className?: string }) => (
    createElement('div', { className }, children)
  ),
}))

vi.mock('@/shared/components/namespace-badge', () => ({
  NamespaceBadge: ({ name }: { name: string }) => createElement('span', null, name),
}))

/**
 * skill-card.tsx exports a single React component (SkillCard).
 * All visual logic is in JSX and depends on hooks (useAuth, useStar).
 * There are no exported pure helpers or constants to test here.
 *
 * We verify the module shape so downstream consumers break fast
 * if the export contract changes.
 */
describe('skill-card module exports', () => {
  it('exports the SkillCard component', () => {
    expect(mod.SkillCard).toBeDefined()
    expect(typeof mod.SkillCard).toBe('function')
  })

  it('limits long summaries to the stable three-line description region', () => {
    const summary = 'A long skill summary that should remain available as a tooltip while the visible card content stays clamped.'
    const html = renderToStaticMarkup(
      createElement(SkillCard, {
        skill: {
          id: 1,
          slug: 'summary-writer',
          displayName: 'Summary Writer',
          summary,
          downloadCount: 0,
          starCount: 0,
          ratingCount: 0,
          namespace: 'global',
          updatedAt: '2026-09-07T00:00:00Z',
          canSubmitPromotion: false,
        },
      })
    )

    expect(html).toContain('skill-card-summary')
    expect(html).toContain(`title="${summary}"`)
    expect(html).toContain('[overflow-wrap:anywhere]')
  })

  it('renders compliance badges from the skill summary snapshot', () => {
    const html = renderToStaticMarkup(
      createElement(SkillCard, {
        skill: {
          id: 1,
          slug: 'audit-runner',
          displayName: 'Audit Runner',
          summary: 'Runs controls',
          downloadCount: 10,
          starCount: 2,
          ratingCount: 0,
          namespace: 'global',
          updatedAt: '2026-08-07T00:00:00Z',
          canSubmitPromotion: false,
          headlineVersion: { id: 11, version: '1.0.0', status: 'PUBLISHED' },
          complianceSnapshot: {
            schemaVersion: '1.0',
            digest: 'sha256:demo',
            items: [
              { standard: 'mitre-attack', controlId: 'T1059', title: 'Command and Scripting Interpreter' },
              { standard: 'nist-csf', controlId: 'PR.AA-01' },
              { standard: 'soc2', controlId: 'CC6.1' },
            ],
          },
        },
      })
    )

    expect(html).toContain('mitre-attack')
    expect(html).toContain('T1059')
    expect(html).toContain('nist-csf')
    expect(html).toContain('+1')
  })
})
