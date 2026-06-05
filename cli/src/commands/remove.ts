import { ConfigStore } from '../stores/config-store'
import { CredentialsStore } from '../stores/credentials-store'
import { SkillHubClient } from '../clients/skillhub-client'
import { resolveRegistry, resolveToken } from '../services/registry-service'
import { removeLocalSkill } from '../services/remove-service'
import { CliError } from '../shared/errors'
import { EXIT } from '../shared/constants'
import { parseSkillName } from '../shared/skill-name-parser'

export interface RemoveCommandOptions {
  agent?: string[] | undefined
  all?: boolean | undefined
  remote?: boolean | undefined
  hard?: boolean | undefined
  namespace?: string | undefined
  registry?: string | undefined
  token?: string | undefined
  json?: boolean | undefined
}

export async function removeCommand(skillNameArg: string, options: RemoveCommandOptions): Promise<string> {
  if (options.all && options.agent?.length) {
    throw new CliError('--all cannot be used with --agent', EXIT.usage)
  }
  if (options.remote && (options.agent?.length || options.all)) {
    throw new CliError('--remote cannot be used with --agent or --all', EXIT.usage)
  }

  const configStore = new ConfigStore()
  const credentialsStore = new CredentialsStore()
  const registry = resolveRegistry(options, process.env, await configStore.read())

  const parsed = parseSkillName(skillNameArg)
  const namespace = options.namespace ?? parsed.namespace
  const slug = parsed.slug

  if (options.remote) {
    const token = resolveToken(options, process.env, await credentialsStore.getToken(registry))

    if (!options.hard && process.stdout.isTTY) {
      const prompts = await import('prompts')
      const { confirm } = await prompts.default({
        type: 'confirm',
        name: 'confirm',
        message: `Delete remote skill ${namespace}/${slug}?`,
        initial: false
      })
      if (!confirm) {
        throw new CliError('remote delete cancelled', EXIT.generic)
      }
    } else if (!options.hard && !process.stdout.isTTY) {
      throw new CliError('non-interactive remote delete requires --hard', EXIT.usage)
    }

    const client = new SkillHubClient(registry, token)
    await client.deleteRemote(namespace, slug)

    if (options.json) {
      return JSON.stringify({ ok: true, scope: 'remote', action: 'hard-delete', namespace, slug })
    }
    return `Removed remote skill: ${namespace}/${slug}\nAction: remote-hard-delete`
  }

  // Local remove
  const result = await removeLocalSkill({
    registry, slug,
    agents: options.agent,
    all: options.all
  })

  if (options.json) {
    return JSON.stringify({ ok: true, scope: 'local', removed: result.removed })
  }
  return result.removed.map(r =>
    r.existed
      ? `Removed ${r.namespace}/${slug} from ${r.dir} (${r.agent})`
      : `Cleaned stale record for ${r.namespace}/${slug} at ${r.dir} (${r.agent}, directory already missing)`
  ).join('\n')
}
