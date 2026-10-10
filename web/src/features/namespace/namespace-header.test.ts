import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it, vi } from 'vitest'
import type { Namespace } from '@/api/types'
import { NamespaceHeader } from './namespace-header'
import * as mod from './namespace-header'

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => key,
      i18n: { language: 'en' },
    }),
  }
})

describe('namespace-header module exports', () => {
  it('exports the NamespaceHeader component', () => {
    expect(mod.NamespaceHeader).toBeDefined()
    expect(typeof mod.NamespaceHeader).toBe('function')
  })
})

describe('NamespaceHeader', () => {
  const baseNamespace: Namespace = {
    id: 1,
    slug: 'skillhub',
    displayName: 'SkillHub',
    type: 'GLOBAL',
    status: 'ACTIVE',
    avatarUrl: 'https://example.com/avatar.png',
    description: 'Shared namespace for all skills',
    createdAt: '2026-03-23T00:00:00.000Z',
  }

  const renderHeader = (namespace: Namespace) =>
    renderToStaticMarkup(createElement(NamespaceHeader, { namespace }))

  it('renders the GLOBAL namespace header with avatar, description, slug and immutable hint', () => {
    const html = renderHeader(baseNamespace)

    expect(html).toContain('SkillHub')
    expect(html).toContain('img')
    expect(html).toContain('src="https://example.com/avatar.png"')
    expect(html).toContain('alt="SkillHub"')
    expect(html).toContain('Shared namespace for all skills')
    expect(html).toContain('@skillhub')
    expect(html).toContain('myNamespaces.typeGlobal')
    expect(html).toContain('namespaceStatus.active')
    expect(html).toContain('namespaceStatus.immutableHint')
    expect(html).toContain('bg-emerald-500/10 text-emerald-500 border-emerald-500/20')
    expect(html).not.toContain('namespaceStatus.frozenHint')
    expect(html).not.toContain('namespaceStatus.archivedHint')
  })

  it('renders the TEAM namespace header with frozen status and hint', () => {
    const html = renderHeader({
      ...baseNamespace,
      type: 'TEAM',
      status: 'FROZEN',
      avatarUrl: undefined,
      description: undefined,
      slug: 'team-space',
      displayName: 'Team Space',
    })

    expect(html).toContain('Team Space')
    expect(html).not.toContain('<img')
    expect(html).not.toContain('Shared namespace for all skills')
    expect(html).toContain('@team-space')
    expect(html).toContain('myNamespaces.typeTeam')
    expect(html).toContain('namespaceStatus.frozen')
    expect(html).toContain('namespaceStatus.frozenHint')
    expect(html).toContain('bg-amber-500/10 text-amber-500 border-amber-500/20')
    expect(html).not.toContain('namespaceStatus.immutableHint')
    expect(html).not.toContain('namespaceStatus.archivedHint')
  })

  it('renders the TEAM namespace header with archived status and hint', () => {
    const html = renderHeader({
      ...baseNamespace,
      type: 'TEAM',
      status: 'ARCHIVED',
      slug: 'team-archive',
      displayName: 'Team Archive',
      avatarUrl: undefined,
      description: 'Archived workspace for old projects',
    })

    expect(html).toContain('Team Archive')
    expect(html).toContain('Archived workspace for old projects')
    expect(html).toContain('@team-archive')
    expect(html).toContain('myNamespaces.typeTeam')
    expect(html).toContain('namespaceStatus.archived')
    expect(html).toContain('namespaceStatus.archivedHint')
    expect(html).toContain('bg-slate-500/10 text-slate-500 border-slate-500/20')
    expect(html).not.toContain('namespaceStatus.immutableHint')
    expect(html).not.toContain('namespaceStatus.frozenHint')
  })
})
