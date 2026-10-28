import { isDeepStrictEqual } from 'node:util'
import { acquireSkillTargetLock } from './skill-target-lock'
import { lstat, readdir, writeFile, readFile } from 'node:fs/promises'
import { join } from 'node:path'
import { CliError } from '../shared/errors'
import { EXIT } from '../shared/constants'
import { InventoryStore, type InventoryItem, type InventoryTarget } from '../stores/inventory-store'

interface MetadataJson {
  registry: string
  namespace: string
  slug: string
  version: string
  fingerprint?: string
  agent: string
  installedAt: string
}

interface DoctorResult {
  inventoryPath: string
  backupPath: string | null
  itemsScanned: number
  targetsScanned: number
  itemsPreserved: number
  targetsPreserved: number
  skipped: Array<{ path: string; reason: string }>
  conflicts: Array<{ key: string; versions: string[] }>
}

export async function runDoctor(cwd: string, home?: string): Promise<DoctorResult> {
  const store = new InventoryStore(home)
  const skipped: DoctorResult['skipped'] = []
  const conflicts: DoctorResult['conflicts'] = []

  // Scan <cwd>/.*/skills/<slug>/.skillhub/metadata.json
  const entries = await scanMetadata(cwd, skipped)

  // Group by registry + namespace + slug
  const groups = new Map<string, { metadata: MetadataJson; installDir: string }[]>()
  for (const entry of entries) {
    const key = `${entry.metadata.registry}|${entry.metadata.namespace}|${entry.metadata.slug}`
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key)!.push(entry)
  }

  // Build scanned items
  const scannedItems: InventoryItem[] = []
  for (const group of groups.values()) {
    const first = group[0]!
    const targets: InventoryTarget[] = group.map(e => ({
      version: e.metadata.version,
      ...(e.metadata.fingerprint === undefined ? {} : { fingerprint: e.metadata.fingerprint }),
      agent: e.metadata.agent,
      rootDir: join(e.installDir, '..'),
      installDir: e.installDir,
      installedAt: e.metadata.installedAt
    }))
    scannedItems.push({
      registry: first.metadata.registry,
      namespace: first.metadata.namespace,
      slug: first.metadata.slug,
      targets
    })
  }

  // Unknown/corrupt inventories are not repaired by discarding their records.
  const oldInventory = await store.read()
  const originalBytes = await readFile(store.path).catch(error => {
    if ((error as NodeJS.ErrnoException).code === 'ENOENT') return null
    throw error
  })
  await store.ensureWritable()

  // Collect scanned installDirs
  const scannedInstallDirs = new Set<string>()
  for (const item of scannedItems) {
    for (const target of item.targets) {
      scannedInstallDirs.add(target.installDir)
    }
  }

  // Preserve old items where installDir is not in scanned set
  // This allows the same slug to coexist in different installDirs (e.g., different projects)
  const preservedItems: InventoryItem[] = []
  for (const oldItem of oldInventory.items) {
    const preservedTargets = oldItem.targets.filter(t => !scannedInstallDirs.has(t.installDir))
    if (preservedTargets.length > 0) {
      preservedItems.push({
        ...oldItem,
        targets: preservedTargets
      })
    }
  }

  let backupPath: string | null = null
  if (originalBytes !== null) {
    backupPath = `${store.path}.bak`
    await writeFile(backupPath, originalBytes)
  }
  const releases: Array<() => Promise<void>> = []
  try {
    for (const item of scannedItems.flatMap(item => item.targets.map(target => ({ item, target })))
      .sort((a, b) => a.target.installDir.localeCompare(b.target.installDir))) {
      releases.push(await acquireSkillTargetLock(item.target.rootDir, item.item.slug))
    }
    const refreshed = await scanMetadata(cwd, [])
    if (!isDeepStrictEqual(refreshed, entries)) throw new Error('Doctor scan changed; retry inspection')
    await store.mutateAtomic(current => {
      for (const scanned of scannedItems) {
        let existing = current.items.find(item => item.registry === scanned.registry && item.namespace === scanned.namespace && item.slug === scanned.slug)
        if (!existing) { existing = { ...scanned, targets: [] }; current.items.push(existing) }
        for (const target of scanned.targets) {
          const prior = oldInventory.items.flatMap(item => item.targets.filter(saved => saved.installDir === target.installDir).map(saved => ({ registry: item.registry, namespace: item.namespace, slug: item.slug, target: saved })))
          const actual = current.items.flatMap(item => item.targets.filter(saved => saved.installDir === target.installDir).map(saved => ({ registry: item.registry, namespace: item.namespace, slug: item.slug, target: saved })))
          if (!isDeepStrictEqual(prior, actual)) throw new Error('Doctor inventory changed; retry inspection')
          if (actual.some(owner => owner.registry !== scanned.registry || owner.namespace !== scanned.namespace || owner.slug !== scanned.slug)) throw new Error('Doctor source ownership conflict')
          const index = existing.targets.findIndex(saved => saved.installDir === target.installDir)
          if (index >= 0) {
            existing.targets[index] = { ...target, installationState: existing.targets[index]!.installationState ?? 'enabled',
              installedBy: existing.targets[index]!.installedBy ?? ['direct'] }
          } else existing.targets.push({ ...target, installedBy: ['direct'] })
        }
      }
    })
  } finally { for (const release of releases.reverse()) await release() }


  return {
    inventoryPath: store.path,
    backupPath,
    itemsScanned: scannedItems.length,
    targetsScanned: scannedItems.reduce((sum, item) => sum + item.targets.length, 0),
    itemsPreserved: preservedItems.length,
    targetsPreserved: preservedItems.reduce((sum, item) => sum + item.targets.length, 0),
    skipped,
    conflicts
  }
}

