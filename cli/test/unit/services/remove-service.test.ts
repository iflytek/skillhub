import { access, mkdir, mkdtemp } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { describe, expect, test } from 'bun:test'
import { removeLocalSkill } from '../../../src/services/remove-service'
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
  test('removes all current-registry installs with the same slug across namespaces', async () => {
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
