import { renameAtomicFile } from '../platform/atomic-rename'
import { inventoryWithoutState, type Inventory } from '../stores/inventory-store'
import { isDeepStrictEqual } from 'node:util'
import { toggleJournal, validateToggleJournal, type ToggleJournal } from './toggle-journal'
import { createHash, randomUUID } from 'node:crypto'
import { constants } from 'node:fs'
import { chmod, link, lstat, mkdir, open, readdir, readFile, realpath, rm, unlink } from 'node:fs/promises'
import { basename, dirname, isAbsolute, join } from 'node:path'
import { canonicalizeExistingPath, pathsOverlap } from '../platform/paths'
import { z } from 'zod'
import { acquireSkillTargetLock } from '../services/skill-target-lock'
import { recoveryAction, transactionEvent, transactionState } from './transaction-journal'
import type { RecoveryAction, TransactionEvent } from './transaction-journal'
import type { InstallCleanup } from '../services/install-transaction'
import { CliError } from '../shared/errors'
import { applyRemovalEvent, removalEvent, removalJournal } from './removal-journal'
import type { RemovalEvent } from './removal-journal'

const id = z.string().uuid()
const text = z.string().min(1).max(2048)
const phase = z.enum(['accepted', 'staging', 'revalidating', 'committing', 'recovering', 'completed', 'failed', 'recovery-required', 'unknown'])
const context = z.object({ hostInstanceId: id, hostLabel: text, sourceIdentity: text,
  sourceLabel: text, sourceRevision: text, initiatedFrom: z.enum(['plaza', 'conversation']),
  createdAt: z.string().datetime(), updatedAt: z.string().datetime() }).strict()
const input = z.object({ kind: z.enum(['install', 'upgrade', 'uninstall', 'remove-stale-record', 'enable', 'disable']),
  namespace: text, slug: text, version: text, targetLabel: text,
  targetPaths: z.array(text.refine(isAbsolute, 'Expected a trusted canonical target path')).min(1).max(100).optional(),
  context: context.omit({ hostInstanceId: true, createdAt: true, updatedAt: true }) }).strict()
const status = z.object({ phase,
  files: z.enum(['pending', 'installed', 'already-installed', 'upgraded', 'removed', 'stale-record-removed', 'enabled', 'disabled', 'failed', 'unknown']),
  discovery: z.enum(['unconfirmed', 'confirmed', 'failed']), warnings: z.array(text).max(100),
  recovery: z.enum(['not-needed', 'restored', 'incomplete', 'unknown']) }).strict()
const existingInstallation = z.object({ fingerprint: text, versionId: z.number().int().positive(),
  inventoryDigest: z.string().regex(/^[a-f0-9]{64}$/) }).strict()
const record = z.object({ schemaVersion: z.union([z.literal(1), z.literal(2), z.literal(3)]), operationId: id, ownerId: text,
  requestKey: id, requestDigest: z.string().regex(/^[a-f0-9]{64}$/), revision: z.number().int().nonnegative(),
  operation: input.omit({ context: true }).extend({ context, status }), transaction: transactionState.optional(),
  toggle: toggleJournal.optional(), removal: removalJournal.optional(), existingInstallation: existingInstallation.optional() }).strict()
export type AcceptedOperationInput = z.infer<typeof input>
export type OperationRecord = z.infer<typeof record>
export type OperationStatus = z.infer<typeof status>
export interface OperationRequest { hostInstanceId: string; ownerId: string; requestKey: string; requestDigest: string; mode: 'new' | 'retry' }
export class OperationStoreError extends Error {
  constructor(readonly code: 'REQUEST_KEY_CONFLICT' | 'REVISION_CONFLICT' | 'HOST_MISMATCH' | 'CORRUPT_RECORD' | 'INVALID_TRANSITION' | 'TARGET_RECOVERY_REQUIRED') { super(code) }
}

/** Hash original request parameters without persisting tokens or arbitrary request contents. */
export function operationRequestDigest(parameters: Record<string, string | number | boolean | null>): string {
  const entries = Object.entries(parameters).sort(([a], [b]) => a < b ? -1 : a > b ? 1 : 0)
  if (entries.some(([, value]) => value !== null && !['string', 'number', 'boolean'].includes(typeof value)
    || typeof value === 'number' && !Number.isFinite(value))) throw new Error('Invalid request parameters')
  return createHash('sha256').update(JSON.stringify(entries)).digest('hex')
}

/** Host-only durable operation ownership. This store never executes or replays a file mutation. */
export class OperationStore {
  private constructor(readonly root: string, readonly hostInstanceId: string,
    private readonly replaceFile: typeof renameAtomicFile = renameAtomicFile) {}

