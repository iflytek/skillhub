import { CLI_VERSION, EXIT } from '../shared/constants'
import { CliError } from '../shared/errors'
import { printResult } from '../shared/output'
import { NpmRegistryClient } from '../clients/npm-registry-client'
import { UpdateService } from '../services/update-service'
import { detectInstallMode, type InstallMode } from '../platform/package-manager'
import { runUpdateCommand } from '../platform/updater'
import type { UpdaterRunResult } from '../platform/updater'

export interface UpdateCommandOptions {
  check?: boolean
  json?: boolean
}

/**
 * Injectable runtime dependencies. Defaults are wired to real npm/shell code
 * for production. Unit tests pass fakes so they don't need process-global
 * module mocks (which leak across files inside Bun's test runner).
 */
export interface UpdateCommandDeps {
  latestVersion?: () => Promise<string>
  detectInstallMode?: () => InstallMode
  run?: (command: readonly string[]) => Promise<UpdaterRunResult>
}

export async function updateCommand(
  options: UpdateCommandOptions,
  deps: UpdateCommandDeps = {}
): Promise<string> {
  const json = Boolean(options.json)
  const checkOnly = Boolean(options.check)

  const latestVersion = deps.latestVersion ?? (() => new NpmRegistryClient().latestVersion())
  const detectMode = deps.detectInstallMode ?? (() => detectInstallMode())
  const run = deps.run ?? runUpdateCommand

  const service = new UpdateService({
    currentVersion: CLI_VERSION,
    latestVersion,
    detectInstallMode: detectMode,
    run
  })

  const result = await service.update({ checkOnly })

  if (!result.available) {
    return printResult(
      json
        ? { ok: true, upToDate: true, version: result.currentVersion }
        : `Already up to date (${result.currentVersion})`,
      json
    )
  }

  if (result.updated) {
    return printResult(
      json
        ? { ok: true, updated: true, from: result.currentVersion, to: result.latestVersion }
        : `Updated skillhub ${result.currentVersion} -> ${result.latestVersion}`,
      json
    )
  }

  if (result.error) {
    throw new CliError(result.error, EXIT.generic, { from: result.currentVersion, to: result.latestVersion })
  }

  // Not updated but available (npx / unknown / checkOnly)
  const lines = [`Update available: ${result.currentVersion} -> ${result.latestVersion}`]
  if (result.next) {
    lines.push(result.next)
  }

  return printResult(
    json
      ? { ok: true, available: true, from: result.currentVersion, to: result.latestVersion, next: result.next }
      : lines.join('\n'),
    json
  )
}
