import { mkdir, rm, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { SkillHubClient } from '../clients/skillhub-client'
import { InventoryStore } from '../stores/inventory-store'
import { CliError } from '../shared/errors'
import { EXIT } from '../shared/constants'
import { extractZip } from '../platform/archive'
import { pathExists } from '../platform/paths'
import type { AgentCandidate } from '../agents/types'

export interface InstallOptions {
  registry: string
  token?: string | undefined
  namespace: string
  slug: string
  version?: string | undefined
  targets: AgentCandidate[]
  force: boolean
  home?: string | undefined
}

export async function installSkill(options: InstallOptions): Promise<{ installed: Array<{ agent: string; dir: string }> }> {
  const client = new SkillHubClient(options.registry, options.token)
  const resolved = await client.resolve(options.namespace, options.slug, options.version)
  const response = await client.download(options.namespace, options.slug, resolved.version)
  const buffer = await response.arrayBuffer()

  const installed: Array<{ agent: string; dir: string }> = []
  const store = new InventoryStore(options.home)

  for (const target of options.targets) {
    const skillDir = join(target.rootDir, options.slug)

    if (await pathExists(skillDir) && !options.force) {
      throw new CliError(`skill already installed at ${skillDir}`, EXIT.filesystem, {
        path: skillDir,
        next: 'pass --force to overwrite'
      })
    }

    if (await pathExists(skillDir) && options.force) {
      await store.removeTargetsByInstallDir(skillDir)
      await rm(skillDir, { recursive: true, force: true })
    }

    // Create skill directory and extract into a clean skill-specific directory.
    await mkdir(skillDir, { recursive: true })
    await extractZip(buffer, skillDir)

    // Write .skillhub/metadata.json
    const metaDir = join(skillDir, '.skillhub')
    await mkdir(metaDir, { recursive: true })
    await writeFile(join(metaDir, 'metadata.json'), JSON.stringify({
      registry: options.registry,
      namespace: options.namespace,
      slug: options.slug,
      version: resolved.version,
      agent: target.agent,
      installedAt: new Date().toISOString()
    }, null, 2))

    // Update inventory
    await store.upsertTarget(options.registry, options.namespace, options.slug, resolved.version, {
      agent: target.agent,
      rootDir: target.rootDir,
      installDir: skillDir,
      installedAt: new Date().toISOString()
    })

    installed.push({ agent: target.agent, dir: skillDir })
  }

  return { installed }
}
