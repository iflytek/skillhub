import { SkillHubClient } from '../clients/skillhub-client'
import { ConfigStore } from '../stores/config-store'
import { CredentialsStore } from '../stores/credentials-store'
import { resolveRegistry, resolveToken } from '../services/registry-service'
import { CliError } from '../shared/errors'
import { EXIT } from '../shared/constants'

export interface WhoamiCommandOptions {
  registry?: string
  token?: string
  json?: boolean
}

export async function whoamiCommand(options: WhoamiCommandOptions): Promise<string> {
  const configStore = new ConfigStore()
  const credentialsStore = new CredentialsStore()
  const registry = resolveRegistry(options, process.env, await configStore.read())
  const token = resolveToken(options, process.env, await credentialsStore.getToken(registry))
  if (!token) {
    throw new CliError('not logged in', EXIT.auth, { registry, next: 'run `skillhub login`' })
  }
  const user = await new SkillHubClient(registry, token).whoami()
  if (options.json) {
    return JSON.stringify({
      ok: true, registry, handle: user.handle, displayName: user.displayName,
      capabilities: user.capabilities ?? null, legacyCompat: user.legacyCompat ?? false,
      expiresAt: user.expiresAt ?? null
    })
  }
  return [
    `Registry: ${registry}`,
    `Handle: ${user.handle}`,
    `Name: ${user.displayName}`,
    `Capabilities: ${user.capabilities?.join(', ') ?? 'unavailable (older server or session)'}`,
    `Legacy compatibility: ${user.legacyCompat ? 'yes' : 'no'}`,
    `Expires: ${user.expiresAt ?? 'not reported'}`
  ].join('\n')
}
