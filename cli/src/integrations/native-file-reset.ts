import { randomUUID } from 'node:crypto'
import { cp, mkdir, readFile, realpath, readdir, rename, rm, unlink, writeFile } from 'node:fs/promises'
import { dirname, join } from 'node:path'
import { isDeepStrictEqual } from 'node:util'
import type { InventoryStore } from '../stores/inventory-store'
import { acquireSkillTargetLock } from '../services/skill-target-lock'
import { assertChanges, durableJson, physicalPath, privateDirectory, rawSnapshot, syncDirectory, syncTree, withOriginal } from './native-changes'
import type { NativeFileResetExecution, NativeInstallAcceptance, NativeOperation, NativeOperationRequest, NativeRecoveryResult } from './native-contract'
import { operationRequestDigest } from './operation-store'

type Raw = Awaited<ReturnType<typeof rawSnapshot>>
interface ResetRecord {
  schemaVersion: 1; operationId: string; ownerId: string; requestKey: string; requestDigest: string; revision: number
  operation: NativeOperation; targetPaths: string[]
  intent?: { target: string; root: string; slug: string; staging: string; backup: string; before: Raw; after: Raw;
    snapshot: NativeFileResetExecution['snapshot']; stage: 'prepared' | 'old-moved' | 'published' | 'committed' | 'restored' }
}
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i
const terminal = (record: ResetRecord) => ['completed', 'failed'].includes(record.operation.phase)
/** Separate directory and schema: older OperationStore builds never parse extended reset records. */
export class FileResetStore {
  readonly root: string
  constructor(private state: string, private hostInstanceId: string, private inventory: InventoryStore, private assertOtherOperations: () => Promise<void> = async () => {}) {
    this.root = join(dirname(state), 'file-reset-v1')
  }
  async get(id: string, owner: string): Promise<ResetRecord | undefined> {
    if (!uuid.test(id)) throw new Error('Invalid file reset id')
    await privateDirectory(this.root)
    let raw: string
    try { raw = await readFile(join(this.root, `${id}.json`), 'utf8') }
    catch (error) { if ((error as NodeJS.ErrnoException).code === 'ENOENT') return undefined; throw error }
    const value = JSON.parse(raw) as ResetRecord
    if (value.schemaVersion !== 1 || value.operationId !== id || value.operation.operationId !== id
      || value.operation.kind !== 'reset-files' || !Array.isArray(value.targetPaths) || !Number.isInteger(value.revision)
      || !value.ownerId || !value.requestDigest) throw new Error('Corrupt file reset journal')
    if (value.ownerId !== owner) throw new Error('File reset is not owned')
    if (value.intent) {
      const i = value.intent
      if (i.target !== join(i.root, i.slug) || i.target !== value.targetPaths[0]
        || i.staging !== `${i.target}.skillhub-reset-stage-${id}` || i.backup !== `${i.target}.skillhub-backup-${id}`
        || i.snapshot.baseline.targets[0]?.path !== i.target) throw new Error('Corrupt file reset intent')
    }
    return value
  }
  async list(): Promise<ResetRecord[]> {
    await privateDirectory(this.root)
    const result: ResetRecord[] = []
    for (const name of await readdir(this.root)) {
      if (!name.endsWith('.json')) continue
      const value = JSON.parse(await readFile(join(this.root, name), 'utf8')) as ResetRecord
      const record = await this.get(name.slice(0, -5), value.ownerId)
      if (record) result.push(record)
    }
    return result
  }
  async save(record: ResetRecord): Promise<void> {
    record.revision++; record.operation.revision = record.revision; record.operation.context.updatedAt = new Date().toISOString()
    await durableJson(join(this.root, `${record.operationId}.json`), record)
  }
  async accept(request: NativeOperationRequest, validate: () => Promise<NativeInstallAcceptance>): Promise<NativeOperation | undefined> {
    if (request.hostInstanceId !== this.hostInstanceId) throw new Error('Host changed')
    await privateDirectory(this.root)
    const release = await acquireSkillTargetLock(this.root, 'journal')
    try {
      const hash = operationRequestDigest({ ...request.parameters, kind: 'reset-files' })
      const previous = (await this.list()).find(record => record.ownerId === request.ownerId && record.requestKey === request.requestKey)
      if (previous) { if (previous.requestDigest !== hash) throw new Error('Request changed'); return previous.operation }
      if (request.mode === 'retry') return undefined
      const input = await validate()
      if ((await this.list()).some(record => !terminal(record))) throw new Error('File reset recovery required')
      const now = new Date().toISOString(), operationId = randomUUID()
      const operation: NativeOperation = { operationId, revision: 0, kind: 'reset-files', namespace: input.namespace,
        slug: input.slug, version: input.version, targetLabel: input.targetLabel,
        context: { ...input.context, hostInstanceId: this.hostInstanceId, createdAt: now, updatedAt: now },
        phase: 'accepted', files: 'pending', discovery: 'unconfirmed', recovery: 'not-needed', warningCount: 0 }
      await this.save({ schemaVersion: 1, operationId, ownerId: request.ownerId, requestKey: request.requestKey,
        requestDigest: hash, revision: 0, operation, targetPaths: [...input.targetPaths] })
      return operation
    } finally { await release() }
  }
  async execute(input: NativeFileResetExecution): Promise<NativeRecoveryResult> {
    const request = { ...input, snapshot: structuredClone(input.snapshot), paths: [...input.paths] }
    const lease = await acquireSkillTargetLock(this.root, `execution-${request.operationId}`)
    try {
      const record = await this.get(request.operationId, request.ownerId)
      if (!record || record.operation.phase !== 'accepted') throw new Error('File reset is not accepted')
      const snapshot = request.snapshot, target = snapshot.baseline.targets[0]!.path
      if (record.operation.version !== snapshot.selection.version || record.operation.namespace !== snapshot.selection.namespace
        || record.operation.slug !== snapshot.selection.slug || record.operation.context.sourceIdentity !== snapshot.selection.registry
        || record.targetPaths.length !== 1 || record.targetPaths[0] !== target || !request.paths.length
        || new Set(request.paths).size !== request.paths.length
        || request.paths.some(path => !snapshot.changes.some(file => file.path === path))) throw new Error('File reset parameters changed')
      const staging = `${target}.skillhub-reset-stage-${request.operationId}`, backup = `${target}.skillhub-backup-${request.operationId}`
      let stagingCreated = false
      try {
        await withOriginal(this.state, snapshot, async original => {
          const release = await acquireSkillTargetLock(snapshot.target.root, snapshot.selection.slug)
          try { await this.inventory.withLockedSnapshot(async () => {
            await this.assertOtherOperations()
            await assertChanges(this.inventory, snapshot)
            await request.authorize([snapshot.target.root, target, staging, backup], 'staging', record.operationId)
            await mkdir(staging, { mode: 0o700 }); stagingCreated = true
            const before = await rawSnapshot(target)
            await cp(target, staging, { recursive: true, force: false, preserveTimestamps: true })
            if (!isDeepStrictEqual(await rawSnapshot(staging), before)) throw new Error('Target changed while copying')
            await assertChanges(this.inventory, snapshot)
            for (const path of request.paths) {
              const physical = physicalPath(snapshot, path), destination = join(staging, physical)
              if (!snapshot.baseline.targets[0]!.metadata.files![path]) await unlink(destination)
              else { await mkdir(dirname(destination), { recursive: true }); await writeFile(destination, await readFile(join(original, path)), { mode: 0o600 }) }
            }
            const after = await rawSnapshot(staging)
            await syncTree(staging)
            await assertChanges(this.inventory, snapshot)
            record.intent = { target, root: snapshot.target.root, slug: snapshot.selection.slug, staging, backup, before, after, snapshot, stage: 'prepared' }
            record.operation.phase = 'committing'; await this.save(record)
            await request.authorize([target, staging, backup], 'replace', record.operationId)
            await this.assertOtherOperations()
            await rename(target, backup); await syncDirectory(snapshot.target.root); record.intent.stage = 'old-moved'; await this.save(record)
            if (!isDeepStrictEqual(await rawSnapshot(backup), before)) throw new Error('Files changed before reset')
            await rename(staging, target); await syncDirectory(snapshot.target.root); record.intent.stage = 'published'; await this.save(record)
            if (!isDeepStrictEqual(await rawSnapshot(target), after)) throw new Error('Reset result changed')
            record.intent.stage = 'committed'; record.operation.phase = 'committing'; record.operation.files = 'files-reset'; await this.save(record)
            await request.authorize([backup], 'cleanup', record.operationId)
            if (!isDeepStrictEqual(await rawSnapshot(backup), before)) throw new Error('Reset backup changed')
            await rm(backup, { recursive: true }); await syncDirectory(snapshot.target.root)
            record.operation.phase = 'completed'; await this.save(record)
          }) } finally { await release() }
        })
      } catch {
        const current = await this.get(record.operationId, record.ownerId)
        if (current?.intent) {
          current.operation.phase = 'recovery-required'; current.operation.recovery = 'unknown'; current.operation.files = 'unknown'; await this.save(current)
          return { operationId: current.operationId, revision: current.revision, outcome: 'unknown', reason: 'file-reset-recovery-required' }
        }
        // No move intent exists: the skill is untouched. Remove only this operation's staging.
        try { if (stagingCreated) { await request.authorize([staging], 'cleanup', record.operationId); await rm(staging, { recursive: true, force: true }) } } catch { /* leave bounded staging for diagnosis */ }
        record.operation.phase = 'failed'; record.operation.files = 'failed'; await this.save(record)
      }
      const current = (await this.get(record.operationId, record.ownerId))!
      return { operationId: current.operationId, revision: current.revision,
        outcome: current.operation.phase === 'completed' ? 'committed' : 'inventory-before', reason: current.operation.phase }
    } finally { await lease() }
  }
  async recover(id: string, owner: string, trusted: readonly string[], authorize: (paths: readonly string[], phase: 'cleanup' | 'rollback') => Promise<void>): Promise<NativeRecoveryResult> {
    const lease = await acquireSkillTargetLock(this.root, `execution-${id}`)
    try {
      const record = await this.get(id, owner)
      if (!record) throw new Error('Reset record missing')
      const i = record.intent
      if (!i) {
        record.operation.phase = 'failed'; record.operation.files = 'failed'; await this.save(record)
        return { operationId: id, revision: record.revision, outcome: 'inventory-before', reason: 'no-reset-intent' }
      }
      if (trusted.length !== 1 || trusted[0] !== i.target) throw new Error('Recovery target changed')
      const release = await acquireSkillTargetLock(i.root, i.slug)
      try { return await this.inventory.withLockedSnapshot(async () => {
        const inventory = await this.inventory.read()
        const ownerItem = inventory.items.find(item => item.registry === i.snapshot.selection.registry && item.namespace === i.snapshot.selection.namespace && item.slug === i.slug)
        const withoutState = (value: unknown) => { const copy = { ...(value as Record<string, unknown>) }; delete copy.installationState; return copy }
        if (!ownerItem || !isDeepStrictEqual(withoutState(ownerItem.targets.find(t => t.installDir === i.target)), withoutState(i.snapshot.baseline.inventoryItem.targets[0]))) throw new Error('Reset recovery ownership changed')
        const read = async (path: string): Promise<Raw | undefined> => {
          try { return await rawSnapshot(path) } catch (error) { if ((error as NodeJS.ErrnoException).code === 'ENOENT') return undefined; throw error }
        }
        if (await realpath(i.root) !== i.root) throw new Error('Reset root redirects')
        const target = await read(i.target), backup = await read(i.backup), staging = await read(i.staging)
        if (i.stage === 'committed') {
          if (!isDeepStrictEqual(target, i.after) || (backup && !isDeepStrictEqual(backup, i.before)) || staging) throw new Error('Committed reset changed')
          if (backup) {
            await authorize([i.backup], 'cleanup')
            if (await realpath(i.root) !== i.root) throw new Error('Reset root redirects')
            if (!isDeepStrictEqual(await read(i.backup), backup) || !isDeepStrictEqual(await read(i.target), target)) throw new Error('Reset recovery content changed after authorization')
            await rm(i.backup, { recursive: true }); await syncDirectory(i.root)
          }
          record.operation.phase = 'completed'; record.operation.files = 'files-reset'; record.operation.recovery = 'not-needed'; await this.save(record)
          return { operationId: id, revision: record.revision, outcome: 'committed', reason: 'reset-committed' }
        }
        if (target && !isDeepStrictEqual(target, i.before) && !isDeepStrictEqual(target, i.after)) throw new Error('Recovery refuses changed target')
        if (backup && !isDeepStrictEqual(backup, i.before)) throw new Error('Recovery backup changed')
        if (staging && !isDeepStrictEqual(staging, i.after)) throw new Error('Recovery staging changed')
        if (!target && !backup) throw new Error('No verified original remains')
        if (backup) {
          await authorize([i.target, i.backup], 'rollback')
          if (await realpath(i.root) !== i.root) throw new Error('Reset root redirects')
          if (!isDeepStrictEqual(await read(i.target), target) || !isDeepStrictEqual(await read(i.backup), backup) || !isDeepStrictEqual(await read(i.staging), staging)) throw new Error('Reset recovery content changed after authorization')
          if (target) await rm(i.target, { recursive: true })
          await rename(i.backup, i.target); await syncDirectory(i.root)
        } else if (!isDeepStrictEqual(target, i.before)) throw new Error('Original reset target missing')
        if (staging) {
          await authorize([i.staging], 'cleanup')
          if (!isDeepStrictEqual(await read(i.staging), staging)) throw new Error('Reset staging changed after authorization')
          await rm(i.staging, { recursive: true }); await syncDirectory(i.root)
        }
        record.intent!.stage = 'restored'; record.operation.phase = 'failed'; record.operation.files = 'failed'; record.operation.recovery = 'restored'; await this.save(record)
        return { operationId: id, revision: record.revision, outcome: 'restored', reason: 'reset-restored' }
      }) } finally { await release() }
    } catch (error) {
      const record = await this.get(id, owner)
      if (record) { record.operation.phase = 'recovery-required'; record.operation.recovery = 'unknown'; await this.save(record) }
      throw error
    } finally { await lease() }
  }
}
