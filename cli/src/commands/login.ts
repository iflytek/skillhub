import { ConfigStore } from '../stores/config-store'
import { CredentialsStore } from '../stores/credentials-store'
import { AuthService } from '../services/auth-service'
import { resolveRegistry, resolveToken } from '../services/registry-service'

export interface LoginCommandOptions {
  registry?: string
  token?: string
  json?: boolean
}

export async function loginCommand(options: LoginCommandOptions): Promise<string> {
  const configStore = new ConfigStore()
  const credentialsStore = new CredentialsStore()
  const registry = resolveRegistry(options, process.env, await configStore.read())
  const token = resolveToken(options, process.env, await credentialsStore.getToken(registry))
  const result = await new AuthService(configStore, credentialsStore).login(registry, token)
  return options.json
    ? JSON.stringify({ ok: true, registry, handle: result.handle })
    : `Logged in to ${registry} as ${result.handle}`
}
