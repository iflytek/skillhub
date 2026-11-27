import { lstat, readFile, realpath } from 'node:fs/promises'
import { isAbsolute, join } from 'node:path'
import { SkillHubClient } from '../clients/skillhub-client'
import { ConfigStore } from '../stores/config-store'
import { CredentialsStore } from '../stores/credentials-store'
import { InventoryStore, parseInventory, readInstallationState } from '../stores/inventory-store'
import { EXIT } from '../shared/constants'
import { resolveRegistry, resolveToken } from './registry-service'
import { readInstalledSkillMetadata, sameInstalledSkillSource } from './installed-skill-metadata'
import { snapshotInstalledSkillDirectory } from './installed-skill-snapshot'
import { assertOwnedRegularTree } from '../integrations/recovery-files'

export interface HealthOptions { registry?: string; token?: string; offline?: boolean }
export interface HealthCheck {
  id: string
  status: 'pass' | 'warn' | 'fail' | 'skipped'
  code: string
  message: string
  next?: string
  exitCode?: number
}
export interface HealthReport {
  schemaVersion: 1
  mode: 'health'
  ok: boolean
  registry: string | null
  registrySource: 'argument' | 'environment' | 'config' | 'default' | null
  credentialSource: 'argument' | 'environment' | 'stored' | 'none' | 'unavailable'
  paths: { config: string | null; credentials: string | null; inventory: string | null }
  checks: HealthCheck[]
  exitCode: number
}
interface HealthDependencies {
  home?: string
  env?: NodeJS.ProcessEnv
  fetch?: (input: string | URL | Request, init?: RequestInit) => Promise<Response>
  timeoutMs?: number
  readStateFile?: (path: string) => Promise<string>
}