  static async open(root: string, replaceFile: typeof renameAtomicFile = renameAtomicFile): Promise<OperationStore> {
    if (!isAbsolute(root)) throw new Error('Operation state requires a trusted absolute directory')
    await mkdir(root, { recursive: true, mode: 0o700 })
    const info = await lstat(root)
    if (!info.isDirectory() || info.isSymbolicLink() || (process.getuid && info.uid !== process.getuid())) throw new Error('Unsafe operation state directory')
    if (process.platform !== 'win32') await chmod(root, 0o700)
    const pending = new OperationStore(root, '', replaceFile)
    return pending.locked(async () => {
      const path = join(root, 'host.json')
      const saved = await readJson(path)
      const hostInstanceId = saved === undefined ? randomUUID() : z.object({ hostInstanceId: id }).strict().parse(saved).hostInstanceId
      if (saved === undefined) await atomicWrite(path, { hostInstanceId }, replaceFile)
      const store = new OperationStore(root, hostInstanceId, replaceFile)
      await store.isolateExtendedRecords()
      return store
    })
  }

  async accept(request: OperationRequest, validateNew: () => Promise<AcceptedOperationInput>): Promise<OperationRecord | undefined> {
    this.validateRequest(request)
    const existing = await this.locked(() => this.findRequest(request))
    if (existing || request.mode === 'retry') return existing
    // Validation must not run under the journal lock: lifecycle code acquires target locks first.
    const accepted = input.parse(await validateNew())
    const source = new URL(accepted.context.sourceIdentity)
    if (!['http:', 'https:'].includes(source.protocol) || source.username || source.password || source.search || source.hash) throw new Error('Unsafe source identity')
    return this.locked(async () => {
      const duplicate = await this.findRequest(request)
      if (duplicate) return duplicate
      const now = new Date().toISOString()
      const value: OperationRecord = { schemaVersion: ['enable', 'disable'].includes(accepted.kind) ? 3 : 2, operationId: randomUUID(), ownerId: request.ownerId,
        requestKey: request.requestKey, requestDigest: request.requestDigest, revision: 0,
        operation: { ...accepted, context: { ...accepted.context, hostInstanceId: this.hostInstanceId, createdAt: now, updatedAt: now },
          status: { phase: 'accepted', files: 'pending', discovery: 'unconfirmed', recovery: 'not-needed', warnings: [] } } }
      await this.writeRecord(record.parse(value))
      return value
    })
  }

  async get(operationId: string, ownerId: string): Promise<OperationRecord | undefined> {
    const value = await this.read(operationId)
    return value?.ownerId === ownerId ? value : undefined
  }

  /** Startup-only internal scan; never expose other owners' records through a Client Remote. */
  async listForRecovery(): Promise<OperationRecord[]> {
    return this.locked(async () => (await this.readAll()).filter(value => needsRecovery(value)
      || needsCleanupVerification(value)))
  }

  async assertTargetsAvailable(targets: readonly string[], currentOperation: { operationId: string; ownerId: string }): Promise<void> {
    await this.locked(async () => {
      const current = await this.get(currentOperation.operationId, currentOperation.ownerId)
      if (!current?.operation.targetPaths || !['staging', 'revalidating'].includes(current.operation.status.phase)
        || targets.length !== current.operation.targetPaths.length || targets.some(path => !current.operation.targetPaths!.includes(path))) {
        throw new OperationStoreError('INVALID_TRANSITION')
      }
      const canonicalTargets = await Promise.all(targets.map(canonicalOperationTarget))
      for (const pending of await this.readAll()) {
        if (pending.operationId === current.operationId || !needsRecovery(pending)) continue
        const status = pending.operation.status
        const mayHaveChangedFiles = !!pending.transaction?.moves.length
          || !!pending.removal?.intent.quarantine || !!pending.toggle
          || ['committing', 'recovering', 'recovery-required', 'unknown'].includes(status.phase)
          || ['incomplete', 'unknown'].includes(status.recovery)
        if (!mayHaveChangedFiles) continue
        const bound = pending.operation.targetPaths ?? pending.transaction?.moves.map(entry => entry.move.canonicalSkillDir)
        // Older/incomplete records without target identity cannot safely be scoped to one directory.
        if (!bound?.length) throw new OperationStoreError('TARGET_RECOVERY_REQUIRED')
        for (const path of bound) {
          const canonical = await canonicalOperationTarget(path)
          if (canonicalTargets.some(target => pathsOverlap(target, canonical))) throw new OperationStoreError('TARGET_RECOVERY_REQUIRED')
        }
      }
    })
  }

  async update(operationId: string, ownerId: string, revision: number, next: OperationStatus): Promise<OperationRecord> {
    const parsed = status.parse(next)
    return this.locked(async () => {
      const current = await this.get(operationId, ownerId)
      if (!current) throw new OperationStoreError('CORRUPT_RECORD')
      if (current.revision !== revision) throw new OperationStoreError('REVISION_CONFLICT')
      assertTransition(current, parsed)
      const value: OperationRecord = { ...current, revision: revision + 1, operation: { ...current.operation,
        context: { ...current.operation.context, updatedAt: new Date().toISOString() }, status: parsed } }
      await this.writeRecord(value)
      return value
    })
  }

