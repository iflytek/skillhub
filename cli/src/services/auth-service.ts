import { SkillHubClient } from '../clients/skillhub-client'
import { ConfigStore } from '../stores/config-store'
import { CredentialsStore } from '../stores/credentials-store'
import { CliError } from '../shared/errors'
import { EXIT } from '../shared/constants'

export class AuthService {
  constructor(
    private readonly configStore: ConfigStore,
    private readonly credentialsStore: CredentialsStore
  ) {}

  async login(registry: string, token?: string): Promise<{ handle: string }> {
    if (!token) {
      throw new CliError('token is required', EXIT.usage, { next: 'pass --token, set SKILLHUB_TOKEN, or use interactive login' })
    }
    const user = await new SkillHubClient(registry, token).whoami()
    await this.configStore.setRegistry(registry)
    await this.credentialsStore.setToken(registry, token)
    return { handle: user.handle }
  }

  async logout(registry: string): Promise<void> {
    await this.credentialsStore.deleteToken(registry)
  }
}
