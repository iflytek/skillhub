import { buildManifest } from '../../scripts/analytics-manifest.mjs'
import manifestText from '../../../docs/frontend-analytics-manifest.json?raw'
import { describe, expect, it } from 'vitest'


interface Site { file: string; host: string; prop: string; event: string; ids?: string[]; forward?: string }
const manifest = JSON.parse(manifestText) as {
  counts: { routes: number; uniqueIds: number; literalJsxSites: number; forwardingSites: number }
  routes: Array<{ name: string; declaration: { file: string; line: number }; implementation: { file: string; line: number } | null }>
  definitions: Array<{ ids: string[] }>
  sites: Site[]
}
const ids = new Set([...manifest.sites.flatMap(site => site.ids ?? []), ...manifest.definitions.flatMap(site => site.ids)])

describe('frontend semantic marker source contract', () => {
  it('detects marker/host/forwarding and registered-route drift using the actual TypeScript AST', () => {
    expect(buildManifest()).toEqual(manifest)
  })

  it('keeps static lowercase categories and precise, separate route/page source anchors', () => {
    expect(ids.size).toBe(manifest.counts.uniqueIds)
    expect(manifest.routes).toHaveLength(manifest.counts.routes)
    expect(new Set(manifest.routes.map(route => route.name)).size).toBe(manifest.routes.length)
    for (const id of ids) expect(id).toMatch(/^[a-z]+(?:\.[a-z]+){2,}$/)
    for (const route of manifest.routes) {
      expect(route.declaration.file).toBe('web/src/app/router.tsx')
      if (route.implementation) {
        expect(route.implementation.file).toMatch(/^web\/src\/pages\//)
        expect(route.implementation.line).toBeGreaterThan(0)
        expect(route.implementation.file).not.toBe(route.declaration.file)
      }
    }
    expect(ids.has('navigation.primary.open')).toBe(false)
    expect(['navigation.skills.open', 'navigation.suites.open', 'navigation.github.open'].every(id => ids.has(id))).toBe(true)
  })

  it('retains the original eight meanings and explicit meaningful cancellation categories', () => {
    for (const id of ['search.query.submit', 'skill.card.open', 'skill.detail.render', 'skill.download.request', 'skill.command.copy', 'skill.prompt.copy', 'skill.publish.request', 'skill.publish.confirm', 'skill.publish.cancel', 'skill.delete.cancel', 'review.reject.cancel', 'github.admission.cancel', 'suite.draft.cancel']) expect(ids.has(id)).toBe(true)
    expect(manifest.sites.filter(site => site.ids?.includes('skill.detail.render')).map(site => [site.file, site.host, site.event])).toEqual([['web/src/pages/skill-detail.tsx', 'div', 'structural']])
    expect(manifest.sites.some(site => site.host === 'ConfirmDialog' && site.prop === 'cancelAnalyticsId' && site.ids?.includes('skill.publish.cancel'))).toBe(true)
  })

  it('has no guessed business defaults in generic UI and forwards only caller-scoped props', () => {
    const primitives = manifest.sites.filter(site => site.file.startsWith('web/src/shared/ui/'))
    expect(primitives.length).toBeGreaterThan(0)
    expect(primitives.every(site => site.forward && !site.ids?.length)).toBe(true)
    for (const [file, prop] of [
      ['features/search/search-bar.tsx', 'analyticsId'],
      ['features/skill/skill-card.tsx', 'analyticsId'],
      ['shared/components/confirm-dialog.tsx', 'confirmAnalyticsId'],
      ['shared/components/confirm-dialog.tsx', 'cancelAnalyticsId'],
      ['features/skill/file-preview-dialog.tsx', 'downloadAnalyticsId'],
      ['features/skill/file-preview-dialog.tsx', 'copyAnalyticsId'],
    ]) expect(manifest.sites.some(site => site.file === 'web/src/' + file && site.forward === prop)).toBe(true)
    expect(manifest.sites.some(site => /pages\/(dashboard\/github-deep-discovery|settings\/accounts)\.tsx$/.test(site.file))).toBe(false)
  })

  it('keeps destructive and finite business branches separate without generic lifecycle fallbacks', () => {
    for (const domain of ['namespace', 'suite']) {
      const actions = domain === 'namespace' ? ['freeze', 'unfreeze', 'archive', 'restore', 'delete'] : ['hide', 'restore', 'archive', 'unarchive', 'delete']
      for (const action of actions) for (const phase of ['request', 'confirm', 'cancel']) expect(ids.has(`${domain}.${action}.${phase}`)).toBe(true)
    }
    for (const action of ['dismiss', 'resolve', 'hide', 'archive']) for (const phase of ['request', 'confirm', 'cancel']) expect(ids.has(`report.${action}.${phase}`)).toBe(true)
    for (const action of ['pause', 'resume']) for (const phase of ['request', 'confirm', 'cancel']) expect(ids.has(`github.platform.${action}.${phase}`)).toBe(true)
    for (const action of ['create', 'update']) for (const phase of ['request', 'confirm', 'cancel']) expect(ids.has(`skill.promotion.${action}.${phase}`)).toBe(true)
    for (const action of ['direct', 'request']) for (const phase of ['submit', 'cancel']) expect(ids.has(`skill.revocation.${action}.${phase}`)).toBe(true)
    for (const generic of ['namespace.lifecycle.confirm', 'suite.lifecycle.confirm', 'report.action.confirm', 'github.platform.control', 'github.credential.toggle', 'skill.promotion.confirm', 'skill.revocation.submit']) expect(ids.has(generic)).toBe(false)
    expect(manifest.sites.some(site => site.file.endsWith('/github-deep-discovery-repository-drawer.tsx'))).toBe(false)
    expect(manifest.sites.some(site => site.file.endsWith('/github-deep-discovery-pagination.tsx'))).toBe(false)
  })
})
