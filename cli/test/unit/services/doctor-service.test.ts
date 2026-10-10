import { describe, expect, test } from 'bun:test'
import { mkdtemp, mkdir, writeFile, symlink, readFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { runDoctor } from '../../../src/services/doctor-service'

type InventoryFile = {
  items: Array<{
    slug: string
    version: string
    targets: unknown[]
  }>
}

async function setupSkillDir(cwd: string, agentDir: string, slug: string, metadata: Record<string, string>) {
  const skillDir = join(cwd, agentDir, 'skills', slug)
  const metaDir = join(skillDir, '.skillhub')
  await mkdir(metaDir, { recursive: true })
  await writeFile(join(metaDir, 'metadata.json'), JSON.stringify(metadata))
  return skillDir
}

describe('doctor-service', () => {
  test('rebuilds inventory from metadata files', async () => {
    const cwd = await mkdtemp(join(tmpdir(), 'doctor-test-'))
    const home = await mkdtemp(join(tmpdir(), 'doctor-home-'))

    await setupSkillDir(cwd, '.codex', 'pdf-parser', {
      registry: 'https://skill.xfyun.cn',
      namespace: 'global',
      slug: 'pdf-parser',
      version: '1.2.0',
      agent: 'codex',
      installedAt: '2026-04-20T12:00:00Z'
    })

    const result = await runDoctor(cwd, home)
    expect(result.itemsScanned).toBe(1)
    expect(result.targetsScanned).toBe(1)
    expect(result.skipped).toHaveLength(0)
    expect(result.conflicts).toHaveLength(0)
  })

  test('skips directories without metadata', async () => {
    const cwd = await mkdtemp(join(tmpdir(), 'doctor-test-'))
    const home = await mkdtemp(join(tmpdir(), 'doctor-home-'))

    await mkdir(join(cwd, '.codex', 'skills', 'no-meta'), { recursive: true })

    const result = await runDoctor(cwd, home)
    expect(result.itemsScanned).toBe(0)
    expect(result.skipped).toHaveLength(1)
    expect(result.skipped[0]!.reason).toBe('no .skillhub directory')
  })

  test('detects version conflicts', async () => {
    const cwd = await mkdtemp(join(tmpdir(), 'doctor-test-'))
    const home = await mkdtemp(join(tmpdir(), 'doctor-home-'))

    await setupSkillDir(cwd, '.codex', 'pdf-parser', {
      registry: 'https://skill.xfyun.cn', namespace: 'global', slug: 'pdf-parser',
      version: '1.0.0', agent: 'codex', installedAt: '2026-04-20T12:00:00Z'
    })
    await setupSkillDir(cwd, '.claude', 'pdf-parser', {
      registry: 'https://skill.xfyun.cn', namespace: 'global', slug: 'pdf-parser',
      version: '2.0.0', agent: 'claude-code', installedAt: '2026-04-20T12:00:00Z'
    })

    const result = await runDoctor(cwd, home)
    expect(result.itemsScanned).toBe(0)
    expect(result.conflicts).toHaveLength(1)
    expect(result.conflicts[0]!.versions).toContain('1.0.0')
    expect(result.conflicts[0]!.versions).toContain('2.0.0')
  })

  test('skips symlinked skill directories', async () => {
    const cwd = await mkdtemp(join(tmpdir(), 'doctor-test-'))
    const home = await mkdtemp(join(tmpdir(), 'doctor-home-'))

    await setupSkillDir(cwd, '.codex', 'real-skill', {
      registry: 'https://skill.xfyun.cn', namespace: 'global', slug: 'real-skill',
      version: '1.0.0', agent: 'codex', installedAt: '2026-04-20T12:00:00Z'
    })

    const skillsDir = join(cwd, '.codex', 'skills')
    const realDir = join(skillsDir, 'real-skill')
    await symlink(realDir, join(skillsDir, 'symlink-skill'))

    const result = await runDoctor(cwd, home)
    expect(result.itemsScanned).toBe(1)
    expect(result.skipped.some(s => s.reason === 'not a regular directory')).toBe(true)
  })

  test('skips symlinked agent directories', async () => {
    const cwd = await mkdtemp(join(tmpdir(), 'doctor-test-'))
    const home = await mkdtemp(join(tmpdir(), 'doctor-home-'))

    await setupSkillDir(cwd, '.codex', 'pdf-parser', {
      registry: 'https://skill.xfyun.cn', namespace: 'global', slug: 'pdf-parser',
      version: '1.0.0', agent: 'codex', installedAt: '2026-04-20T12:00:00Z'
    })

    await symlink(join(cwd, '.codex'), join(cwd, '.fake-agent'))

    const result = await runDoctor(cwd, home)
    expect(result.itemsScanned).toBe(1)
    expect(result.targetsScanned).toBe(1)
  })

  test('skips symlinked .skillhub directories', async () => {
    const cwd = await mkdtemp(join(tmpdir(), 'doctor-test-'))
    const home = await mkdtemp(join(tmpdir(), 'doctor-home-'))

    const skillDir = join(cwd, '.codex', 'skills', 'evil-skill')
    await mkdir(skillDir, { recursive: true })

    const realMetaDir = await mkdtemp(join(tmpdir(), 'real-meta-'))
    await writeFile(join(realMetaDir, 'metadata.json'), JSON.stringify({
      registry: 'https://skill.xfyun.cn',
      namespace: 'global',
      slug: 'evil-skill',
      version: '1.0.0',
      agent: 'codex',
      installedAt: '2026-04-20T12:00:00Z'
    }))
    await symlink(realMetaDir, join(skillDir, '.skillhub'))

    const result = await runDoctor(cwd, home)
    expect(result.itemsScanned).toBe(0)
    expect(result.skipped.some(s => s.reason === '.skillhub is not a regular directory')).toBe(true)
  })

  test('merges scan results with existing inventory', async () => {
    const cwd = await mkdtemp(join(tmpdir(), 'doctor-test-'))
    const home = await mkdtemp(join(tmpdir(), 'doctor-home-'))

    // Pre-seed old inventory with one item NOT in current cwd
    const inventoryPath = join(home, '.skillhub', 'inventory.json')
    await mkdir(join(home, '.skillhub'), { recursive: true })
    await writeFile(inventoryPath, JSON.stringify({
      items: [
        {
          registry: 'https://skill.xfyun.cn',
          namespace: 'global',
          slug: 'external-skill',
          version: '1.0.0',
          targets: [
            {
              agent: 'codex',
              rootDir: '/external/project/.codex',
              installDir: '/external/project/.codex/skills/external-skill',
              installedAt: '2026-04-01T10:00:00Z'
            }
          ]
        }
      ]
    }))

    // Scan cwd finds one new item
    await setupSkillDir(cwd, '.claude', 'local-skill', {
      registry: 'https://skill.xfyun.cn',
      namespace: 'global',
      slug: 'local-skill',
      version: '2.0.0',
      agent: 'claude-code',
      installedAt: '2026-04-20T12:00:00Z'
    })

    const result = await runDoctor(cwd, home)

    // Final inventory should contain BOTH items
    expect(result.itemsScanned).toBe(1)
    expect(result.targetsScanned).toBe(1)
    expect(result.itemsPreserved).toBe(1)
    expect(result.targetsPreserved).toBe(1)
    expect(result.conflicts).toHaveLength(0)

    const finalInventory = JSON.parse(await readFile(inventoryPath, 'utf-8')) as InventoryFile
    expect(finalInventory.items).toHaveLength(2)

    const externalSkillItem = finalInventory.items.find(item => item.slug === 'external-skill')!
    expect(externalSkillItem).toBeDefined()
    expect(externalSkillItem.version).toBe('1.0.0')
    expect(externalSkillItem.targets).toHaveLength(1)

    const localSkillItem = finalInventory.items.find(item => item.slug === 'local-skill')!
    expect(localSkillItem).toBeDefined()
    expect(localSkillItem.version).toBe('2.0.0')
    expect(localSkillItem.targets).toHaveLength(1)
  })

  test('refreshes old record when scan hits same installDir', async () => {
    const cwd = await mkdtemp(join(tmpdir(), 'doctor-test-'))
    const home = await mkdtemp(join(tmpdir(), 'doctor-home-'))

    const installDir = join(cwd, '.codex', 'skills', 'pdf-parser')

    // Pre-seed old inventory with pdf-parser v1.0.0 at specific installDir
    const inventoryPath = join(home, '.skillhub', 'inventory.json')
    await mkdir(join(home, '.skillhub'), { recursive: true })
    await writeFile(inventoryPath, JSON.stringify({
      items: [
        {
          registry: 'https://skill.xfyun.cn',
          namespace: 'global',
          slug: 'pdf-parser',
          version: '1.0.0',
          targets: [
            {
              agent: 'codex',
              rootDir: join(cwd, '.codex'),
              installDir,
              installedAt: '2026-04-01T10:00:00Z'
            }
          ]
        }
      ]
    }))

    // Place metadata for pdf-parser v2.0.0 at the SAME installDir
    await setupSkillDir(cwd, '.codex', 'pdf-parser', {
      registry: 'https://skill.xfyun.cn',
      namespace: 'global',
      slug: 'pdf-parser',
      version: '2.0.0',
      agent: 'codex',
      installedAt: '2026-04-20T12:00:00Z'
    })

    const result = await runDoctor(cwd, home)

    // Final inventory should have 1 item with version 2.0.0 and 1 target
    expect(result.itemsScanned).toBe(1)
    expect(result.targetsScanned).toBe(1)
    expect(result.conflicts).toHaveLength(0)

    // Verify the inventory file has the updated version
    const finalInventory = JSON.parse(await readFile(inventoryPath, 'utf-8'))
    expect(finalInventory.items).toHaveLength(1)
    expect(finalInventory.items[0].version).toBe('2.0.0')
    expect(finalInventory.items[0].targets).toHaveLength(1)
  })

  test('conflict groups do not delete unrelated old items', async () => {
    const cwd = await mkdtemp(join(tmpdir(), 'doctor-test-'))
    const home = await mkdtemp(join(tmpdir(), 'doctor-home-'))

    // Pre-seed old inventory with image-resizer at /external/proj (NOT in cwd)
    const inventoryPath = join(home, '.skillhub', 'inventory.json')
    await mkdir(join(home, '.skillhub'), { recursive: true })
    await writeFile(inventoryPath, JSON.stringify({
      items: [
        {
          registry: 'https://skill.xfyun.cn',
          namespace: 'global',
          slug: 'image-resizer',
          version: '3.0.0',
          targets: [
            {
              agent: 'codex',
              rootDir: '/external/proj/.codex',
              installDir: '/external/proj/.codex/skills/image-resizer',
              installedAt: '2026-03-15T08:00:00Z'
            }
          ]
        }
      ]
    }))

    // In cwd, create conflict: pdf-parser v1.0.0 in .codex, pdf-parser v2.0.0 in .claude
    await setupSkillDir(cwd, '.codex', 'pdf-parser', {
      registry: 'https://skill.xfyun.cn',
      namespace: 'global',
      slug: 'pdf-parser',
      version: '1.0.0',
      agent: 'codex',
      installedAt: '2026-04-20T12:00:00Z'
    })
    await setupSkillDir(cwd, '.claude', 'pdf-parser', {
      registry: 'https://skill.xfyun.cn',
      namespace: 'global',
      slug: 'pdf-parser',
      version: '2.0.0',
      agent: 'claude-code',
      installedAt: '2026-04-20T12:00:00Z'
    })

    const result = await runDoctor(cwd, home)

    // Should detect conflict
    expect(result.conflicts).toHaveLength(1)
    expect(result.conflicts[0]!.versions).toContain('1.0.0')
    expect(result.conflicts[0]!.versions).toContain('2.0.0')

    // Final inventory should still contain image-resizer
    const finalInventory = JSON.parse(await readFile(inventoryPath, 'utf-8')) as InventoryFile
    const imageResizerItem = finalInventory.items.find(item => item.slug === 'image-resizer')!
    expect(imageResizerItem).toBeDefined()
    expect(imageResizerItem.version).toBe('3.0.0')
    expect(imageResizerItem.targets).toHaveLength(1)
  })
})