function record(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

/** Observation only: never call state migration, mutation, login or installation-lock APIs. */
export async function runHealth(options: HealthOptions, dependencies: HealthDependencies = {}): Promise<HealthReport> {
  const env = dependencies.env ?? process.env
  const home = dependencies.home ?? (env.HOME || env.USERPROFILE || '')
  const report: HealthReport = {
    schemaVersion: 1, mode: 'health', ok: true, registry: null, registrySource: null,
    credentialSource: 'none', paths: { config: null, credentials: null, inventory: null }, checks: [], exitCode: 0
  }
  const add = (id: string, status: HealthCheck['status'], code: string, message: string, exitCode?: number, next?: string) => {
    report.checks.push({ id, status, code, message, ...(exitCode === undefined ? {} : { exitCode }), ...(next ? { next } : {}) })
  }
  const read = dependencies.readStateFile ?? ((path: string) => readFile(path, 'utf8'))
  async function state(id: string, path: string, validate: (value: Record<string, unknown>) => boolean) {
    let value: unknown
    try {
      const bytes = await read(path)
      try { value = JSON.parse(bytes) } catch {
        add(id, 'fail', 'INVALID_JSON', 'State file is not valid JSON.', EXIT.filesystem, 'Inspect the state file; no changes were made.')
        return { value: undefined, failed: true }
      }
    } catch (error) {
      if ((error as NodeJS.ErrnoException).code === 'ENOENT') {
        add(id, 'warn', 'STATE_MISSING', 'Optional state file does not exist; it was not created.')
        return { value: undefined, failed: false }
      }
      add(id, 'fail', 'STATE_UNREADABLE', 'State file could not be read.', EXIT.filesystem, 'Check file access and type; no probe file was written.')
      return { value: undefined, failed: true }
    }
    try {
      if (!record(value) || !validate(value)) throw new Error('Invalid state')
    } catch {
      add(id, 'fail', 'INVALID_STATE', 'State file has an unsupported or invalid structure.', EXIT.filesystem)
      return { value: undefined, failed: true }
    }
    add(id, 'pass', 'STATE_READABLE', 'State file is readable and its structure is valid; write access was not tested.')
    return { value: value as Record<string, unknown>, failed: false }
  }
  const secrets = [options.token, env.SKILLHUB_TOKEN].filter((value): value is string => Boolean(value))
  if (!home) {
    add('home', 'fail', 'HOME_UNAVAILABLE', 'Cannot resolve the user state directory.', EXIT.filesystem)
    add('connectivity', 'skipped', 'REGISTRY_UNRESOLVED', 'Remote connectivity was not checked.')
    add('identity', 'skipped', 'REGISTRY_UNRESOLVED', 'Remote identity was not checked.')
  } else {
    report.paths = { config: new ConfigStore(home).path, credentials: new CredentialsStore(home).path, inventory: new InventoryStore(home).path }
    const config = await state('config', report.paths.config!, value => value.registry === undefined || typeof value.registry === 'string')
    const credentials = await state('credentials', report.paths.credentials!, value => value.tokens === undefined
      || record(value.tokens) && Object.values(value.tokens).every(token => typeof token === 'string'))
    const saved = credentials.value?.tokens as Record<string, string> | undefined
    if (saved) secrets.push(...Object.values(saved).filter(Boolean))
    if (!config.failed || options.registry || env.SKILLHUB_REGISTRY) {
      const registry = resolveRegistry(options, env, config.value ?? {})
      try {
        const url = new URL(registry)
        if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.search || url.hash
          || [...registry].some(char => char.charCodeAt(0) <= 32 || char.charCodeAt(0) === 127)) throw new Error('Unsafe registry')
        report.registry = registry
        report.registrySource = options.registry ? 'argument' : env.SKILLHUB_REGISTRY ? 'environment' : config.value?.registry ? 'config' : 'default'
        add('registry', 'pass', 'REGISTRY_SELECTED', 'The registry was resolved using the normal CLI precedence.')
      } catch {
        add('registry', 'fail', 'INVALID_REGISTRY', 'Registry must be an HTTP(S) URL without credentials, query or fragment.', EXIT.generic, 'Check the registry address; unsafe input was not displayed or contacted.')
      }
    } else {
      add('registry', 'skipped', 'REGISTRY_UNRESOLVED', 'Configuration failed; no default registry will be contacted.')
    }
    const token = resolveToken(options, env, report.registry ? saved?.[report.registry] : undefined)
    report.credentialSource = options.token ? 'argument' : env.SKILLHUB_TOKEN ? 'environment'
      : token ? 'stored' : credentials.failed ? 'unavailable' : 'none'
    const inventory = await state('inventory', report.paths.inventory!, value => { parseInventory(value); return true })
    if (inventory.value && report.registry) {
      const parsed = parseInventory(inventory.value)
      let inspected = 0
      for (const item of parsed.items.filter(item => item.registry.replace(/\/+$/, '') === report.registry)) {
        for (const target of item.targets) {
          const id = `installation:${inspected++}`
          try {
            if (['.', '..'].includes(item.slug) || /[/\\]/.test(item.slug)
              || !isAbsolute(target.rootDir) || !isAbsolute(target.installDir) || join(target.rootDir, item.slug) !== target.installDir
              || await realpath(target.rootDir) !== target.rootDir || await realpath(target.installDir) !== target.installDir
              || !(await lstat(target.installDir)).isDirectory()) throw new Error('Unsafe location')
            await assertOwnedRegularTree(target.installDir)
            const metadata = await readInstalledSkillMetadata(target.installDir)
            if (metadata.status !== 'valid' || !sameInstalledSkillSource(metadata.metadata, item)
              || metadata.metadata.version !== target.version) throw new Error('Invalid metadata')
            const entryState = await readInstallationState(target.installDir)
            if (!entryState) throw new Error('Missing or ambiguous entry')
            if (inventory.value.schemaVersion === 3 && target.installationState !== entryState) throw new Error('Activation state is inconsistent')
            if (!target.fingerprint || !metadata.metadata.files) {
              add(id, 'warn', 'INTEGRITY_UNVERIFIED', 'Registered installation has no complete integrity baseline; its contents were not certified.')
              continue
            }
            const observed = { ...target, installationState: entryState }
            const snapshot = await snapshotInstalledSkillDirectory({ ...item, targets: [observed] }, observed)
            if (snapshot.fingerprint !== target.fingerprint) {
              add(id, 'warn', 'CONTENT_MODIFIED', 'Registered installation has local content changes; no files were repaired.', undefined, 'Review local changes before an explicit upgrade.')
            } else {
              add(id, 'pass', 'INSTALLATION_VERIFIED', 'Registered installation matches its baseline; activation state was preserved.')
            }
          } catch {
            add(id, 'fail', 'INSTALLATION_UNVERIFIED', 'Registered installation is missing, unsafe, unreadable or has inconsistent metadata.', EXIT.filesystem, 'Inspect the registered installation; no repair or lock was attempted.')
          }
        }
      }
      if (!inspected) add('installations', 'skipped', 'NO_REGISTERED_INSTALLATIONS', 'No installations are registered for the selected registry.')
    } else {
      add('installations', 'skipped', 'INSTALLATIONS_NOT_CHECKED', 'No readable inventory and selected registry are available.')
    }
    if (options.offline || !report.registry) {
      add('connectivity', 'skipped', options.offline ? 'OFFLINE' : 'REGISTRY_UNRESOLVED', 'Remote connectivity was not checked.')
      add('identity', 'skipped', options.offline ? 'OFFLINE' : 'REGISTRY_UNRESOLVED', 'Credential validity was not checked.')
    } else {
      report.checks.push(...await inspectRegistry(report.registry, token, report.credentialSource, dependencies))
    }
  }
  report.exitCode = [EXIT.filesystem, EXIT.auth, EXIT.network, EXIT.generic]
    .find(code => report.checks.some(check => check.status === 'fail' && check.exitCode === code)) ?? 0
  report.ok = report.exitCode === 0
  // Redact known credentials even if they occur inside a configured path or URL.
  const redact = (value: string | null) => value === null ? null
    : secrets.reduce((text, secret) => text.split(secret).join('[REDACTED]'), value)
  report.registry = redact(report.registry)
  report.paths = { config: redact(report.paths.config), credentials: redact(report.paths.credentials), inventory: redact(report.paths.inventory) }
  report.checks = report.checks.map(check => check.next ? { ...check, next: redact(check.next)! } : check)
  return report
}

