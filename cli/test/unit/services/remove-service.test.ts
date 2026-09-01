import { access, mkdir, mkdtemp, symlink } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { describe, expect, test } from 'bun:test'
import { removeLocalSkill } from '../../../src/services/remove-service'
import { acquireSkillTargetLock, skillTargetLockPath } from '../../../src/services/skill-target-lock'
import { InventoryStore } from '../../../src/stores/inventory-store'

async function exists(path: string): Promise<boolean> {
  try {
    await access(path)
    return true
  } catch {
    return false
  }
}

describe('removeLocalSkill', () => {
  test('bare slug removes all current-registry installs with the same slug across namespaces', async () => {
    const home = await mkdtemp(join(tmpdir(), 'skillhub-remove-home-'))
    const root = await mkdtemp(join(tmpdir(), 'skillhub-remove-root-'))
    const globalDir = join(root, 'codex', 'demo')
    const teamDir = join(root, 'claude', 'demo')
    await mkdir(globalDir, { recursive: true })
    await mkdir(teamDir, { recursive: true })

    const store = new InventoryStore(home)
    await store.write({
      items: [
        {
          registry: 'https://skill.xfyun.cn',
          namespace: 'global',
          slug: 'demo',
          version: '1.0.0',
          targets: [{ agent: 'codex', rootDir: join(root, 'codex'), installDir: globalDir, installedAt: '2026-04-20T00:00:00Z' }]
        },
        {
          registry: 'https://skill.xfyun.cn',
          namespace: 'team',
          slug: 'demo',
          version: '1.0.0',
          targets: [{ agent: 'claude-code', rootDir: join(root, 'claude'), installDir: teamDir, installedAt: '2026-04-20T00:00:00Z' }]
        }
      ]
    })

    const result = await removeLocalSkill({ registry: 'https://skill.xfyun.cn', slug: 'demo', home })

    expect(result.removed.map(item => item.namespace).sort()).toEqual(['global', 'team'])
    expect(await exists(globalDir)).toBe(false)
    expect(await exists(teamDir)).toBe(false)
    expect((await store.read()).items).toEqual([])
  })

  test('namespace filter removes only the matching same-slug install', async () => {
    const home = await mkdtemp(join(tmpdir(), 'skillhub-remove-home-'))
    const root = await mkdtemp(join(tmpdir(), 'skillhub-remove-root-'))
    const globalDir = join(root, 'codex', 'demo')
    const teamDir = join(root, 'claude', 'demo')
    await mkdir(globalDir, { recursive: true })
    await mkdir(teamDir, { recursive: true })

    const store = new InventoryStore(home)
    await store.write({
      items: [
        {
          registry: 'https://skill.xfyun.cn',
          namespace: 'global',
          slug: 'demo',
          version: '1.0.0',
          targets: [{ agent: 'codex', rootDir: join(root, 'codex'), installDir: globalDir, installedAt: '2026-04-20T00:00:00Z' }]
        },
        {
          registry: 'https://skill.xfyun.cn',
          namespace: 'team',
          slug: 'demo',
          version: '1.0.0',
          targets: [{ agent: 'claude-code', rootDir: join(root, 'claude'), installDir: teamDir, installedAt: '2026-04-20T00:00:00Z' }]
        }
      ]
    })

    const result = await removeLocalSkill({
      registry: 'https://skill.xfyun.cn',
      namespace: 'team',
      slug: 'demo',
      home
    })

    expect(result.removed.map(item => item.namespace)).toEqual(['team'])
    expect(await exists(globalDir)).toBe(true)
    expect(await exists(teamDir)).toBe(false)
    expect((await store.read()).items.map(item => item.namespace)).toEqual(['global'])
  })

  test('does not remove a target while install or upgrade holds its lifecycle lock', async () => {
    const home = await mkdtemp(join(tmpdir(), 'skillhub-remove-lock-home-'))
    const rootDir = await mkdtemp(join(tmpdir(), 'skillhub-remove-lock-root-'))
    const skillDir = join(rootDir, 'demo')
    await mkdir(skillDir, { recursive: true })
    const store = new InventoryStore(home)
    await store.write({
      items: [{
        registry: 'https://skill.xfyun.cn',
        namespace: 'global',
        slug: 'demo',
        version: '1.0.0',
        targets: [{ agent: 'codex', rootDir, installDir: skillDir, installedAt: '2026-09-01T00:00:00Z' }]
      }]
    })

    const release = await acquireSkillTargetLock(rootDir, 'demo')
    try {
      await expect(removeLocalSkill({ registry: 'https://skill.xfyun.cn', slug: 'demo', home }))
        .rejects.toThrow('install target is busy')
      expect(await exists(skillDir)).toBe(true)
      expect((await store.read()).items[0]?.targets).toHaveLength(1)
    } finally {
      await release()
    }

    const removed = await removeLocalSkill({ registry: 'https://skill.xfyun.cn', slug: 'demo', home })
    expect(removed.removed).toHaveLength(1)
    expect(await exists(skillDir)).toBe(false)
    expect((await store.read()).items).toEqual([])
    expect(await exists(await skillTargetLockPath(rootDir, 'demo'))).toBe(false)
  })

  test('legacy symlink roots use the same lifecycle lock as their real target', async () => {
    const home = await mkdtemp(join(tmpdir(), 'skillhub-remove-alias-home-'))
    const parent = await mkdtemp(join(tmpdir(), 'skillhub-remove-alias-parent-'))
    const realRoot = join(parent, 'real-root')
    const aliasRoot = join(parent, 'legacy-alias')
    const realSkillDir = join(realRoot, 'demo')
    const aliasSkillDir = join(aliasRoot, 'demo')
    await mkdir(realSkillDir, { recursive: true })
    await symlink(realRoot, aliasRoot, 'dir')

    const store = new InventoryStore(home)
    await store.write({
      items: [{
        registry: 'https://skill.xfyun.cn',
        namespace: 'global',
        slug: 'demo',
        version: '1.0.0',
        targets: [{ agent: 'codex', rootDir: aliasRoot, installDir: aliasSkillDir, installedAt: '2026-09-01T00:00:00Z' }]
      }]
    })

    const lockPath = await skillTargetLockPath(realRoot, 'demo')
    expect(await skillTargetLockPath(aliasRoot, 'demo')).toBe(lockPath)
    const release = await acquireSkillTargetLock(realRoot, 'demo')
    try {
      await expect(removeLocalSkill({ registry: 'https://skill.xfyun.cn', slug: 'demo', home }))
        .rejects.toThrow('install target is busy')
      expect(await exists(realSkillDir)).toBe(true)
      expect((await store.read()).items[0]?.targets).toHaveLength(1)
    } finally {
      await release()
    }
    expect(await exists(lockPath)).toBe(false)
  })

  test('removes a stale inventory target when the recorded root directory is missing', async () => {
    const home = await mkdtemp(join(tmpdir(), 'skillhub-remove-stale-home-'))
    const parent = await mkdtemp(join(tmpdir(), 'skillhub-remove-stale-parent-'))
    const rootDir = join(parent, 'missing-root')
    const skillDir = join(rootDir, 'demo')
    const store = new InventoryStore(home)
    await store.write({
      items: [{
        registry: 'https://skill.xfyun.cn',
        namespace: 'global',
        slug: 'demo',
        version: '1.0.0',
        targets: [{ agent: 'codex', rootDir, installDir: skillDir, installedAt: '2026-09-01T00:00:00Z' }]
      }]
    })

    const result = await removeLocalSkill({ registry: 'https://skill.xfyun.cn', slug: 'demo', home })

    expect(result.removed).toEqual([{ namespace: 'global', agent: 'codex', dir: skillDir, existed: false }])
    expect(await exists(rootDir)).toBe(false)
    expect((await store.read()).items).toEqual([])
    expect(await exists(await skillTargetLockPath(rootDir, 'demo'))).toBe(false)
  })

  test('throws on path traversal in installDir', async () => {
    const home = await mkdtemp(join(tmpdir(), 'skillhub-remove-traversal-'))

    const store = new InventoryStore(home)
    await store.write({
      items: [
        {
          registry: 'https://skill.xfyun.cn',
          namespace: 'global',
          slug: 'evil',
          version: '1.0.0',
          targets: [{ agent: 'codex', rootDir: '/safe/root', installDir: '/etc/passwd', installedAt: '2026-04-20T00:00:00Z' }]
        }
      ]
    })

    await expect(
      removeLocalSkill({ registry: 'https://skill.xfyun.cn', slug: 'evil', home })
    ).rejects.toThrow('unsafe remove path')
  })
})
