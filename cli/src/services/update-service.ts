import { gt as semverGt } from 'semver'
import { CLI_PACKAGE_NAME } from '../shared/constants'
import type { InstallMode } from '../platform/package-manager'
import type { UpdaterRunResult } from '../platform/updater'

export interface UpdateServiceDeps {
  currentVersion: string
  latestVersion: () => Promise<string>
  detectInstallMode: () => InstallMode
  run: (command: readonly string[]) => Promise<UpdaterRunResult>
}

export interface UpdateOptions {
  checkOnly: boolean
}

export interface UpdateResult {
  updated: boolean
  available: boolean
  currentVersion?: string | undefined
  latestVersion?: string | undefined
  next?: string | undefined
  error?: string | undefined
}

export class UpdateService {
  constructor(private readonly deps: UpdateServiceDeps) {}

  async update(options: UpdateOptions): Promise<UpdateResult> {
    const current = this.deps.currentVersion
    const latest = await this.deps.latestVersion()

    // No update available (use semver comparison)
    if (!semverGt(latest, current)) {
      return {
        updated: false,
        available: false,
        currentVersion: current,
        latestVersion: latest
      }
    }

    // Update available but only checking
    if (options.checkOnly) {
      return {
        updated: false,
        available: true,
        currentVersion: current,
        latestVersion: latest
      }
    }

    // Determine install mode and update strategy
    const mode = this.deps.detectInstallMode()

    switch (mode) {
      case 'npx':
        return {
          updated: false,
          available: true,
          currentVersion: current,
          latestVersion: latest,
          next: `Run: npx ${CLI_PACKAGE_NAME}@latest <command> or install globally: npm install -g ${CLI_PACKAGE_NAME}`
        }

      case 'npm-global': {
        const result = await this.deps.run(['npm', 'install', '-g', `${CLI_PACKAGE_NAME}@latest`])
        return {
          updated: result.success,
          available: true,
          currentVersion: current,
          latestVersion: latest,
          error: result.success ? undefined : result.output
        }
      }

      case 'bun-global': {
        const result = await this.deps.run(['bun', 'add', '-g', `${CLI_PACKAGE_NAME}@latest`])
        return {
          updated: result.success,
          available: true,
          currentVersion: current,
          latestVersion: latest,
          error: result.success ? undefined : result.output
        }
      }

      case 'unknown':
      default:
        return {
          updated: false,
          available: true,
          currentVersion: current,
          latestVersion: latest,
          next: `Update manually: npm install -g ${CLI_PACKAGE_NAME}@latest or bun add -g ${CLI_PACKAGE_NAME}@latest`
        }
    }
  }
}
