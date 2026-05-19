import { stat } from 'node:fs/promises'
import { ConfigStore } from '../stores/config-store'
import { InventoryStore } from '../stores/inventory-store'
import { resolveRegistry } from '../services/registry-service'

export interface ListCommandOptions {
  agent?: string[] | undefined
  dir?: string | undefined
  registry?: string | undefined
  json?: boolean | undefined
}

export async function listCommand(options: ListCommandOptions): Promise<string> {
  const configStore = new ConfigStore()
  const registry = resolveRegistry(options, process.env, await configStore.read())
  const store = new InventoryStore()
  const inventory = await store.read()

  // Flatten targets
  type FlatTarget = { namespace: string; slug: string; version: string; agent: string; installDir: string; installedAt: string; status: string }
  const flat: FlatTarget[] = []

  for (const item of inventory.items) {
    if (item.registry !== registry) continue
    for (const target of item.targets) {
      if (options.agent?.length && !options.agent.includes(target.agent)) continue
      if (options.dir && !target.installDir.startsWith(options.dir)) continue

      let status = 'ok'
      try {
        await stat(target.installDir)
      } catch {
        status = 'missing'
      }

      flat.push({
        namespace: item.namespace,
        slug: item.slug,
        version: item.version,
        agent: target.agent,
        installDir: target.installDir,
        installedAt: target.installedAt,
        status
      })
    }
  }

  if (options.json) {
    return JSON.stringify({ ok: true, items: flat })
  }

  if (flat.length === 0) return 'No skills installed.'

  return flat.map(t =>
    `${t.namespace}/${t.slug}@${t.version}  ${t.agent}  ${t.installDir}  ${t.installedAt}  ${t.status}`
  ).join('\n')
}
