import type { Inventory } from '../stores/inventory-store'

export interface InstallMove {
  phase: 'backup' | 'install'
  index: number
  agent: string
  canonicalSkillDir: string
  stagingDir: string
  backupDir: string | null
  from: string
  to: string
}

export interface InstallCommit {
  before: Inventory
  after: Inventory
}

export interface InstallPreparation {
  before: Inventory
  release: { registry: string; namespace: string; slug: string; version: string; fingerprint: string }
  targets: Array<{ agent: string; canonicalSkillDir: string; skillDir: string; stagingDir: string; installedAt: string }>
}

export interface InstallCleanup {
  path: string
  removed: boolean
  error?: string
}

export interface InstallStaging {
  canonicalSkillDir: string
  rootDir: string
  stagingDir: string
}

/** Durable observers are awaited; these are not UI progress listeners. */
export interface InstallTransactionObserver {
  /** Called before download and again while holding all target locks. */
  beforeTargetMutation?(canonicalTargets: readonly string[]): Promise<void>
  /** Persist the chosen temporary path before creating its directory or extracting bytes. */
  beforeStaging?(staging: InstallStaging): Promise<void>
  /** Persist every staged target and the old inventory before the first rename. */
  beforePreparation?(preparation: InstallPreparation): Promise<void>
  beforeMove?(move: InstallMove): Promise<void>
  afterMove?(move: InstallMove): Promise<void>
  beforeInventoryCommit?(commit: InstallCommit): Promise<void>
  afterInventoryCommit?(): Promise<void>
  cleanupResult?(cleanup: InstallCleanup): Promise<void>
}

export interface InstallTransactionWarning {
  stage: 'afterInventoryCommit' | 'backupCleanup' | 'cleanupResult'
  message: string
  path?: string
}
