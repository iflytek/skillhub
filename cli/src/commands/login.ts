import { ConfigStore } from '../stores/config-store'
import { CredentialsStore } from '../stores/credentials-store'
import { AuthService } from '../services/auth-service'
import { resolveRegistry, resolveToken } from '../services/registry-service'
import { openExternalUrl } from '../platform/browser'

export interface LoginCommandOptions {
  registry?: string
  token?: string
  json?: boolean
  noOpen?: boolean
}

export async function loginCommand(options: LoginCommandOptions): Promise<string> {
  const configStore = new ConfigStore()
  const credentialsStore = new CredentialsStore()
  const registry = resolveRegistry(options, process.env, await configStore.read())
  const token = resolveToken(options, process.env, await credentialsStore.getToken(registry))
  const authService = new AuthService(configStore, credentialsStore)
  const result = token
    ? await authService.login(registry, token)
    : await authService.loginWithDeviceFlow(registry, async details => {
        const opened = options.noOpen ? false : openExternalUrl(details.verificationUri)
        const message = options.json
          ? JSON.stringify({ event: 'device_authorization', ...details, browserOpened: opened })
          : [
              `Authorize this device at: ${details.verificationUri}`,
              `Code: ${details.userCode}`,
              opened ? 'A browser window was opened.' : 'Open the URL in a browser to continue.',
              `Waiting for authorization (expires in ${details.expiresIn}s)...`
            ].join('\n')
        process.stderr.write(`${message}\n`)
      })
  return options.json
    ? JSON.stringify({ ok: true, registry, handle: result.handle })
    : `Logged in to ${registry} as ${result.handle}`
}
