import { ConfigStore } from '../stores/config-store'
import { CredentialsStore } from '../stores/credentials-store'
import { AuthService } from '../services/auth-service'
import { resolveRegistry, resolveTokenWithSource, type TokenSource } from '../services/registry-service'
import { openExternalUrl } from '../platform/browser'
import { CliError } from '../shared/errors'
import { EXIT } from '../shared/constants'

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
  const resolvedToken = resolveTokenWithSource(options, process.env, await credentialsStore.getToken(registry))
  const authService = new AuthService(configStore, credentialsStore)
  const onVerification = async (details: { userCode: string; verificationUri: string; expiresIn: number }) => {
    const opened = options.noOpen ? false : openExternalUrl(details.verificationUri)
    const message = options.json
      ? JSON.stringify({ event: 'device_authorization', ...details, browserOpened: opened })
      : [
          `Authorize this device at: ${details.verificationUri}`,
          `Code: ${details.userCode}`,
          opened ? 'Browser launch requested. If no browser opened, use the URL above.' : 'Open the URL in a browser to continue.',
          `Waiting for authorization (expires in ${details.expiresIn}s)...`
        ].join('\n')
    process.stderr.write(`${message}\n`)
  }

  let result: { handle: string }
  if (!resolvedToken) {
    result = await authService.loginWithDeviceFlow(registry, onVerification)
  } else if (resolvedToken.source === 'stored') {
    result = await authService.loginWithStoredCredential(registry, resolvedToken.token, onVerification, () => {
      if (!options.json) {
        process.stderr.write(`The saved credential for ${registry} was rejected; starting browser sign-in.\n`)
      }
    })
  } else {
    try {
      result = await authService.login(registry, resolvedToken.token)
    } catch (error) {
      throw describeRejectedTokenInput(error, resolvedToken.source)
    }
  }

  return options.json
    ? JSON.stringify({ ok: true, registry, handle: result.handle })
    : `Logged in to ${registry} as ${result.handle}`
}

/** Name the input that supplied a token the registry rejected, instead of the
 * generic "run `skillhub login`" advice that repeats the failing command. */
function describeRejectedTokenInput(error: unknown, source: TokenSource): unknown {
  if (!(error instanceof CliError) || error.exitCode !== EXIT.auth || error.httpStatus !== 401) return error
  const next = source === 'argument'
    ? 'the token from --token was rejected. Retry without --token to sign in with the browser: skillhub login --registry <registry>; replace <registry> with the selected URL.'
    : 'the token from SKILLHUB_TOKEN was rejected. Unset SKILLHUB_TOKEN to sign in with the browser: skillhub login --registry <registry>; replace <registry> with the selected URL.'
  return new CliError(error.message, error.exitCode, { ...error.details, next }, error.httpStatus)
}
