import { ConfigStore } from '../stores/config-store'
import { CredentialsStore } from '../stores/credentials-store'
import { AuthService } from '../services/auth-service'
import { resolveRegistry } from '../services/registry-service'

export interface LogoutCommandOptions {
  registry?: string
  json?: boolean
}

export async function logoutCommand(options: LogoutCommandOptions): Promise<string> {
  const configStore = new ConfigStore()
  const credentialsStore = new CredentialsStore()
  const registry = resolveRegistry(options, process.env, await configStore.read())
  await new AuthService(configStore, credentialsStore).logout(registry)
  return options.json
    ? JSON.stringify({ ok: true, registry })
    : `Logged out from ${registry}`
}
