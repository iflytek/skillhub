import { createHash, randomUUID } from 'node:crypto'
import { writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { zipSync } from 'fflate'
import { SkillHubClient } from '../../src/clients/skillhub-client'
import { OperationStore, operationRequestDigest } from '../../src/integrations/operation-store'
import { createInstallTransactionObserver } from '../../src/integrations/transaction-journal'
import { installSkill } from '../../src/services/install-service'
import { InventoryStore } from '../../src/stores/inventory-store'

const [root, boundary, first] = process.argv.slice(2)
if (!root || !boundary) throw new Error('Missing crash fixture arguments')
const firstInstall = first === 'first'
const identity = { registry: 'https://registry.example', namespace: 'team', slug: 'writer' }
const oldContent = '# Old recovery fixture'
const newContent = '# New recovery fixture'
const fingerprint = (content: string) => 'sha256:' + createHash('sha256').update('SKILL.md:' + createHash('sha256').update(content).digest('hex') + '\n').digest('hex')
class LocalClient extends SkillHubClient {
  constructor(readonly content: string) { super(identity.registry) }
  async download(): Promise<Response> {
    return new Response(new Uint8Array(zipSync({ 'SKILL.md': new TextEncoder().encode(this.content) })).buffer)
  }
}
const targets = ['user', 'project'].map(scope => ({ agent: 'dsh', rootDir: join(root, scope), scope: scope as 'user' | 'project', source: 'explicit' as const }))
const home = join(root, 'home')
if (!firstInstall) await installSkill({ ...identity, home, force: false, targets, client: new LocalClient(oldContent),
  resolved: { namespace: 'team', slug: 'writer', version: '1.0.0', versionId: 1, fingerprint: fingerprint(oldContent), downloadUrl: '' } })
const operations = await OperationStore.open(join(root, 'operations'))
const trustedTargetPaths = targets.map(target => join(target.rootDir, 'writer'))
const accepted = (await operations.accept({ ownerId: 'session', hostInstanceId: operations.hostInstanceId, mode: 'new',
  requestKey: randomUUID(), requestDigest: operationRequestDigest({ version: '2.0.0' }) }, async () => ({
  kind: firstInstall ? 'install' : 'upgrade', namespace: 'team', slug: 'writer', version: '2.0.0', targetLabel: 'Both', targetPaths: trustedTargetPaths,
  context: { sourceIdentity: identity.registry, sourceLabel: 'SkillHub', sourceRevision: randomUUID(), hostLabel: 'Host', initiatedFrom: 'plaza' }
})))!
await operations.update(accepted.operationId, accepted.ownerId, accepted.revision, { ...accepted.operation.status, phase: 'staging' })
await writeFile(join(root, 'fixture.json'), JSON.stringify({ operationId: accepted.operationId, ownerId: accepted.ownerId,
  trustedTargetPaths, before: await new InventoryStore(home).read(), oldContent, newContent }))
const observer = await createInstallTransactionObserver(operations, accepted.operationId, accepted.ownerId)
async function stop(at: string): Promise<void> {
  if (boundary !== at) return
  process.kill(process.pid, 'SIGKILL')
  await new Promise(() => {})
}
await installSkill({ ...identity, home, force: !firstInstall, targets, client: new LocalClient(newContent),
  validateStagedSkill: async () => { await stop('staged-content') },
  resolved: { namespace: 'team', slug: 'writer', version: '2.0.0', versionId: 2, fingerprint: fingerprint(newContent), downloadUrl: '' },
  transactionObserver: { ...observer,
    beforeStaging: async staging => { await observer.beforeStaging!(staging); await stop('before-staging') },
    beforePreparation: async preparation => { await observer.beforePreparation!(preparation); await stop('prepared') },
    beforeMove: async move => { await observer.beforeMove!(move); if (move.index === 0) await stop(`before-${move.phase}`) },
    afterMove: async move => { await observer.afterMove!(move); if (move.index === 0) await stop(`after-${move.phase}`) },
    beforeInventoryCommit: async commit => { await observer.beforeInventoryCommit!(commit); await stop('before-commit') },
    afterInventoryCommit: async () => { await stop('after-commit'); await observer.afterInventoryCommit!() }
  }
})
throw new Error('Crash boundary was not reached')
