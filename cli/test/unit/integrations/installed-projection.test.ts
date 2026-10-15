import { expect, test } from 'bun:test'
import { mkdtemp, mkdir, rm, symlink, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { readInstalledProjection } from '../../../src/integrations/installed-projection'
import type { InventoryItem } from '../../../src/stores/inventory-store'

const registry = 'https://registry.example'
test('installed projection preserves both scopes and fixed versions without scanning other targets', async () => {
  const root = await mkdtemp(join(tmpdir(), 'skillhub-projection-'))
  try {
    const user = join(root, 'user')
    const project = join(root, 'project')
    const item: InventoryItem = { registry: registry + '/', namespace: 'demo', slug: 'writer', version: '1.2.0',
      fingerprint: 'original', targets: [user, project].map(rootDir => ({ agent: 'dsh', rootDir,
        installDir: join(rootDir, 'writer'), installedAt: '2026-10-14T00:00:00Z' })) }
    for (const target of item.targets) {
      await mkdir(join(target.installDir, '.skillhub'), { recursive: true })
      await writeFile(join(target.installDir, '.skillhub/metadata.json'), JSON.stringify({ ...item,
        agent: target.agent, installedAt: target.installedAt }))
      // No hash claim: local content may differ; presence alone is shown by this projection.
      await writeFile(join(target.installDir, 'SKILL.md'), 'locally edited')
    }
    const inventory = { read: async () => ({ items: [item,
      { ...item, registry: 'https://other.example' },
      { ...item, targets: [{ ...item.targets[0]!, rootDir: join(root, 'other-project'), installDir: '/not-read' }] },
      { ...item, targets: [{ ...item.targets[0]!, agent: 'other-agent' }] },
    ] }) }
    const targets = [{ scope: 'user' as const, root: user }, { scope: 'project' as const, root: project }]
    const result = await readInstalledProjection(inventory, registry, targets)
    expect(result.complete).toBe(true)
    expect(result.issues).toEqual([])
    expect(result.items.map(value => [value.scope, value.version, value.state])).toEqual([
      ['user', '1.2.0', 'present'], ['project', '1.2.0', 'present'],
    ])
    await rm(join(project, 'writer'), { recursive: true })
    expect((await readInstalledProjection(inventory, registry, targets)).items[1]!.state).toBe('missing')
    await symlink(join(user, 'writer'), join(project, 'writer'))
    const linked = await readInstalledProjection(inventory, registry, targets)
    expect(linked.complete).toBe(false)
    expect(linked.items[1]!.state).toBe('mismatch')
    await writeFile(join(user, 'writer/.skillhub/metadata.json'), '{')
    expect((await readInstalledProjection(inventory, registry, targets)).items[0]!.state).toBe('metadata-invalid')
  } finally { await rm(root, { recursive: true, force: true }) }
})

test('inventory failure is partial and escaping targets are reported without reading outside roots', async () => {
  const failed = await readInstalledProjection({ read: async () => { throw new Error('permission denied') } }, registry, [])
  expect(failed).toEqual({ items: [], complete: false, issues: ['inventory-unreadable'] })
  const trusted = resolve('/trusted')
  const item: InventoryItem = { registry, namespace: 'demo', slug: 'writer', version: '1', targets: [
    { agent: 'dsh', rootDir: trusted, installDir: resolve('/untrusted/secret'), installedAt: 'today' },
  ] }
  const result = await readInstalledProjection({ read: async () => ({ items: [item, item] }) }, registry,
    [{ scope: 'project', root: trusted }])
  expect(result.complete).toBe(false)
  expect(result.items).toHaveLength(1)
  expect(result.items[0]!.state).toBe('mismatch')
  expect(result.items[0]!.targetPath).toBe(join(trusted, 'writer'))
})
