import { readFile, writeFile } from 'node:fs/promises'
import { dirname } from 'node:path'
import { joinPath, userStateDir, ensureDir, applyCredentialPermissions, pathExists } from '../platform/paths'

interface CredentialsFile extends Record<string, unknown> {
  tokens?: Record<string, unknown>
}

export class CredentialsStore {
  readonly path: string

  constructor(home?: string) {
    this.path = joinPath(userStateDir(home), 'credentials.json')
  }

  async read(): Promise<CredentialsFile> {
    if (!(await pathExists(this.path))) return {}
    return JSON.parse(await readFile(this.path, 'utf-8')) as CredentialsFile
  }

  async getToken(registry: string): Promise<string | undefined> {
    const token = (await this.read()).tokens?.[registry]
    return typeof token === 'string' ? token : undefined
  }

  async setToken(registry: string, token: string): Promise<void> {
    const current = await this.read()
    await ensureDir(dirname(this.path))
    await writeFile(this.path, JSON.stringify({
      ...current,
      tokens: { ...current.tokens, [registry]: token }
    }, null, 2))
    await applyCredentialPermissions(this.path)
  }

  async deleteToken(registry: string): Promise<void> {
    const current = await this.read()
    const tokens = { ...current.tokens }
    delete tokens[registry]
    await ensureDir(dirname(this.path))
    await writeFile(this.path, JSON.stringify({ ...current, tokens }, null, 2))
    await applyCredentialPermissions(this.path)
  }
}