  /** Caller holds target/inventory locks and has verified this exact read-only installation. */
  async completeExistingInstallation(operationId: string, ownerId: string, revision: number,
    snapshot: z.infer<typeof existingInstallation>): Promise<OperationRecord> {
    const parsed = existingInstallation.parse(snapshot)
    return this.locked(async () => {
      const current = await this.get(operationId, ownerId)
      if (!current) throw new OperationStoreError('CORRUPT_RECORD')
      if (current.revision !== revision) throw new OperationStoreError('REVISION_CONFLICT')
      if (current.operation.kind !== 'install' || current.operation.status.phase !== 'revalidating'
        || current.operation.status.files !== 'pending' || current.operation.status.recovery !== 'not-needed'
        || current.transaction || current.removal || current.existingInstallation
        || current.operation.targetPaths?.length !== 1) throw new OperationStoreError('INVALID_TRANSITION')
      const next: OperationStatus = { ...current.operation.status,
        phase: 'completed', files: 'already-installed', recovery: 'not-needed' }
      assertTransition(current, next)
      const value: OperationRecord = { ...current, revision: revision + 1, existingInstallation: parsed,
        operation: { ...current.operation, status: next,
          context: { ...current.operation.context, updatedAt: new Date().toISOString() } } }
      await this.writeRecord(record.parse(value))
      return value
    })
  }

  async recordTransaction(operationId: string, ownerId: string, revision: number, event: TransactionEvent): Promise<OperationRecord> {
    const parsed = transactionEvent.parse(event)
    return this.locked(async () => {
      const current = await this.get(operationId, ownerId)
      if (!current) throw new OperationStoreError('CORRUPT_RECORD')
      if (current.revision !== revision) throw new OperationStoreError('REVISION_CONFLICT')
      if (!['install', 'upgrade'].includes(current.operation.kind)
        || !['staging', 'revalidating', 'committing'].includes(current.operation.status.phase)) throw new OperationStoreError('INVALID_TRANSITION')
      const transaction = current.transaction ?? { moves: [], cleanup: [] }
      let nextPhase = current.operation.status.phase
      switch (parsed.type) {
        case 'beforeStaging': {
          const stage = parsed.staging
          const suffix = basename(stage.stagingDir).slice(`.${current.operation.slug}.install-`.length)
          if (current.operation.status.phase !== 'staging' || transaction.preparation || transaction.moves.length
            || !current.operation.targetPaths?.includes(stage.canonicalSkillDir)
            || !isAbsolute(stage.rootDir) || dirname(stage.stagingDir) !== stage.rootDir
            || !basename(stage.stagingDir).startsWith(`.${current.operation.slug}.install-`)
            || !/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(suffix)
            || join(await canonicalizeExistingPath(stage.rootDir), current.operation.slug) !== stage.canonicalSkillDir
            || transaction.staging?.some(entry => entry.canonicalSkillDir === stage.canonicalSkillDir || entry.stagingDir === stage.stagingDir)) {
            throw new OperationStoreError('INVALID_TRANSITION')
          }
          transaction.staging ??= []
          transaction.staging.push(stage)
          break
        }
        case 'beforePreparation': {
          const { release, targets } = parsed.preparation
          const bound = current.operation.targetPaths
          if (transaction.preparation || transaction.moves.length || transaction.inventoryCommit || !bound
            || release.registry !== current.operation.context.sourceIdentity || release.namespace !== current.operation.namespace
            || release.slug !== current.operation.slug || release.version !== current.operation.version
            || targets.length !== bound.length || new Set(targets.map(target => target.canonicalSkillDir)).size !== targets.length
            || targets.some(target => !bound.includes(target.canonicalSkillDir))
            || (transaction.staging && (transaction.staging.length !== targets.length || targets.some(target =>
              !transaction.staging!.some(stage => stage.canonicalSkillDir === target.canonicalSkillDir && stage.stagingDir === target.stagingDir))))) throw new OperationStoreError('INVALID_TRANSITION')
          transaction.preparation = parsed.preparation
          nextPhase = 'revalidating'
          break
        }
        case 'beforeMove': {
          if (!current.operation.targetPaths?.includes(parsed.move.canonicalSkillDir) || transaction.inventoryCommit || transaction.moves.some(entry => entry.move.index === parsed.move.index && entry.move.phase === parsed.move.phase)) throw new OperationStoreError('INVALID_TRANSITION')
          if (transaction.preparation) {
            const target = transaction.preparation.targets[parsed.move.index]
            if (!target || target.canonicalSkillDir !== parsed.move.canonicalSkillDir || target.agent !== parsed.move.agent
              || target.stagingDir !== parsed.move.stagingDir
              || (parsed.move.phase === 'backup' ? parsed.move.from !== target.skillDir : parsed.move.to !== target.skillDir)) {
              throw new OperationStoreError('INVALID_TRANSITION')
            }
          }
          transaction.moves.push({ move: parsed.move, completed: false })
          nextPhase = 'revalidating'
          break
        }
        case 'afterMove': {
          const entry = transaction.moves.find(entry => JSON.stringify(entry.move) === JSON.stringify(parsed.move))
          if (!entry || transaction.inventoryCommit) throw new OperationStoreError('INVALID_TRANSITION')
          entry.completed = true
          break
        }
        case 'beforeInventoryCommit': {
          if (transaction.inventoryCommit || !transaction.moves.length || transaction.moves.some(entry => !entry.completed)) throw new OperationStoreError('INVALID_TRANSITION')
          if (transaction.preparation?.targets.some(target => !transaction.moves.some(entry => entry.move.phase === 'install'
            && entry.move.canonicalSkillDir === target.canonicalSkillDir && entry.completed))) throw new OperationStoreError('INVALID_TRANSITION')
          transaction.inventoryCommit = { ...parsed.commit, committed: false }
          nextPhase = 'committing'
          break
        }
        case 'afterInventoryCommit': {
          if (!transaction.inventoryCommit) throw new OperationStoreError('INVALID_TRANSITION')
          transaction.inventoryCommit.committed = true
          break
        }
        case 'cleanupResult': {
          if (!transaction.inventoryCommit?.committed || !transaction.moves.some(entry => entry.move.phase === 'backup' && entry.move.to === parsed.cleanup.path)) throw new OperationStoreError('INVALID_TRANSITION')
          transaction.cleanup.push(parsed.cleanup)
          break
        }
      }
      const value: OperationRecord = { ...current, revision: revision + 1, transaction,
        operation: { ...current.operation, status: { ...current.operation.status, phase: nextPhase },
          context: { ...current.operation.context, updatedAt: new Date().toISOString() } } }
      await this.writeRecord(record.parse(value))
      return value
    })
  }

