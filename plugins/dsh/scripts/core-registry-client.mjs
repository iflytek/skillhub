import { SkillHubClient as RegistryClient } from '../../../cli/src/clients/skillhub-client.ts';
import { registryCredentials, registryFetch } from './core-registry-transport.ts';
export * from '../../../cli/src/clients/skillhub-client.ts';

export class SkillHubClient extends RegistryClient {
  constructor(registry, token, fetchImpl = fetch) {
    super(registry, token, registryFetch(registry, token ?? registryCredentials.getStore()?.(registry), fetchImpl));
  }
}