async function inspectRegistry(registry: string, token: string | undefined, source: HealthReport['credentialSource'], dependencies: HealthDependencies): Promise<HealthCheck[]> {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), dependencies.timeoutMs ?? 5000)
  const checks: HealthCheck[] = []
  let status: number | undefined
  let transportCode: string | undefined
  const diagnosticFetch = Object.assign(async (input: string | URL | Request, init?: RequestInit) => {
    try {
      const response = await (dependencies.fetch ?? fetch)(input, { ...init, signal: controller.signal, redirect: 'manual' })
      status = response.status
      return response
    } catch (error) {
      let cause: unknown = error
      for (let depth = 0; depth < 5 && record(cause); depth++) {
        if (typeof cause.code === 'string') transportCode = cause.code
        cause = cause.cause
      }
      throw error
    }
  }, { preconnect: fetch.preconnect })
  try {
    const user = await new SkillHubClient(registry, token, diagnosticFetch).whoami()
    if (!user || typeof user.handle !== 'string' || !user.handle) throw new Error('Invalid identity response')
    checks.push({ id: 'connectivity', status: 'pass', code: 'REGISTRY_REACHABLE', message: 'The registry returned a valid CLI identity response.' })
    checks.push({ id: 'identity', status: token ? 'pass' : 'warn', code: token ? 'CREDENTIAL_ACCEPTED' : 'NO_CREDENTIAL',
      message: token ? 'The selected credential was accepted; operation permissions were not tested.' : 'No credential was supplied; anonymous usability is separate from login.' })
  } catch {
    if (controller.signal.aborted || status === undefined) {
      const code = controller.signal.aborted || /TIMEOUT|ETIMEDOUT/.test(transportCode ?? '') ? 'TIMEOUT' : ['ENOTFOUND', 'EAI_AGAIN'].includes(transportCode ?? '') ? 'DNS_FAILURE'
        : transportCode === 'ECONNREFUSED' ? 'CONNECTION_REFUSED'
          : /CERT|TLS|SSL|UNABLE_TO_VERIFY_LEAF_SIGNATURE/.test(transportCode ?? '') ? 'TLS_FAILURE' : 'NETWORK_FAILURE'
      checks.push({ id: 'connectivity', status: 'fail', code, message: 'The registry request could not complete.', exitCode: EXIT.network, next: 'Check the selected registry, DNS, network and TLS configuration.' })
      checks.push({ id: 'identity', status: 'skipped', code: 'IDENTITY_NOT_CHECKED', message: 'Credential validity could not be determined.' })
    } else if (status === 401 || status === 403) {
      checks.push({ id: 'connectivity', status: 'pass', code: 'HTTP_REACHABLE', message: 'The identity endpoint returned an HTTP response.' })
      checks.push({ id: 'identity', status: token ? 'fail' : source === 'unavailable' ? 'skipped' : 'warn',
        code: !token ? 'NO_CREDENTIAL' : status === 401 ? 'CREDENTIAL_REJECTED' : 'ACCESS_DENIED',
        message: !token ? 'No credential could be supplied; anonymous usability was not tested.'
          : status === 401 ? 'The selected credential was rejected; stored credentials were left unchanged.' : 'Identity access was denied; this does not prove the credential has expired.',
        ...(token ? { exitCode: EXIT.auth } : {}), next: source === 'argument' ? 'Retry skillhub whoami --registry <registry> without --token; replace <registry> with the selected URL.'
          : source === 'environment' ? 'Remove SKILLHUB_TOKEN from the environment, then run skillhub whoami --registry <registry>; replace <registry> with the selected URL.'
            : source === 'stored' ? 'Run skillhub logout --registry <registry>, then skillhub login --registry <registry>; replace <registry> with the selected URL.'
              : 'Run skillhub login --registry <registry> when you want to sign in; replace <registry> with the selected URL.' })
    } else {
      checks.push({ id: 'connectivity', status: 'fail', code: status >= 300 && status < 400 ? 'REDIRECT_REFUSED' : status >= 200 && status < 300 ? 'INVALID_RESPONSE' : 'HTTP_FAILURE',
        message: status >= 200 && status < 300 ? 'The registry did not return a valid CLI identity response.' : `The identity endpoint returned HTTP ${status}; redirects were not followed.`,
        exitCode: status >= 500 ? EXIT.network : EXIT.generic, next: 'Check the registry URL and server compatibility or availability.' })
      checks.push({ id: 'identity', status: 'skipped', code: 'IDENTITY_NOT_CHECKED', message: 'Credential validity could not be determined.' })
    }
  } finally { clearTimeout(timer) }
  return checks
}
