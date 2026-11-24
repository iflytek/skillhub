import { ConfigStore } from '../stores/config-store'
import { CredentialsStore } from '../stores/credentials-store'
import { resolveRegistry, resolveToken } from '../services/registry-service'
import { findOutdatedSkills, type OutdatedItem, type OutdatedReport } from '../services/outdated-service'
import { CliError } from '../shared/errors'
import { formatUpstreamTag } from '../shared/github-source'

export interface OutdatedCommandOptions {
  namespace?: string | undefined
  agent?: string[] | undefined
  dir?: string | undefined
  registry?: string | undefined
  token?: string | undefined
  json?: boolean | undefined
}

export async function outdatedCommand(options: OutdatedCommandOptions): Promise<string> {
  const registry = resolveRegistry(options, process.env, await new ConfigStore().read())
  const credentials = new CredentialsStore()
  const report = await findOutdatedSkills({
    registry,
    namespace: options.namespace,
    agents: options.agent,
    dir: options.dir,
    tokenForRegistry: async (source: string) => resolveToken(options, process.env, await credentials.getToken(source))
  })
  const output = renderOutdatedReport(report, Boolean(options.json))
  if (report.failure) {
    process.stdout.write(`${output}\n`)
    throw new CliError('outdated check is incomplete', report.failure.exitCode, {
      ...report.failure.details,
      failed: report.failed
    })
  }
  return output
}

export function renderOutdatedReport(report: OutdatedReport, json: boolean): string {
  if (json) {
    return JSON.stringify({
      ok: report.failed === 0,
      registry: report.registry,
      summary: { checked: report.checked, outdated: report.outdated, unknown: report.unknown, failed: report.failed },
      items: report.items
    })
  }

  if (report.checked === 0) return 'No skills installed.'
  if (report.items.length === 0) return `All ${report.checked} checked installations are up to date.`

  return [
    `Outdated: ${report.outdated} with newer versions, ${report.unknown} unknown, ${report.failed} failed, ${report.checked} checked`,
    ...report.items.map(renderItem),
    'Run "skillhub upgrade <coordinate> --check" to review an upgrade; nothing was changed.'
  ].join('\n')
}

function renderItem(item: OutdatedItem): string {
  const versions = `${item.currentVersion} -> ${item.remoteVersion ?? '?'}`
  const reason = item.reason ? ` (${item.reason})` : ''
  const local = item.localState === 'modified'
    ? ` [local changes: ${item.changedFiles.length} file${item.changedFiles.length === 1 ? '' : 's'}]`
    : item.localState === 'unverified'
      ? ` [local state unverified: ${item.localReason ?? 'unknown'}]`
      : ''
  const upstream = formatUpstreamTag(item.remoteSource)
  const source = upstream ? `  (${upstream})` : ''
  return `${item.change.padEnd(10)} ${item.coordinate} ${versions}${source}  ${item.agent}  ${item.installDir}${reason}${local}`
}
