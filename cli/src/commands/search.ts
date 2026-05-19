import { SkillHubClient } from '../clients/skillhub-client'
import { ConfigStore } from '../stores/config-store'
import { CredentialsStore } from '../stores/credentials-store'
import { resolveRegistry, resolveToken } from '../services/registry-service'

export interface SearchCommandOptions {
  registry?: string
  token?: string
  limit?: number
  json?: boolean
}

export async function searchCommand(query: string, options: SearchCommandOptions): Promise<string> {
  const configStore = new ConfigStore()
  const credentialsStore = new CredentialsStore()
  const registry = resolveRegistry(options, process.env, await configStore.read())
  const token = resolveToken(options, process.env, await credentialsStore.getToken(registry))
  const client = new SkillHubClient(registry, token)
  const result = await client.search(query ?? '', options.limit ?? 20)
  if (options.json) {
    return JSON.stringify({ ok: true, items: result.items, total: result.total })
  }
  if (result.items.length === 0) return 'No skills found.'
  return result.items
    .map(item => `${item.namespace}/${item.slug}  ${item.latestVersion ?? '-'}  ${item.summary ?? ''}`)
    .join('\n')
}
