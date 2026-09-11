import { SkillHubClient } from '../clients/skillhub-client'
import { ConfigStore } from '../stores/config-store'
import { CredentialsStore } from '../stores/credentials-store'
import { CliError } from '../shared/errors'
import { EXIT } from '../shared/constants'

export class AuthService {
  private readonly clientFactory: (registry: string, token?: string) => SkillHubClient
  private readonly sleep: (milliseconds: number) => Promise<void>
  private readonly now: () => number

  constructor(
    private readonly configStore: ConfigStore,
    private readonly credentialsStore: CredentialsStore,
    options: {
      clientFactory?: (registry: string, token?: string) => SkillHubClient
      sleep?: (milliseconds: number) => Promise<void>
      now?: () => number
    } = {}
  ) {
    this.clientFactory = options.clientFactory ?? ((registry, token) => new SkillHubClient(registry, token))
    this.sleep = options.sleep ?? (milliseconds => new Promise(resolve => setTimeout(resolve, milliseconds)))
    this.now = options.now ?? Date.now
  }

  async login(registry: string, token: string): Promise<{ handle: string }> {
    const user = await this.clientFactory(registry, token).whoami()
    await this.configStore.setRegistry(registry)
    await this.credentialsStore.setToken(registry, token)
    return { handle: user.handle }
  }

  async loginWithDeviceFlow(
    registry: string,
    onVerification: (details: { userCode: string; verificationUri: string; expiresIn: number }) => Promise<void>
  ): Promise<{ handle: string }> {
    const client = this.clientFactory(registry)
    const device = await client.requestDeviceCode()
    if (
      !device.deviceCode ||
      !device.userCode ||
      !device.verificationUri ||
      !Number.isFinite(device.expiresIn) ||
      !Number.isFinite(device.interval) ||
      device.expiresIn <= 0 ||
      device.interval <= 0
    ) {
      throw new CliError('registry returned invalid device authorization data', EXIT.auth, { registry })
    }

    let verificationUri: string
    try {
      const parsedVerificationUri = new URL(device.verificationUri, `${registry}/`)
      if (parsedVerificationUri.protocol !== 'http:' && parsedVerificationUri.protocol !== 'https:') {
        throw new Error('unsupported protocol')
      }
      verificationUri = parsedVerificationUri.toString()
    } catch {
      throw new CliError('registry returned invalid device verification URL', EXIT.auth, { registry })
    }
    await onVerification({ userCode: device.userCode, verificationUri, expiresIn: device.expiresIn })

    const expiresAt = this.now() + device.expiresIn * 1_000
    const intervalMilliseconds = device.interval * 1_000
    while (this.now() < expiresAt) {
      await this.sleep(Math.min(intervalMilliseconds, Math.max(0, expiresAt - this.now())))
      if (this.now() >= expiresAt) break

      const response = await client.pollDeviceToken(device.deviceCode)
      if (response.accessToken) {
        if (response.tokenType && response.tokenType.toLowerCase() !== 'bearer') {
          throw new CliError('registry returned unsupported device token type', EXIT.auth, { registry })
        }
        return this.login(registry, response.accessToken)
      }
      if (response.error && response.error !== 'authorization_pending') {
        throw new CliError(`device authorization failed: ${response.error}`, EXIT.auth, { registry })
      }
    }

    throw new CliError('device authorization expired', EXIT.auth, { registry, next: 'run `skillhub login` again' })
  }

  async logout(registry: string): Promise<void> {
    await this.credentialsStore.deleteToken(registry)
  }
}
