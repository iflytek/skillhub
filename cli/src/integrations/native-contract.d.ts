/** Private, generated into the native plugin at build time. No CLI or schema-library types cross this boundary. */
export interface NativePendingOperation {
  operationId: string
  ownerId: string
  kind: 'install' | 'upgrade' | 'uninstall' | 'remove-stale-record'
  namespace: string
  slug: string
  targetPaths: string[]
}

export interface NativeRecoveryResult {
  operationId: string
  revision: number
  outcome: 'committed' | 'inventory-before' | 'restored' | 'unknown'
  reason: string
}

export interface InstalledProjectionTarget { scope: 'user' | 'project'; root: string }
export interface InstalledProjectionItem {
  scope: 'user' | 'project'
  namespace: string
  slug: string
  version: string
  /** Internal Host path, never a Remote field. */
  targetPath: string
  fingerprint?: string
  /** Internal digest of this exact target's release, timestamp and ownership. */
  recordRevision?: string
  state: 'present' | 'missing' | 'metadata-invalid' | 'mismatch' | 'unreadable'
}
export interface InstalledProjection {
  items: InstalledProjectionItem[]
  complete: boolean
  issues: ('inventory-unreadable' | 'target-unreadable' | 'invalid-record')[]
}
export interface UpdateIdentity { version: string; fingerprint?: string }
export interface UpdateAssessment {
  state: 'upgradable' | 'current' | 'blocked'
  reason: 'newer-version' | 'same-content' | 'remote-older' | 'unknown-order' | 'same-version-content-changed' | 'missing-fingerprint'
  currentVersion: string
  candidateVersion: string
}

export interface SkillExample { id: string; title: string; prompt: string }
export type SkillExamples =
  | { state: 'ready'; digest: string; defaultLocale: string; items: Record<string, SkillExample[]> }
  | { state: 'none' }
  | { state: 'invalid' }
  | { state: 'unavailable'; retryable: boolean }

export interface NativeRuntime {
  /** Host-only fixed-path read after installation verification; never a Remote path argument. */
  readSkillExamples(directory: string): Promise<SkillExamples>
  /** Host-only, read-only evidence; the callback must verify the actual scoped DSH content and resource directory. */
  observeInstallation(operationId: string, ownerId: string, trustedRoots: readonly string[],
    observe: (paths: readonly string[]) => Promise<boolean>): Promise<boolean>
  findPlanOperation(request: NativeOperationRequest): Promise<NativeOperation | undefined>
  executeRemoval(request: NativeRemovalExecution): Promise<NativeRecoveryResult>
  acceptRemoval(request: NativeOperationRequest,
    validateNew: () => Promise<NativeInstallAcceptance & { kind: 'uninstall' | 'remove-stale-record' }>): Promise<NativeOperation | undefined>
  inspectRemoval(selected: UpgradeSelection, target: InstalledProjectionTarget): Promise<RemovalBaseline>
  executeUpgrade(request: NativeUpgradeExecution): Promise<NativeRecoveryResult>
  acceptUpgrade(request: NativeOperationRequest, validateNew: () => Promise<NativeInstallAcceptance>): Promise<NativeOperation | undefined>
  inspectUpgrade(selected: UpgradeSelection, targets: readonly InstalledProjectionTarget[]): Promise<UpgradeBaseline>
  assessUpdate(installed: UpdateIdentity, candidate: UpdateIdentity): UpdateAssessment
  listInstalled(registry: string, targets: readonly InstalledProjectionTarget[]): Promise<InstalledProjection>
  readonly hostInstanceId: string
  listPending(): Promise<NativePendingOperation[]>
  recover(operationId: string, ownerId: string, trustedTargetPaths: readonly string[],
    authorize: (paths: readonly string[], phase: 'cleanup' | 'rollback') => Promise<void>): Promise<NativeRecoveryResult>
  executeInstall(request: NativeInstallExecution): Promise<NativeRecoveryResult>
  acceptInstall(request: NativeOperationRequest, validateNew: () => Promise<NativeInstallAcceptance>): Promise<NativeOperation | undefined>
  getOperation(operationId: string, ownerId: string): Promise<NativeOperation | undefined>
}