  /** Internal recovery writer: caller must verify all files while holding target and inventory locks. */
  async confirmCommittedInstallation(operationId: string, ownerId: string, revision: number): Promise<OperationRecord> {
    return this.locked(async () => {
      const current = await this.get(operationId, ownerId)
      if (!current) throw new OperationStoreError('CORRUPT_RECORD')
      if (current.revision !== revision) throw new OperationStoreError('REVISION_CONFLICT')
      const transaction = current.transaction
      const targets = current.operation.targetPaths
      if (!['install', 'upgrade'].includes(current.operation.kind) || !transaction?.inventoryCommit || !targets?.length
        || !['committing', 'recovering', 'recovery-required', 'unknown', 'completed'].includes(current.operation.status.phase)
        || targets.some(path => transaction.moves.filter(entry => entry.move.phase === 'install'
          && entry.move.canonicalSkillDir === path && entry.completed).length !== 1)) throw new OperationStoreError('INVALID_TRANSITION')
      const files = current.operation.kind === 'install' ? 'installed' : 'upgraded'
      if (current.operation.status.phase === 'completed' && current.operation.status.files !== files) throw new OperationStoreError('INVALID_TRANSITION')
      if (current.operation.status.phase === 'completed' && transaction.inventoryCommit.committed) return current
      transaction.inventoryCommit.committed = true
      const warnings = [...current.operation.status.warnings]
      if (needsCleanupVerification(current) && !warnings.includes('Backup cleanup requires verification.')) {
        warnings.push('Backup cleanup requires verification.')
      }
      const value: OperationRecord = { ...current, revision: revision + 1, transaction,
        operation: { ...current.operation, context: { ...current.operation.context, updatedAt: new Date().toISOString() },
          status: { ...current.operation.status, phase: 'completed', files, recovery: 'not-needed', warnings } } }
      await this.writeRecord(record.parse(value))
      return value
    })
  }

  /** Record a verified post-commit backup cleanup while the caller holds target/inventory locks. */
  async recordRecoveredCleanup(operationId: string, ownerId: string, revision: number, cleanup: InstallCleanup): Promise<OperationRecord> {
    const event = transactionEvent.parse({ type: 'cleanupResult', cleanup })
    if (event.type !== 'cleanupResult') throw new OperationStoreError('INVALID_TRANSITION')
    return this.locked(async () => {
      const current = await this.get(operationId, ownerId)
      if (!current) throw new OperationStoreError('CORRUPT_RECORD')
      if (current.revision !== revision) throw new OperationStoreError('REVISION_CONFLICT')
      const transaction = current.transaction
      if (!['install', 'upgrade'].includes(current.operation.kind) || current.operation.status.phase !== 'completed' || !transaction?.inventoryCommit?.committed
        || !transaction.moves.some(entry => entry.move.phase === 'backup' && entry.move.to === event.cleanup.path)) {
        throw new OperationStoreError('INVALID_TRANSITION')
      }
      transaction.cleanup = [...transaction.cleanup.filter(entry => entry.path !== event.cleanup.path), event.cleanup]
      const warning = 'Backup cleanup requires verification.'
      const warnings = current.operation.status.warnings.filter(value => value !== warning)
      if (needsCleanupVerification(current)) warnings.push(warning)
      const value: OperationRecord = { ...current, revision: revision + 1, transaction,
        operation: { ...current.operation, status: { ...current.operation.status, warnings },
          context: { ...current.operation.context, updatedAt: new Date().toISOString() } } }
      await this.writeRecord(record.parse(value))
      return value
    })
  }

