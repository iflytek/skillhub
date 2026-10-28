import { randomUUID } from 'node:crypto'
import { mkdir, readFile, rm, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { OperationStore } from '../../src/integrations/operation-store'
import { openNativeRuntime } from '../../src/integrations/native-runtime'
import { InventoryStore } from '../../src/stores/inventory-store'
import { snapshotSkillDirectory } from '../../src/services/skill-fingerprint'

const [root, boundary, mode] = process.argv.slice(2)
if (!root || !boundary || !['uninstall', 'stale', 'recover'].includes(mode ?? '')) throw new Error('Missing removal crash arguments')
async function stop(at: string): Promise<void> {
  if (boundary !== at) return
  process.kill(process.pid, 'SIGKILL')
  await new Promise(() => {})
}
const append = OperationStore.prototype.recordRemoval
OperationStore.prototype.recordRemoval = async function (...args) {
  const event = args[3]
  if (mode === 'recover') {
    if (event.type === 'restore' && event.completed) await stop('after-restore')
    if (event.type === 'cleanup') await stop('after-recovery-cleanup')
  } else {
    if (event.type === 'intent') await stop('before-intent')
    if (event.type === 'isolated') await stop('after-rename')
    if (event.type === 'committed') await stop('after-commit')
    if (event.type === 'cleanup') await stop('after-cleanup')
  }
  const result = await append.apply(this, args)
  if (mode === 'recover') {
    if (event.type === 'restore' && !event.completed) await stop('before-restore')
  } else {
    if (event.type === 'intent') await stop('intent')
    if (event.type === 'isolated') await stop('isolated')
    if (event.type === 'beforeCommit') await stop('before-commit')
    if (event.type === 'committed') await stop('committed')
  }
  return result
}
const runtime = await openNativeRuntime({ stateDirectory: join(root, 'operations'), inventoryHome: join(root, 'home') })
if (mode === 'recover') {
  const fixture = JSON.parse(await readFile(join(root, 'fixture.json'), 'utf8')) as { operationId: string; ownerId: string; path: string }
  await runtime.recover(fixture.operationId, fixture.ownerId, [fixture.path], async () => {})
} else {
  const identity = { registry: 'https://registry.example', namespace: 'demo', slug: 'writer', version: '1.0.0' }
  const content = '# Removal crash fixture'
  const targets = []
  for (const scope of ['selected', 'retained']) {
    const rootDir = join(root, scope, 'skills')
    const installDir = join(rootDir, identity.slug)
    await mkdir(join(installDir, '.skillhub'), { recursive: true })
    await writeFile(join(installDir, 'SKILL.md'), content)
    const snapshot = await snapshotSkillDirectory(installDir)
    const target = { installationState: 'enabled' as const, version: identity.version, fingerprint: snapshot.fingerprint, agent: 'dsh', rootDir, installDir, installedAt: '2026-10-14' }
    targets.push(target)
    await writeFile(join(installDir, '.skillhub/metadata.json'), JSON.stringify({ ...identity, ...snapshot,
      agent: 'dsh', installedAt: target.installedAt }))
  }
  const inventory = new InventoryStore(join(root, 'home'))
  const before = { items: [{ registry: identity.registry, namespace: identity.namespace, slug: identity.slug, targets }] }
  await inventory.writeAtomic(before)
  const selected = targets[0]!
  if (mode === 'stale') await rm(selected.installDir, { recursive: true })
  const baseline = await runtime.inspectRemoval(identity, { scope: 'project', root: selected.rootDir })
  if (baseline.status !== 'ready') throw new Error('Invalid removal crash fixture')
  const accepted = (await runtime.acceptRemoval({ hostInstanceId: runtime.hostInstanceId, ownerId: 'session',
    requestKey: randomUUID(), mode: 'new', parameters: { planId: randomUUID() } }, async () => ({ kind: baseline.kind,
    namespace: identity.namespace, slug: identity.slug, version: identity.version, targetLabel: 'Selected', targetPaths: [selected.installDir],
    context: { sourceIdentity: identity.registry, sourceLabel: 'Registry', sourceRevision: 'fixture', hostLabel: 'Host', initiatedFrom: 'plaza' } })))!
  await writeFile(join(root, 'fixture.json'), JSON.stringify({ operationId: accepted.operationId, ownerId: 'session',
    path: selected.installDir, retainedPath: targets[1]!.installDir, before, content,
    quarantine: mode === 'stale' ? null : join(root, 'selected/.skillhub-trash', accepted.operationId) }))
  await runtime.executeRemoval({ operationId: accepted.operationId, ownerId: 'session', baseline, authorize: async () => {} })
}
throw new Error('Removal crash boundary was not reached')