export interface UpgradeSelection { registry: string; namespace: string; slug: string; version: string }
/** Private snapshot carried between the bundled core and Host, never supplied by the Client. */
export type UpgradeBaseline =
  | { status: 'blocked'; reason: 'record-changed' | 'target-outside-context' | 'shared-target' | 'target-alias' | 'local-changes' | 'metadata-invalid' | 'target-unavailable' }
  | { status: 'ready'; inventoryItem: Omit<UpgradeSelection, 'version'> & {
    targets: Array<{ agent: string; rootDir: string; installDir: string; installedAt: string; version: string; fingerprint?: string; installedBy?: string[] }>
  }; targets: Array<{
    scope: 'user' | 'project'; root: string; path: string; files: Record<string, string>;
    metadata: UpgradeSelection & { schemaVersion?: number; versionId?: number; fingerprint?: string;
      files?: Record<string, string>; source?: string; agent?: string; installedAt?: string }
  }> }

export interface NativeOperationRequest {
  hostInstanceId: string
  ownerId: string
  requestKey: string
  mode: 'new' | 'retry'
  /** Original business parameters, before resolving expiring Host references. */
  parameters: Record<string, string | number | boolean | null>
}

export interface NativeInstallAcceptance {
  namespace: string
  slug: string
  version: string
  targetLabel: string
  targetPaths: string[]
  context: {
    sourceIdentity: string
    sourceLabel: string
    sourceRevision: string
    hostLabel: string
    initiatedFrom: 'plaza' | 'conversation'
  }
}

/** Safe projection: no target paths, transaction internals, owner IDs or request digests. */
export interface NativeOperation {
  operationId: string
  revision: number
  kind: 'install' | 'upgrade' | 'uninstall' | 'remove-stale-record'
  namespace: string
  slug: string
  version: string
  targetLabel: string
  context: NativeInstallAcceptance['context'] & { hostInstanceId: string; createdAt: string; updatedAt: string }
  phase: 'accepted' | 'staging' | 'revalidating' | 'committing' | 'recovering' | 'completed' | 'failed' | 'recovery-required' | 'unknown'
  files: 'pending' | 'installed' | 'already-installed' | 'upgraded' | 'removed' | 'stale-record-removed' | 'failed' | 'unknown'
  discovery: 'unconfirmed' | 'confirmed' | 'failed'
  recovery: 'not-needed' | 'restored' | 'incomplete' | 'unknown'
  warningCount: number
  /** Optional for responses retained from older development packages. No filesystem paths. */
  resultDetails?: {
    beforeVersion: string | null
    afterVersion: string | null
    targets: { label: string; outcome: 'installed' | 'upgraded' | 'removed' | 'stale-record-removed' | 'restored' | 'unknown' }[]
    warnings: ('backup-cleanup' | 'removal-cleanup' | 'verification-required')[]
  }
}

/** Internal Host input; never a Remote/tool argument schema. */
export interface NativeInstallExecution {
  operationId: string
  ownerId: string
  registry: string
  namespace: string
  slug: string
  version: string
  versionId: number
  fingerprint: string
  root: string
  scope: 'user' | 'project'
  authorize(paths: readonly string[], phase: 'staging' | 'replace' | 'cleanup' | 'rollback'): Promise<void>
  validateStaged(directory: string): Promise<void>
}

export interface NativeCore {
  openNativeRuntime(options: { stateDirectory: string; inventoryHome: string }): Promise<NativeRuntime>
}

export interface NativeUpgradeExecution extends Omit<NativeInstallExecution, 'root' | 'scope'> {
  baseline: Extract<UpgradeBaseline, { status: 'ready' }>
}

export type RemovalBaseline = Extract<UpgradeBaseline, { status: 'blocked' }>
  | { status: 'ready'; kind: 'uninstall'; inventoryItem: Extract<UpgradeBaseline, { status: 'ready' }>['inventoryItem'];
      target: Extract<UpgradeBaseline, { status: 'ready' }>['targets'][number] }
  | { status: 'ready'; kind: 'remove-stale-record'; inventoryItem: Extract<UpgradeBaseline, { status: 'ready' }>['inventoryItem'];
      target: InstalledProjectionTarget & { path: string } }

export interface NativeRemovalExecution {
  operationId: string
  ownerId: string
  baseline: Extract<RemovalBaseline, { status: 'ready' }>
  /** operationId is supplied by the core from its owned accepted record, never from Client paths. */
  authorize(paths: readonly string[], phase: 'staging' | 'replace' | 'cleanup' | 'rollback', operationId: string): Promise<void>
}
