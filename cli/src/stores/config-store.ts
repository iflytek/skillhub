import { readFile, writeFile } from 'node:fs/promises'
import { dirname } from 'node:path'
import { joinPath, userStateDir, ensureDir, pathExists } from '../platform/paths'

export interface CliConfig {
  registry?: string
  defaultAgent?: string
  lastUpdateCheckAt?: string
}

export class ConfigStore {
  readonly path: string

  constructor(home?: string) {
    this.path = joinPath(userStateDir(home), 'config.json')
  }

  async read(): Promise<CliConfig> {
    if (!(await pathExists(this.path))) return {}
    return JSON.parse(await readFile(this.path, 'utf-8')) as CliConfig
  }

  async write(config: CliConfig): Promise<void> {
    await ensureDir(dirname(this.path))
    await writeFile(this.path, JSON.stringify(config, null, 2))
  }

  async setRegistry(registry: string): Promise<void> {
    await this.write({ ...(await this.read()), registry })
  }
}