  /** Await before and after each recovery filesystem change; caller owns target/inventory locks. */
  async recordRecoveryAction(operationId: string, ownerId: string, revision: number, action: RecoveryAction, completed: boolean): Promise<OperationRecord> {
    const parsed = recoveryAction.parse(action)
    return this.locked(async () => {
      const current = await this.get(operationId, ownerId)
      if (!current) throw new OperationStoreError('CORRUPT_RECORD')
      if (current.revision !== revision) throw new OperationStoreError('REVISION_CONFLICT')
      const transaction = current.transaction
      const target = transaction?.preparation?.targets[parsed.index]
      if (current.operation.status.phase !== 'recovering' || !transaction || !target || transaction.inventoryCommit?.committed) {
        throw new OperationStoreError('INVALID_TRANSITION')
      }
      const stage = target.stagingDir
      const skill = target.skillDir
      const backup = transaction.moves.find(entry => entry.move.phase === 'backup' && entry.move.index === parsed.index)?.move.to
      if (parsed.kind === 'return-staging' ? parsed.from !== skill || parsed.to !== stage
        : parsed.kind === 'restore-backup' ? parsed.from !== backup || parsed.to !== skill
          : parsed.from !== stage || parsed.to !== null) throw new OperationStoreError('INVALID_TRANSITION')
      const actions = transaction.recoveryActions ?? []
      const existing = actions.find(entry => JSON.stringify(entry.action) === JSON.stringify(parsed))
      if (completed && !existing || !completed && existing?.completed) throw new OperationStoreError('INVALID_TRANSITION')
      if (existing) existing.completed = completed
      else actions.push({ action: parsed, completed: false })
      transaction.recoveryActions = actions
      const value: OperationRecord = { ...current, transaction, revision: revision + 1,
        operation: { ...current.operation, context: { ...current.operation.context, updatedAt: new Date().toISOString() } } }
      await this.writeRecord(record.parse(value))
      return value
    })
  }

  async recordRemoval(operationId: string, ownerId: string, revision: number, event: RemovalEvent): Promise<OperationRecord> {
    const parsed = removalEvent.parse(event)
    return this.locked(async () => {
      const current = await this.get(operationId, ownerId)
      if (!current) throw new OperationStoreError('CORRUPT_RECORD')
      if (current.revision !== revision) throw new OperationStoreError('REVISION_CONFLICT')
      let removal
      try { removal = applyRemovalEvent(current, parsed) }
      catch { throw new OperationStoreError('INVALID_TRANSITION') }
      const value: OperationRecord = { ...current, removal, revision: revision + 1,
        operation: { ...current.operation, status: { ...current.operation.status,
          phase: parsed.type === 'beforeCommit' ? 'committing' : current.operation.status.phase },
        context: { ...current.operation.context, updatedAt: new Date().toISOString() } } }
      await this.writeRecord(record.parse(value))
      return value
    })
  }

  async recordToggle(operationId: string, ownerId: string, revision: number, journal: ToggleJournal): Promise<OperationRecord> {
    return this.locked(async () => {
      const current = await this.get(operationId, ownerId)
      if (!current || current.revision !== revision) throw new OperationStoreError('REVISION_CONFLICT')
      const parsed = toggleJournal.parse(journal)
      validateToggleJournal(current, parsed)
      if (!['revalidating', 'committing', 'recovering'].includes(current.operation.status.phase)) throw new OperationStoreError('INVALID_TRANSITION')
      if (current.toggle && (!isDeepStrictEqual({ ...current.toggle, stage: parsed.stage }, parsed)
        || current.toggle.stage === 'committed' || current.toggle.stage === 'restored')) throw new OperationStoreError('INVALID_TRANSITION')
      if (!current.toggle && parsed.stage !== 'intent') throw new OperationStoreError('INVALID_TRANSITION')
      const transitions: Record<ToggleJournal['stage'], ToggleJournal['stage'][]> = {
        intent: ['linked', 'restored'], linked: ['moved', 'restoring', 'restored'], moved: ['committed', 'restoring', 'restored'],
        restoring: ['restore-linked', 'restored'], 'restore-linked': ['restored'], committed: [], restored: []
      }
      if (current.toggle && !transitions[current.toggle.stage].includes(parsed.stage)) throw new OperationStoreError('INVALID_TRANSITION')
      const value = record.parse({ ...current, toggle: parsed, revision: revision + 1,
        operation: { ...current.operation, status: { ...current.operation.status,
          phase: current.operation.status.phase === 'recovering' ? 'recovering' : parsed.stage === 'intent' ? 'revalidating' : 'committing' },
          context: { ...current.operation.context, updatedAt: new Date().toISOString() } } })
      await this.writeRecord(value)
      return value
    })
  }