async function scanMetadata(cwd: string, skipped: DoctorResult['skipped']): Promise<Array<{ metadata: MetadataJson; installDir: string }>> {
  const results: Array<{ metadata: MetadataJson; installDir: string }> = []

  let topEntries: string[]
  try {
    topEntries = await readdir(cwd)
  } catch {
    throw new CliError('cannot read project directory', EXIT.filesystem, { path: cwd })
  }

  for (const dirName of topEntries) {
    if (!dirName.startsWith('.')) continue
    const agentDir = join(cwd, dirName)
    try {
      const st = await lstat(agentDir)
      if (st.isSymbolicLink() || !st.isDirectory()) {
        skipped.push({ path: agentDir, reason: 'not a regular directory' })
        continue
      }
    } catch {
      skipped.push({ path: agentDir, reason: 'cannot stat' })
      continue
    }
    const skillsDir = join(agentDir, 'skills')
    let slugDirs: string[]
    try {
      slugDirs = await readdir(skillsDir)
    } catch {
      continue
    }

    for (const slug of slugDirs) {
      const slugPath = join(skillsDir, slug)
      try {
        const st = await lstat(slugPath)
        if (st.isSymbolicLink() || !st.isDirectory()) {
          skipped.push({ path: slugPath, reason: 'not a regular directory' })
          continue
        }
      } catch {
        skipped.push({ path: slugPath, reason: 'cannot stat' })
        continue
      }
      // A renamed entry is never sufficient evidence to reconstruct disabled ownership.
      try {
        await lstat(join(slugPath, 'SKILL.md.disabled'))
        skipped.push({ path: slugPath, reason: 'disabled entry requires its existing inventory record' })
        continue
      } catch (error) {
        if ((error as NodeJS.ErrnoException).code !== 'ENOENT') {
          skipped.push({ path: slugPath, reason: 'cannot verify disabled entry' })
          continue
        }
      }
      const skillhubDir = join(slugPath, '.skillhub')
      try {
        const skillhubSt = await lstat(skillhubDir)
        if (skillhubSt.isSymbolicLink() || !skillhubSt.isDirectory()) {
          skipped.push({ path: slugPath, reason: '.skillhub is not a regular directory' })
          continue
        }
      } catch {
        skipped.push({ path: slugPath, reason: 'no .skillhub directory' })
        continue
      }
      const metadataPath = join(skillhubDir, 'metadata.json')
      try {
        const content = await readFile(metadataPath, 'utf-8')
        const metadata = JSON.parse(content) as MetadataJson
        if (!metadata.registry || !metadata.namespace || !metadata.slug || !metadata.version || !metadata.agent || !metadata.installedAt) {
          skipped.push({ path: slugPath, reason: 'incomplete metadata' })
          continue
        }
        results.push({ metadata, installDir: slugPath })
      } catch {
        skipped.push({ path: slugPath, reason: 'no .skillhub/metadata.json' })
      }
    }
  }

  return results
}
