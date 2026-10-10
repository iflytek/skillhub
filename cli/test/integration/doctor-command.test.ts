import { describe, expect, test } from 'bun:test'
import { mkdir, writeFile, readFile } from 'node:fs/promises'
import { join } from 'node:path'
import { createTempHome } from '../helpers/temp-env'
import { runCli } from '../helpers/run-cli'

/**
 * Seed a single skill metadata file under the given scan root.
 * Doctor scans its cwd for `.<agent>/skills/<slug>/.skillhub/metadata.json`,
 * so we seed fixtures inside `scanRoot` (a temp dir) and pass that same dir
 * as the CLI's cwd — no writes into the repo working tree.
 */
async function seedSkill(scanRoot: string, options: {
  agentDir: string
  slug: string
  metadata: {
    registry: string
    namespace: string
    slug: string
    version: string
    agent: string
    installedAt: string
  }
}): Promise<void> {
  const metaDir = join(scanRoot, options.agentDir, 'skills', options.slug, '.skillhub')
  await mkdir(metaDir, { recursive: true })
  await writeFile(join(metaDir, 'metadata.json'), JSON.stringify(options.metadata))
}

describe('doctor command', () => {
  test('doctor --json empty home exits 0 and returns parseable JSON', async () => {
    const { home, cwd } = await createTempHome()

    const result = await runCli(['doctor', '--json'], {
      HOME: home,
      USERPROFILE: home
    }, { cwd })

    expect(result.exitCode).toBe(0)

    const json = JSON.parse(result.stdout)
    expect(json.ok).toBe(true)
    expect(typeof json.inventoryPath).toBe('string')
    expect(json.inventoryPath).toContain('.skillhub')
    expect(json.inventoryPath).toContain('inventory.json')
    expect(json.backupPath).toBeNull()
    expect(json.itemsScanned).toBe(0)
    expect(json.targetsScanned).toBe(0)
    expect(json.itemsPreserved).toBe(0)
    expect(json.targetsPreserved).toBe(0)
    expect(Array.isArray(json.skipped)).toBe(true)
    expect(Array.isArray(json.conflicts)).toBe(true)
  })

  test('doctor human output empty home exits 0 and shows Inventory line', async () => {
    const { home, cwd } = await createTempHome()

    const result = await runCli(['doctor'], {
      HOME: home,
      USERPROFILE: home
    }, { cwd })

    expect(result.exitCode).toBe(0)
    expect(result.stdout).toContain('Inventory:')
    expect(result.stdout).toContain('.skillhub')
    expect(result.stdout).toContain('inventory.json')
    expect(result.stdout).toContain('Scanned: 0 items, 0 targets')
    expect(result.stdout).not.toContain('Backup:')
  })

  test('doctor rebuilds inventory.json from seeded metadata files', async () => {
    const { home, cwd } = await createTempHome()

    await seedSkill(cwd, {
      agentDir: '.codex',
      slug: 'pdf-parser',
      metadata: {
        registry: 'https://skill.xfyun.cn',
        namespace: 'global',
        slug: 'pdf-parser',
        version: '1.2.0',
        agent: 'codex',
        installedAt: '2026-04-20T12:00:00Z'
      }
    })

    const result = await runCli(['doctor'], {
      HOME: home,
      USERPROFILE: home
    }, { cwd })

    expect(result.exitCode).toBe(0)

    const inventoryPath = join(home, '.skillhub', 'inventory.json')
    const raw = await readFile(inventoryPath, 'utf-8')
    const inventory = JSON.parse(raw) as { items: Array<{
      namespace: string
      slug: string
      version: string
      registry: string
    }> }

    expect(inventory.items).toHaveLength(1)
    expect(inventory.items[0]).toMatchObject({
      registry: 'https://skill.xfyun.cn',
      namespace: 'global',
      slug: 'pdf-parser',
      version: '1.2.0'
    })
  })

  test('doctor --json reflects rebuilt inventory items in output', async () => {
    const { home, cwd } = await createTempHome()

    await seedSkill(cwd, {
      agentDir: '.claude',
      slug: 'image-resizer',
      metadata: {
        registry: 'https://skill.xfyun.cn',
        namespace: 'global',
        slug: 'image-resizer',
        version: '2.0.0',
        agent: 'claude-code',
        installedAt: '2026-04-21T09:00:00Z'
      }
    })

    const result = await runCli(['doctor', '--json'], {
      HOME: home,
      USERPROFILE: home
    }, { cwd })

    expect(result.exitCode).toBe(0)

    const json = JSON.parse(result.stdout)
    expect(json.ok).toBe(true)
    expect(json.itemsScanned).toBe(1)
    expect(json.targetsScanned).toBe(1)
    expect(json.backupPath).toBeNull()
    expect(Array.isArray(json.skipped)).toBe(true)
    expect(json.conflicts).toHaveLength(0)
    expect(typeof json.inventoryPath).toBe('string')
    expect(json.inventoryPath).toContain('inventory.json')
  })

  // -------------------------------------------------------------------------
  // P1: same registry+namespace+slug appearing in two agent dirs with
  // different versions surfaces in `conflicts` and is excluded from items.
  // -------------------------------------------------------------------------
  test('doctor reports conflicts when two agent dirs disagree on version', async () => {
    const { home, cwd } = await createTempHome()

    // Two installs of the same global/pdf-parser with mismatched versions.
    await seedSkill(cwd, {
      agentDir: '.codex',
      slug: 'pdf-parser',
      metadata: {
        registry: 'https://skill.xfyun.cn',
        namespace: 'global',
        slug: 'pdf-parser',
        version: '1.0.0',
        agent: 'codex',
        installedAt: '2026-04-20T12:00:00Z'
      }
    })
    await seedSkill(cwd, {
      agentDir: '.claude',
      slug: 'pdf-parser',
      metadata: {
        registry: 'https://skill.xfyun.cn',
        namespace: 'global',
        slug: 'pdf-parser',
        version: '2.0.0',
        agent: 'claude-code',
        installedAt: '2026-04-21T09:00:00Z'
      }
    })

    const result = await runCli(['doctor', '--json'], {
      HOME: home,
      USERPROFILE: home
    }, { cwd })

    expect(result.exitCode).toBe(0)
    const json = JSON.parse(result.stdout) as {
      ok: boolean
      itemsScanned: number
      targetsScanned: number
      conflicts: Array<{ key: string; versions: string[] }>
    }
    expect(json.ok).toBe(true)
    // Conflicting group is dropped from items, recorded as a conflict.
    expect(json.itemsScanned).toBe(0)
    expect(json.targetsScanned).toBe(0)
    expect(json.conflicts).toHaveLength(1)
    expect(json.conflicts[0]?.key).toBe('https://skill.xfyun.cn|global|pdf-parser')
    expect(json.conflicts[0]?.versions.sort()).toEqual(['1.0.0', '2.0.0'])

    // The persisted inventory must mirror the JSON output: no items.
    const inventory = JSON.parse(
      await readFile(join(home, '.skillhub', 'inventory.json'), 'utf-8')
    ) as { items: unknown[] }
    expect(inventory.items).toHaveLength(0)
  })

  // -------------------------------------------------------------------------
  // P1: malformed metadata (unparseable JSON, or missing required fields)
  // is reported in `skipped` and does not produce inventory entries. Two
  // distinct failure modes are seeded to exercise both branches in
  // scanMetadata: JSON.parse throw and the post-parse field check.
  // -------------------------------------------------------------------------
  test('doctor reports skipped entries for malformed and incomplete metadata', async () => {
    const { home, cwd } = await createTempHome()

    // (1) Bad JSON: triggers the catch around JSON.parse → "no .skillhub/metadata.json"
    //     because the catch block is shared with the readFile failure path.
    const badJsonDir = join(cwd, '.codex', 'skills', 'broken-json', '.skillhub')
    await mkdir(badJsonDir, { recursive: true })
    await writeFile(join(badJsonDir, 'metadata.json'), '{ this is not json')

    // (2) Incomplete fields: parses fine but is missing `version`.
    const incompleteDir = join(cwd, '.claude', 'skills', 'incomplete', '.skillhub')
    await mkdir(incompleteDir, { recursive: true })
    await writeFile(
      join(incompleteDir, 'metadata.json'),
      JSON.stringify({
        registry: 'https://skill.xfyun.cn',
        namespace: 'global',
        slug: 'incomplete',
        // version intentionally missing
        agent: 'claude-code',
        installedAt: '2026-04-22T10:00:00Z'
      })
    )

    // (3) A valid sibling so we can prove skipped entries don't poison the
    //     surrounding scan — the valid skill should still land in inventory.
    await seedSkill(cwd, {
      agentDir: '.codex',
      slug: 'good-skill',
      metadata: {
        registry: 'https://skill.xfyun.cn',
        namespace: 'global',
        slug: 'good-skill',
        version: '1.0.0',
        agent: 'codex',
        installedAt: '2026-04-22T10:00:00Z'
      }
    })

    const result = await runCli(['doctor', '--json'], {
      HOME: home,
      USERPROFILE: home
    }, { cwd })

    expect(result.exitCode).toBe(0)
    const json = JSON.parse(result.stdout) as {
      ok: boolean
      itemsScanned: number
      skipped: Array<{ path: string; reason: string }>
    }
    expect(json.ok).toBe(true)

    // Both broken entries should be in skipped, the good one in items.
    const broken = json.skipped.find(s => s.path.endsWith('broken-json'))
    expect(broken).toBeDefined()
    const incomplete = json.skipped.find(s => s.path.endsWith('incomplete'))
    expect(incomplete).toBeDefined()
    expect(incomplete?.reason).toContain('incomplete')

    expect(json.itemsScanned).toBe(1) // only good-skill
    const inventory = JSON.parse(
      await readFile(join(home, '.skillhub', 'inventory.json'), 'utf-8')
    ) as { items: Array<{ slug: string }> }
    expect(inventory.items).toHaveLength(1)
    expect(inventory.items[0]?.slug).toBe('good-skill')
  })

  test('doctor backs up existing inventory.json and reports backupPath', async () => {
    const { home, cwd } = await createTempHome()

    const skillhubDir = join(home, '.skillhub')
    await mkdir(skillhubDir, { recursive: true })
    const inventoryPath = join(skillhubDir, 'inventory.json')
    const originalContent = JSON.stringify({ items: [{ registry: 'old', namespace: 'x', slug: 'y', version: '0.0.1', targets: [] }] })
    await writeFile(inventoryPath, originalContent)

    const result = await runCli(['doctor'], {
      HOME: home,
      USERPROFILE: home
    }, { cwd })

    expect(result.exitCode).toBe(0)

    const backupPath = `${inventoryPath}.bak`
    const backupContent = await readFile(backupPath, 'utf-8')
    expect(backupContent).toBe(originalContent)

    expect(result.stdout).toContain('Backup:')
    expect(result.stdout).toContain('inventory.json.bak')
  })

  test('doctor merges with existing inventory and preserves out-of-cwd entries', async () => {
    const { home, cwd } = await createTempHome()

    const skillhubDir = join(home, '.skillhub')
    await mkdir(skillhubDir, { recursive: true })
    const inventoryPath = join(skillhubDir, 'inventory.json')

    await writeFile(inventoryPath, JSON.stringify({
      items: [
        {
          registry: 'https://skill.xfyun.cn',
          namespace: 'global',
          slug: 'external-skill',
          version: '1.0.0',
          targets: [
            {
              agent: 'claude-code',
              rootDir: '/external/project/.claude',
              installDir: '/external/project/.claude/skills/external-skill',
              installedAt: '2026-04-01T00:00:00Z'
            }
          ]
        }
      ]
    }))

    await seedSkill(cwd, {
      agentDir: '.claude',
      slug: 'local-skill',
      metadata: {
        registry: 'https://skill.xfyun.cn',
        namespace: 'global',
        slug: 'local-skill',
        version: '2.0.0',
        agent: 'claude-code',
        installedAt: '2026-04-21T09:00:00Z'
      }
    })

    const result = await runCli(['doctor', '--json'], {
      HOME: home,
      USERPROFILE: home
    }, { cwd })

    expect(result.exitCode).toBe(0)

    const json = JSON.parse(result.stdout)
    expect(json.itemsScanned).toBe(1)
    expect(json.itemsPreserved).toBe(1)
    expect(json.targetsPreserved).toBe(1)

    const raw = await readFile(inventoryPath, 'utf-8')
    const inventory = JSON.parse(raw) as {
      items: Array<{ slug: string }>
    }

    expect(inventory.items.map(item => item.slug)).toEqual(
      expect.arrayContaining(['external-skill', 'local-skill'])
    )
  })

  // -------------------------------------------------------------------------
  // P1 — Symlink safety: doctor must skip (not follow) symlinked agent /
  // skill / .skillhub directories. This protects against malicious or
  // accidental symlinks that would otherwise let metadata be slurped from
  // arbitrary filesystem locations.
  // -------------------------------------------------------------------------
  test('doctor skips an agent dir that is a symlink', async () => {
    const { home, cwd } = await createTempHome()
    const { symlink, mkdir: mkdirP } = await import('node:fs/promises')

    // Real target with a valid metadata file off in /tmp.
    const realRoot = join(cwd, '__real__', '.codex', 'skills', 'pdf-parser', '.skillhub')
    await mkdirP(realRoot, { recursive: true })
    await writeFile(join(realRoot, 'metadata.json'), JSON.stringify({
      registry: 'https://skill.xfyun.cn', namespace: 'global', slug: 'pdf-parser',
      version: '1.0.0', agent: 'codex', installedAt: '2026-04-20T12:00:00Z'
    }))

    // Symlink ./.codex -> __real__/.codex inside cwd. Doctor scans cwd.
    await symlink(join(cwd, '__real__', '.codex'), join(cwd, '.codex'))

    const result = await runCli(['doctor', '--json'], {
      HOME: home, USERPROFILE: home
    }, { cwd })

    expect(result.exitCode).toBe(0)
    const json = JSON.parse(result.stdout) as {
      itemsScanned: number
      skipped: Array<{ path: string; reason: string }>
    }
    // The symlinked agent dir must NOT contribute an inventory item.
    expect(json.itemsScanned).toBe(0)
    expect(json.skipped.some(s => s.path.endsWith('.codex') && s.reason.includes('regular directory'))).toBe(true)
  })

  test('doctor skips a slug dir that is a symlink (real agent dir, symlinked slug)', async () => {
    const { home, cwd } = await createTempHome()
    const { symlink, mkdir: mkdirP } = await import('node:fs/promises')

    // Real metadata under cwd/__real__/pdf-parser/.skillhub/
    const realSlug = join(cwd, '__real__', 'pdf-parser')
    const realSkillhub = join(realSlug, '.skillhub')
    await mkdirP(realSkillhub, { recursive: true })
    await writeFile(join(realSkillhub, 'metadata.json'), JSON.stringify({
      registry: 'https://skill.xfyun.cn', namespace: 'global', slug: 'pdf-parser',
      version: '1.0.0', agent: 'codex', installedAt: '2026-04-20T12:00:00Z'
    }))

    // .codex/skills exists as a real dir, but pdf-parser inside it is a
    // symlink to the real metadata location.
    const skillsDir = join(cwd, '.codex', 'skills')
    await mkdirP(skillsDir, { recursive: true })
    await symlink(realSlug, join(skillsDir, 'pdf-parser'))

    const result = await runCli(['doctor', '--json'], {
      HOME: home, USERPROFILE: home
    }, { cwd })

    expect(result.exitCode).toBe(0)
    const json = JSON.parse(result.stdout) as {
      itemsScanned: number
      skipped: Array<{ path: string; reason: string }>
    }
    expect(json.itemsScanned).toBe(0)
    const symlinked = json.skipped.find(s => s.path.endsWith('pdf-parser'))
    expect(symlinked).toBeDefined()
    expect(symlinked?.reason).toContain('regular directory')
  })

  test('doctor skips a .skillhub dir that is a symlink', async () => {
    const { home, cwd } = await createTempHome()
    const { symlink, mkdir: mkdirP } = await import('node:fs/promises')

    // Real metadata reachable through a symlinked .skillhub directory.
    const realSkillhub = join(cwd, '__real_meta__')
    await mkdirP(realSkillhub, { recursive: true })
    await writeFile(join(realSkillhub, 'metadata.json'), JSON.stringify({
      registry: 'https://skill.xfyun.cn', namespace: 'global', slug: 'pdf-parser',
      version: '1.0.0', agent: 'codex', installedAt: '2026-04-20T12:00:00Z'
    }))

    const slugDir = join(cwd, '.codex', 'skills', 'pdf-parser')
    await mkdirP(slugDir, { recursive: true })
    await symlink(realSkillhub, join(slugDir, '.skillhub'))

    const result = await runCli(['doctor', '--json'], {
      HOME: home, USERPROFILE: home
    }, { cwd })

    expect(result.exitCode).toBe(0)
    const json = JSON.parse(result.stdout) as {
      itemsScanned: number
      skipped: Array<{ path: string; reason: string }>
    }
    expect(json.itemsScanned).toBe(0)
    const skipped = json.skipped.find(s => s.path.endsWith('pdf-parser'))
    expect(skipped).toBeDefined()
    expect(skipped?.reason.toLowerCase()).toMatch(/skillhub|regular directory/)
  })
})