  private validateRequest(request: OperationRequest): void {
    if (request.hostInstanceId !== this.hostInstanceId) throw new OperationStoreError('HOST_MISMATCH')
    text.parse(request.ownerId); id.parse(request.requestKey)
    record.shape.requestDigest.parse(request.requestDigest)
    z.enum(['new', 'retry']).parse(request.mode)
  }
  private path(operationId: string): string { return join(this.root, `${id.parse(operationId)}.json`) }
  private async read(operationId: string): Promise<OperationRecord | undefined> {
    await this.validateExtendedDirectory()
    const extended = await readJson(this.extendedPath(operationId))
    const ordinary = await readJson(this.path(operationId))
    if (extended !== undefined && ordinary !== undefined && !isDeepStrictEqual(extended, ordinary)) throw new OperationStoreError('CORRUPT_RECORD')
    const json = extended ?? ordinary
    if (json === undefined) return undefined
    const parsed = record.safeParse(json)
    if (!parsed.success || parsed.data.operationId !== operationId || parsed.data.operation.context.hostInstanceId !== this.hostInstanceId) throw new OperationStoreError('CORRUPT_RECORD')
    if (parsed.data.schemaVersion < 3 && (parsed.data.toggle || ['enable', 'disable'].includes(parsed.data.operation.kind))) throw new OperationStoreError('CORRUPT_RECORD')
    if (parsed.data.toggle) validateToggleJournal(parsed.data, parsed.data.toggle)
    return parsed.data
  }
  private async findRequest(request: OperationRequest): Promise<OperationRecord | undefined> {
    let match: OperationRecord | undefined
    for (const stored of await this.readAll()) {
      if (!stored || stored.ownerId !== request.ownerId || stored.requestKey !== request.requestKey) continue
      if (match) throw new OperationStoreError('CORRUPT_RECORD')
      if (stored.requestDigest !== request.requestDigest) throw new OperationStoreError('REQUEST_KEY_CONFLICT')
      match = stored
    }
    return match
  }
  private async readAll(): Promise<OperationRecord[]> {
    const values: OperationRecord[] = []
    await this.validateExtendedDirectory()
    const extended = await readdir(join(this.root, 'activation')).catch((error: NodeJS.ErrnoException) => { if (error.code === 'ENOENT') return []; throw error })
    for (const name of [...new Set([...(await readdir(this.root)), ...extended])].sort()) {
      if (name === 'host.json' || !name.endsWith('.json')) continue
      const value = await this.read(name.slice(0, -5))
      if (!value) throw new OperationStoreError('CORRUPT_RECORD')
      values.push(value)
    }
    return values
  }

  private extendedPath(operationId: string): string { return join(this.root, 'activation', `${id.parse(operationId)}.json`) }

  private async validateExtendedDirectory(): Promise<void> {
    try {
      const info = await lstat(join(this.root, 'activation'))
      if (!info.isDirectory() || info.isSymbolicLink() || (process.getuid && info.uid !== process.getuid())) throw new OperationStoreError('CORRUPT_RECORD')
    } catch (error) { if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error }
  }

  private async prepareExtendedDirectory(): Promise<void> {
    const path = join(this.root, 'activation')
    await mkdir(path, { recursive: true, mode: 0o700 })
    const info = await lstat(path)
    if (!info.isDirectory() || info.isSymbolicLink() || (process.getuid && info.uid !== process.getuid())) throw new OperationStoreError('CORRUPT_RECORD')
  }

  /** The intermediate duplicate must be identical; never prefer a competing revision. */
  private async isolateRecord(operationId: string): Promise<void> {
    await this.prepareExtendedDirectory()
    const source = this.path(operationId)
    const destination = this.extendedPath(operationId)
    const original = await readJson(source)
    if (original === undefined) return
    await this.read(operationId)
    const bytes = await readFile(source)
    const backup = join(this.root, 'activation', `${operationId}.${createHash('sha256').update(bytes).digest('hex')}.backup`)
    try {
      const file = await open(backup, 'wx', 0o600)
      try { await file.writeFile(bytes); await file.sync() } finally { await file.close() }
    } catch (error) { if ((error as NodeJS.ErrnoException).code !== 'EEXIST') throw error }
    const info = await lstat(backup)
    if (!info.isFile() || info.isSymbolicLink() || !(await readFile(backup)).equals(bytes)) throw new OperationStoreError('CORRUPT_RECORD')
    try { await link(source, destination) }
    catch (error) {
      if ((error as NodeJS.ErrnoException).code !== 'EEXIST' || !isDeepStrictEqual(await readJson(destination), original)) throw error
    }
    await syncJournalDirectory(join(this.root, 'activation'))
    await unlink(source)
    await syncJournalDirectory(this.root)
  }

