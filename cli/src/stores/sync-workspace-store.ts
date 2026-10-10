import { mkdir, readFile, rename, rm, writeFile } from 'node:fs/promises'
import { dirname, join } from 'node:path'
import { pathExists } from '../platform/paths'

export interface NamespaceSyncStateSkill {
  version: string
  fingerprint: string
}

export interface NamespaceSyncState {
  registry: string
  namespace: string
  lastSyncAt: string
  skills: Record<string, NamespaceSyncStateSkill>
}

export class SyncWorkspaceStore {
  readonly path: string

  constructor(rootDir: string) {
    this.path = join(rootDir, '.skillhub', 'namespace-sync.json')
  }

  async read(): Promise<NamespaceSyncState | null> {
    if (!(await pathExists(this.path))) return null
    return JSON.parse(await readFile(this.path, 'utf8')) as NamespaceSyncState
  }

  async write(state: NamespaceSyncState): Promise<void> {
    await mkdir(dirname(this.path), { recursive: true })
    const tempPath = `${this.path}.${process.pid}.${Date.now()}.tmp`
    try {
      await writeFile(tempPath, JSON.stringify(state, null, 2))
      await rename(tempPath, this.path)
    } finally {
      await rm(tempPath, { force: true }).catch(() => {})
    }
  }
}
