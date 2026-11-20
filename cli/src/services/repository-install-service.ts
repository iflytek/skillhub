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
  action: 'installed' | 'failed'
  installed?: InstallResult['installed']
  warnings?: string[]
  exitCode?: number
  reason?: string
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
  for (const member of plan.members) {
    const identity = { coordinate: `@${member.namespace}/${member.slug}`, version: member.version, versionId: member.versionId }
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
      // Unexpected errors may contain HTTP headers, credentials or local stack data.
      items.push({ ...identity, action: 'failed',
        reason: error instanceof CliError ? error.message : 'unexpected installation failure',
        exitCode: error instanceof CliError ? error.exitCode : EXIT.generic })
    }
  }
  return { repositoryId: plan.repositoryId,
    summary: { installed: items.filter(item => item.action === 'installed').length, skipped: 0,
      failed: items.filter(item => item.action === 'failed').length }, items }
}