  /** Preserve original extended evidence while keeping legacy readers' root scan usable. */
  private async isolateExtendedRecords(): Promise<void> {
    await this.validateExtendedDirectory()
    for (const name of await readdir(this.root)) {
      if (name === 'host.json' || !name.endsWith('.json')) continue
      const operationId = name.slice(0, -5)
      const raw = await readJson(this.path(operationId)) as OperationRecord | undefined
      if (raw?.schemaVersion === 3 || await readJson(this.extendedPath(operationId)) !== undefined) await this.isolateRecord(operationId)
    }
  }

  private async writeRecord(value: OperationRecord): Promise<void> {
    const extended = value.schemaVersion === 3 || await readJson(this.extendedPath(value.operationId)) !== undefined || operationSnapshots(value).some(snapshot => snapshot.items.some(item => item.targets.some(target => target.installationState === 'disabled')))
    if (extended) {
      await this.prepareExtendedDirectory()
      await this.isolateRecord(value.operationId)
      await writeOperationRecord(this.extendedPath(value.operationId), { ...value, schemaVersion: 3 }, this.replaceFile)
      return
    }
    // v2 snapshots contain ownership only, exactly as consumed by published clients.
    const compatible = structuredClone(value)
    if (compatible.transaction?.preparation) compatible.transaction.preparation.before = inventoryWithoutState(compatible.transaction.preparation.before)
    if (compatible.transaction?.inventoryCommit) {
      compatible.transaction.inventoryCommit.before = inventoryWithoutState(compatible.transaction.inventoryCommit.before)
      compatible.transaction.inventoryCommit.after = inventoryWithoutState(compatible.transaction.inventoryCommit.after)
    }
    if (compatible.removal) {
      compatible.removal.intent.inventoryItem = inventoryWithoutState({ items: [compatible.removal.intent.inventoryItem] }).items[0]!
      if (compatible.removal.inventoryCommit) {
        compatible.removal.inventoryCommit.before = inventoryWithoutState(compatible.removal.inventoryCommit.before)
        compatible.removal.inventoryCommit.after = inventoryWithoutState(compatible.removal.inventoryCommit.after)
      }
    }
    await writeOperationRecord(this.path(value.operationId), compatible, this.replaceFile)
  }

  private async locked<T>(run: () => Promise<T>): Promise<T> {
    let release: (() => Promise<void>) | undefined
    const deadline = Date.now() + 10_000
    while (!release) {
      try { release = await acquireSkillTargetLock(this.root, 'operation-journal') }
      catch (error) {
        if (!(error instanceof CliError) || error.details.reason !== 'TARGET_BUSY' || Date.now() >= deadline) throw error
        await new Promise(resolve => setTimeout(resolve, 20))
      }
    }
    try { return await run() } finally { await release() }
  }
}

function needsRecovery(value: OperationRecord): boolean {
  const { phase, recovery } = value.operation.status
  return !['completed', 'failed'].includes(phase) || ['incomplete', 'unknown'].includes(recovery)
    || (phase === 'failed' && !!value.transaction?.moves.length && recovery !== 'restored')
    || (phase === 'failed' && !!value.removal?.intent.quarantine && recovery !== 'restored')
}

function needsCleanupVerification(value: OperationRecord): boolean {
  if (value.removal?.inventoryCommit?.committed && value.removal.intent.quarantine !== null
    && value.removal.cleanup?.removed !== true) return true
  const transaction = value.transaction
  if (!transaction) return false
  // Missing acknowledgment is not proof that a backup was removed. Scan it without replaying cleanup.
  const acknowledged = new Map(transaction.cleanup.map(entry => [entry.path, entry.removed]))
  if ([...acknowledged.values()].some(removed => !removed)) return true
  if (!transaction.inventoryCommit?.committed) return false
  return transaction.moves.some(entry => entry.move.phase === 'backup' && acknowledged.get(entry.move.to) !== true)
}

