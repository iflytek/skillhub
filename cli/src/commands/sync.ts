import { join, resolve } from 'node:path'
import { ConfigStore } from '../stores/config-store'
import { CredentialsStore } from '../stores/credentials-store'
import { SkillHubClient } from '../clients/skillhub-client'
import { resolveRegistry, resolveToken } from '../services/registry-service'
import {
  discoverSkillDirectories,
  inspectNamespaceWorkspace,
  pullNamespace,
  pushSkills,
  type PullResult,
  type PushResultItem,
  type SyncStatusEntry
} from '../services/sync-service'
import { CliError } from '../shared/errors'
import { EXIT } from '../shared/constants'

export interface SyncCommonOptions {
  namespace?: string
  dir?: string
  registry?: string
  token?: string
  json?: boolean
}

export interface SyncPullOptions extends SyncCommonOptions {
  check?: boolean
  prune?: boolean
  force?: boolean
}

export interface SyncPushOptions extends SyncCommonOptions {
  all?: boolean
  visibility?: string
  dryRun?: boolean
  submitReview?: boolean
}

export async function syncPullCommand(options: SyncPullOptions): Promise<string> {
  const context = await resolveSyncContext(options)
  const result = await pullNamespace({
    ...context,
    check: Boolean(options.check),
    prune: Boolean(options.prune),
    force: Boolean(options.force)
  })
  const output = renderPullResult(result, Boolean(options.json), Boolean(options.check))
  if (result.failures.length > 0) {
    process.stdout.write(`${output}\n`)
    throw new CliError('namespace sync completed with failures', EXIT.generic, {
      namespace: context.namespace,
      failures: result.failures
    })
  }
  return output
}

export async function syncStatusCommand(options: SyncCommonOptions): Promise<string> {
  const context = await resolveSyncContext(options)
  const result = await inspectNamespaceWorkspace(context)
  return renderStatusEntries(context.namespace, context.rootDir, result.entries, Boolean(options.json))
}

export async function syncDiffCommand(options: SyncCommonOptions): Promise<string> {
  const context = await resolveSyncContext(options)
  const result = await inspectNamespaceWorkspace(context)
  const changed = result.entries.filter(entry => entry.status !== 'up-to-date')
  if (options.json) {
    return JSON.stringify({ ok: true, namespace: context.namespace, rootDir: context.rootDir, items: changed })
  }
  if (changed.length === 0) return `No differences for namespace ${context.namespace}.`
  return changed.flatMap(entry => {
    const lines = [`${entry.status.padEnd(16)} ${entry.slug}`]
    for (const path of entry.changedFiles) lines.push(`  ${path}`)
    if (entry.reason) lines.push(`  ${entry.reason}`)
    return lines
  }).join('\n')
}

export async function syncPushCommand(path: string | undefined, options: SyncPushOptions): Promise<string> {
  const context = await resolveSyncContext(options)
  if (path && options.all) {
    throw new CliError('path cannot be combined with --all', EXIT.usage)
  }
  if (!path && !options.all) {
    throw new CliError('provide a skill path or pass --all', EXIT.usage)
  }

  const visibility = normalizeVisibility(options.visibility ?? 'namespace-only')
  if (options.submitReview && visibility === 'PRIVATE') {
    throw new CliError('--submit-review requires public or namespace-only visibility', EXIT.usage)
  }
  const paths = options.all
    ? await discoverSkillDirectories(context.rootDir)
    : [resolve(path!)]
  if (paths.length === 0) {
    throw new CliError(`no skill directories found in ${context.rootDir}`, EXIT.filesystem, { path: context.rootDir })
  }

  const results = await pushSkills({
    client: context.client,
    namespace: context.namespace,
    paths,
    visibility,
    dryRun: Boolean(options.dryRun),
    submitReview: Boolean(options.submitReview)
  })
  const output = renderPushResults(context.namespace, results, Boolean(options.json), Boolean(options.dryRun))
  if (results.some(item => item.action === 'failed')) {
    process.stdout.write(`${output}\n`)
    throw new CliError('one or more skills failed to push', EXIT.validation, {
      namespace: context.namespace,
      failed: results.filter(item => item.action === 'failed')
    })
  }
  return output
}

async function resolveSyncContext(options: SyncCommonOptions): Promise<{
  client: SkillHubClient
  registry: string
  token: string
  namespace: string
  rootDir: string
}> {
  const configStore = new ConfigStore()
  const credentialsStore = new CredentialsStore()
  const registry = resolveRegistry(options, process.env, await configStore.read())
  const token = resolveToken(options, process.env, await credentialsStore.getToken(registry))
  if (!token) {
    throw new CliError('authentication required for namespace sync', EXIT.auth, { next: 'run `skillhub login`' })
  }
  const namespace = options.namespace ?? 'global'
  const rootDir = resolve(options.dir ?? join(process.cwd(), '.agents', 'skills'))
  return { client: new SkillHubClient(registry, token), registry, token, namespace, rootDir }
}

export function renderPullResult(result: PullResult, json: boolean, check: boolean): string {
  if (json) {
    return JSON.stringify({ ok: result.failures.length === 0, check, ...result })
  }
  const lines = [
    `${check ? 'Checked' : 'Synchronized'} ${result.namespace} in ${result.rootDir}`,
    ...result.actions.map(item => `${item.action.padEnd(10)} ${item.slug}`),
    ...result.entries
      .filter(entry => !result.actions.some(action => action.slug === entry.slug))
      .map(entry => `${entry.status.padEnd(16)} ${entry.slug}`),
    ...result.warnings.map(item => `warning    ${item.slug}: ${item.message}`),
    ...result.failures.map(item => `failed     ${item.slug}: ${item.message}`)
  ]
  return lines.join('\n')
}

function renderStatusEntries(namespace: string, rootDir: string, entries: SyncStatusEntry[], json: boolean): string {
  if (json) return JSON.stringify({ ok: true, namespace, rootDir, items: entries })
  if (entries.length === 0) return `No installable skills found in namespace ${namespace}.`
  return entries.map(entry => {
    const versions = entry.remoteVersion
      ? ` local=${entry.localVersion ?? '-'} remote=${entry.remoteVersion}`
      : ` local=${entry.localVersion ?? '-'}`
    return `${entry.status.padEnd(16)} ${entry.slug}${versions}`
  }).join('\n')
}

function renderPushResults(namespace: string, results: PushResultItem[], json: boolean, dryRun: boolean): string {
  if (json) return JSON.stringify({ ok: results.every(item => item.action !== 'failed'), namespace, dryRun, items: results })
  return results.map(item => {
    const coordinate = item.slug ? `${namespace}/${item.slug}${item.version ? `@${item.version}` : ''}` : item.path
    const detail = item.errors?.length ? `: ${item.errors.join('; ')}` : ''
    return `${item.action.padEnd(16)} ${coordinate}${detail}`
  }).join('\n')
}

function normalizeVisibility(value: string): 'PUBLIC' | 'NAMESPACE_ONLY' | 'PRIVATE' {
  const normalized = value.toUpperCase().replace(/-/g, '_')
  if (normalized !== 'PUBLIC' && normalized !== 'NAMESPACE_ONLY' && normalized !== 'PRIVATE') {
    throw new CliError('visibility must be public, namespace-only, or private', EXIT.usage)
  }
  return normalized
}
