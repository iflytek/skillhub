import type { AgentCandidate } from '../agents/types'
import type { RepositoryInstallPlan } from '../clients/skillhub-client'
import { SkillHubClient } from '../clients/skillhub-client'
import { EXIT } from '../shared/constants'
import { CliError } from '../shared/errors'
import { installSkill, type InstallResult } from './install-service'

export interface RepositoryInstallItem {
  coordinate: string
  version: string
  versionId: number
  action: 'installed' | 'failed' | 'skipped'
  installed?: InstallResult['installed']
  warnings?: string[]
  exitCode?: number
  reason?: string
  recovery?: { status: 'unknown' | 'rollback-incomplete'; targets: string[]; stagingPaths: string[]; retainedBackups: string[]; next: string }
}

export interface RepositoryInstallResult {
  repositoryId: string
  summary: { installed: number; skipped: number; failed: number }
  items: RepositoryInstallItem[]
}

/** Repository membership is a source grouping, not a Suite or a filesystem transaction. */
export async function installRepositoryPlan(
  plan: RepositoryInstallPlan,
  options: { registry: string; token?: string | undefined; targets: AgentCandidate[]; home?: string },
  deps: { installSkill?: typeof installSkill } = {}
): Promise<RepositoryInstallResult> {
  const client = new SkillHubClient(options.registry, options.token)
  const items: RepositoryInstallItem[] = []
  let recoveryRequired = false
  for (const member of plan.members) {
    const identity = { coordinate: `@${member.namespace}/${member.slug}`, version: member.version, versionId: member.versionId }
    if (recoveryRequired) {
      items.push({ ...identity, action: 'skipped', reason: 'an earlier installation requires recovery; no further writes attempted' })
      continue
    }
    try {
      const result = await (deps.installSkill ?? installSkill)({
        ...options,
        namespace: member.namespace,
        slug: member.slug,
        version: member.version,
        resolved: member,
        sourceRepositoryId: plan.repositoryId,
        force: false,
        client
      })
      items.push({ ...identity, action: 'installed', installed: result.installed,
        ...(result.warnings?.length ? { warnings: result.warnings } : {}) })
    } catch (error) {
      const recovery = safeRecovery(error)
      recoveryRequired = recovery !== undefined
      // Unexpected errors may contain HTTP headers, credentials or local stack data.
      items.push({ ...identity, action: 'failed',
        reason: error instanceof CliError ? error.message : 'unexpected installation failure',
        exitCode: error instanceof CliError ? error.exitCode : EXIT.generic,
        ...(recovery ? { recovery } : {}) })
    }
  }
  return { repositoryId: plan.repositoryId,
    summary: { installed: items.filter(item => item.action === 'installed').length, skipped: items.filter(item => item.action === 'skipped').length,
      failed: items.filter(item => item.action === 'failed').length }, items }
}

function safeRecovery(error: unknown): RepositoryInstallItem['recovery'] {
  if (!(error instanceof CliError)) return undefined
  const unknown = error.details.reason === 'INSTALL_COMMIT_UNKNOWN'
  const rollback = error.message === 'installation failed and rollback was incomplete'
  if (!unknown && !rollback) return undefined
  const paths = (value: unknown) => Array.isArray(value) ? value.filter((path): path is string => typeof path === 'string') : []
  const rollbackTargets = Array.isArray(error.details.rollbackFailures) ? error.details.rollbackFailures.flatMap(entry =>
    typeof entry === 'object' && entry !== null && 'path' in entry && typeof entry.path === 'string' ? [entry.path] : []) : []
  return { status: unknown ? 'unknown' : 'rollback-incomplete', targets: unknown ? paths(error.details.targets) : rollbackTargets,
    stagingPaths: paths(error.details.stagingPaths), retainedBackups: paths(error.details.retainedBackups),
    next: unknown ? 'verify the inventory and retained directories before retrying' : 'restore the retained backup directories before retrying' }
}