function assertTransition(current: OperationRecord, next: OperationStatus): void {
  const prior = current.operation.status
  const allowed: Record<OperationStatus['phase'], OperationStatus['phase'][]> = {
    accepted: ['staging', 'revalidating', 'completed', 'failed', 'recovering', 'unknown'],
    staging: ['revalidating', 'failed', 'recovering', 'recovery-required', 'unknown'],
    revalidating: ['committing', 'completed', 'failed', 'recovering', 'recovery-required', 'unknown'],
    committing: ['completed', 'failed', 'recovering', 'recovery-required', 'unknown'],
    recovering: ['completed', 'failed', 'recovery-required', 'unknown'],
    'recovery-required': ['recovering'], unknown: ['recovering'], completed: [], failed: []
  }
  if (next.phase !== prior.phase && !allowed[prior.phase].includes(next.phase)) throw new OperationStoreError('INVALID_TRANSITION')
  if (['completed', 'failed'].includes(prior.phase) && (next.files !== prior.files || next.recovery !== prior.recovery)) throw new OperationStoreError('INVALID_TRANSITION')
  if (next.phase === 'completed') {
    if (['uninstall', 'remove-stale-record'].includes(current.operation.kind)
      && !current.removal?.inventoryCommit?.committed) throw new OperationStoreError('INVALID_TRANSITION')
    if (['enable', 'disable'].includes(current.operation.kind) && current.toggle?.stage !== 'committed') throw new OperationStoreError('INVALID_TRANSITION')
    const outcomes = { install: ['installed', 'already-installed'], upgrade: ['upgraded', 'already-installed'],
      uninstall: ['removed'], 'remove-stale-record': ['stale-record-removed'], enable: ['enabled'], disable: ['disabled'] }
    if (!outcomes[current.operation.kind].includes(next.files) || !['not-needed', 'restored'].includes(next.recovery)) throw new OperationStoreError('INVALID_TRANSITION')
    if (['accepted', 'revalidating'].includes(prior.phase) && next.files !== 'already-installed') throw new OperationStoreError('INVALID_TRANSITION')
  }
}

async function readJson(path: string): Promise<unknown | undefined> {
  let file
  try { file = await open(path, constants.O_RDONLY | constants.O_NOFOLLOW) }
  catch (error) { if ((error as NodeJS.ErrnoException).code === 'ENOENT') return undefined; throw error }
  try {
    const info = await file.stat()
    if (!info.isFile() || info.size > 1024 * 1024) throw new OperationStoreError('CORRUPT_RECORD')
    return JSON.parse(await file.readFile('utf8')) as unknown
  } finally { await file.close() }
}

async function atomicWrite(path: string, value: unknown, replaceFile: typeof renameAtomicFile): Promise<void> {
  const temporary = `${path}.${randomUUID()}.tmp`
  try {
    const payload = JSON.stringify(value)
    if (Buffer.byteLength(payload) > 1024 * 1024) throw new OperationStoreError('CORRUPT_RECORD')
    const file = await open(temporary, 'wx', 0o600)
    try { await file.writeFile(payload); await file.sync() } finally { await file.close() }
    await replaceFile(temporary, path)
    if (process.platform !== 'win32') {
      const directory = await open(dirname(path), 'r')
      try { await directory.sync() } finally { await directory.close() }
    }
  } finally { await rm(temporary, { force: true }) }
}

function operationSnapshots(value: OperationRecord): Inventory[] {
  return [value.transaction?.preparation?.before, value.transaction?.inventoryCommit?.before,
    value.transaction?.inventoryCommit?.after, value.removal ? { items: [value.removal.intent.inventoryItem] } : undefined,
    value.removal?.inventoryCommit?.before, value.removal?.inventoryCommit?.after].filter((snapshot): snapshot is Inventory => snapshot !== undefined)
}

async function syncJournalDirectory(path: string): Promise<void> {
  if (process.platform === 'win32') return
  const directory = await open(path, 'r')
  try { await directory.sync() } finally { await directory.close() }
}

/** Recovery may advance v1 status, but never rewrite the original serialized evidence. */
async function writeOperationRecord(path: string, value: OperationRecord, replaceFile: typeof renameAtomicFile): Promise<void> {
  if (value.schemaVersion === 3) return atomicWrite(path, value, replaceFile)
  const previous = await readJson(path) as OperationRecord | undefined
  if (!previous && value.schemaVersion === 2) return atomicWrite(path, value, replaceFile)
  if (!previous || previous.schemaVersion !== value.schemaVersion) throw new Error('Legacy operation evidence missing')
  const preserved = structuredClone(value)
  if (preserved.transaction && previous.transaction) {
    if (preserved.transaction.preparation && previous.transaction.preparation) {
      preserved.transaction.preparation.before = previous.transaction.preparation.before
    }
    if (preserved.transaction.inventoryCommit && previous.transaction.inventoryCommit) {
      preserved.transaction.inventoryCommit.before = previous.transaction.inventoryCommit.before
      preserved.transaction.inventoryCommit.after = previous.transaction.inventoryCommit.after
    }
  }
  if (preserved.removal && previous.removal) {
    preserved.removal.intent = previous.removal.intent
    if (preserved.removal.inventoryCommit && previous.removal.inventoryCommit) {
      preserved.removal.inventoryCommit.before = previous.removal.inventoryCommit.before
      preserved.removal.inventoryCommit.after = previous.removal.inventoryCommit.after
    }
  }
  await atomicWrite(path, preserved, replaceFile)
}

/** A pending install may not exist yet; resolve the nearest existing ancestor. */
async function canonicalOperationTarget(path: string): Promise<string> {
  try { return await realpath(path) }
  catch (error) {
    if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error
    const parent = dirname(path)
    if (parent === path) throw error
    return join(await canonicalOperationTarget(parent), basename(path))
  }
}
