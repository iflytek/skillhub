import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it, vi } from 'vitest'
import { ResourceCard } from './resource-card'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}))

vi.mock('@/shared/components/namespace-badge', () => ({
  NamespaceBadge: ({ name }: { name: string }) => <span>{name}</span>,
}))

describe('ResourceCard', () => {
  it('renders an explicit Suite type independently from a same-slug Skill', () => {
    const html = renderToStaticMarkup(
      <ResourceCard
        resource={{
          resourceType: 'SUITE',
          detailUrl: '/suite/team-ai/starter',
          id: 10,
          namespace: 'team-ai',
          slug: 'starter',
          displayName: 'Starter Suite',
          version: '2.0.0',
          visibility: 'PUBLIC',
          installCount: 3,
          available: true,
          updatedAt: '2026-09-07T10:00:00Z',
        }}
        onClick={() => undefined}
      />,
    )

    expect(html).toContain('suite.resourceTypeSuite')
    expect(html).toContain('Starter Suite')
    expect(html).toContain('@team-ai')
    expect(html).toContain('v2.0.0')
  })

  it('shows the computed unavailable state', () => {
    const html = renderToStaticMarkup(
      <ResourceCard
        resource={{
          resourceType: 'SUITE',
          detailUrl: '/suite/team-ai/degraded',
          id: 11,
          namespace: 'team-ai',
          slug: 'degraded',
          displayName: 'Degraded Suite',
          version: '1.0.0',
          visibility: 'PUBLIC',
          installCount: 0,
          available: false,
          updatedAt: '2026-09-07T10:00:00Z',
        }}
        onClick={() => undefined}
      />,
    )

    expect(html).toContain('suite.unavailable')
  })
})
