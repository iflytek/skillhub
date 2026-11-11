import { ConfigStore } from '../stores/config-store'
import { CredentialsStore } from '../stores/credentials-store'
import { SkillHubClient } from '../clients/skillhub-client'
import { resolveRegistry, resolveToken } from '../services/registry-service'
import { removeLocalSkill } from '../services/remove-service'
import { CliError } from '../shared/errors'
import { EXIT } from '../shared/constants'
import { hasExplicitNamespace, resolveSkillName } from '../shared/skill-name-parser'

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

  const { namespace, slug } = resolveSkillName(skillNameArg, options.namespace)

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
    try {
      await client.deleteRemote(namespace, slug)
    } catch (error) {
      if (error instanceof CliError
          && error.details.errorCode === 'error.apiToken.scope.missing'
          && Array.isArray(error.details.errorArgs)
          && error.details.errorArgs.includes('skill:delete')) {
        throw new CliError(error.message, error.exitCode, {
          ...error.details,
          next: 'Explicit authorization required: create a token in Web with skill:delete, '
            + 'then use `skillhub login --token <token>`. Logging in again does not grant deletion.'
        })
      }
      throw error
    }

    if (options.json) {
      return JSON.stringify({ ok: true, scope: 'remote', action: 'hard-delete', namespace, slug })
    }
    return `Removed remote skill: ${namespace}/${slug}\nAction: remote-hard-delete`
  }

  // Local remove
  const namespaceFilter = options.namespace !== undefined || hasExplicitNamespace(skillNameArg)
    ? namespace
    : undefined
  const result = await removeLocalSkill({
    registry,
    namespace: namespaceFilter,
    slug,
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
