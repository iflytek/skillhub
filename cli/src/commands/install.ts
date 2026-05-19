import { ConfigStore } from '../stores/config-store'
import { CredentialsStore } from '../stores/credentials-store'
import { resolveRegistry, resolveToken } from '../services/registry-service'
import { installSkill } from '../services/install-service'
import { resolveInstallTargets } from '../agents/resolver'

export interface InstallCommandOptions {
  namespace?: string | undefined
  version?: string | undefined
  agent?: string[] | undefined
  dir?: string | undefined
  force?: boolean | undefined
  registry?: string | undefined
  token?: string | undefined
  json?: boolean | undefined
}

export async function installCommand(slug: string, options: InstallCommandOptions): Promise<string> {
  const configStore = new ConfigStore()
  const credentialsStore = new CredentialsStore()
  const registry = resolveRegistry(options, process.env, await configStore.read())
  const token = resolveToken(options, process.env, await credentialsStore.getToken(registry))
  const namespace = options.namespace ?? 'global'

  const targets = await resolveInstallTargets({
    cwd: process.cwd(),
    dir: options.dir,
    agents: options.agent ?? [],
    json: Boolean(options.json),
    interactive: process.stdout.isTTY === true
  })

  const result = await installSkill({
    registry, token, namespace, slug,
    version: options.version,
    targets,
    force: Boolean(options.force)
  })

  if (options.json) {
    return JSON.stringify({ ok: true, namespace, slug, installed: result.installed })
  }
  return result.installed.map(i => `Installed ${namespace}/${slug} -> ${i.dir} (${i.agent})`).join('\n')
}
