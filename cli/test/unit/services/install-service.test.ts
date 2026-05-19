import { access, mkdir, mkdtemp, readFile, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { afterEach, describe, expect, test } from 'bun:test'
import { zipSync } from 'fflate'
import { installSkill } from '../../../src/services/install-service'

const originalFetch = globalThis.fetch

async function exists(path: string): Promise<boolean> {
  try {
    await access(path)
    return true
  } catch {
    return false
  }
}

function installFetch(zipEntries: Record<string, string>): typeof fetch {
  const archive = zipSync(Object.fromEntries(
    Object.entries(zipEntries).map(([name, content]) => [name, new TextEncoder().encode(content)])
  ))

  const fakeFetch = async (input: URL | RequestInfo) => {
    const path = new URL(String(input)).pathname
    if (path.endsWith('/resolve')) {
      return Response.json({
        code: 0,
        data: {
          namespace: 'global',
          slug: 'demo',
          version: '1.0.0',
          versionId: 1,
          fingerprint: 'fp',
          downloadUrl: '/download'
        }
      })
    }
    if (path.endsWith('/download')) {
      const body = archive.buffer.slice(archive.byteOffset, archive.byteOffset + archive.byteLength) as ArrayBuffer
      return new Response(body, { status: 200 })
    }
    return Response.json({ code: 404 }, { status: 404 })
  }
  return fakeFetch as unknown as typeof fetch
}

describe('installSkill', () => {
  afterEach(() => {
    globalThis.fetch = originalFetch
  })

  test('fails when target skill directory already exists without metadata', async () => {
    globalThis.fetch = installFetch({ 'SKILL.md': '# Demo' })
    const rootDir = await mkdtemp(join(tmpdir(), 'skillhub-install-root-'))
    const skillDir = join(rootDir, 'demo')
    await mkdir(skillDir, { recursive: true })
    await writeFile(join(skillDir, 'local.txt'), 'keep')

    await expect(installSkill({
      registry: 'http://registry.test',
      namespace: 'global',
      slug: 'demo',
      targets: [{ agent: 'codex', rootDir, scope: 'project', source: 'explicit' }],
      force: false
    })).rejects.toThrow('skill already installed')
  })

  test('force replaces the old skill directory instead of overlaying files', async () => {
    globalThis.fetch = installFetch({ 'SKILL.md': '# New' })
    const home = await mkdtemp(join(tmpdir(), 'skillhub-install-home-'))
    const rootDir = await mkdtemp(join(tmpdir(), 'skillhub-install-root-'))
    const skillDir = join(rootDir, 'demo')
    await mkdir(skillDir, { recursive: true })
    await writeFile(join(skillDir, 'stale.txt'), 'old')

    await installSkill({
      registry: 'http://registry.test',
      namespace: 'global',
      slug: 'demo',
      targets: [{ agent: 'codex', rootDir, scope: 'project', source: 'explicit' }],
      force: true,
      home
    })

    expect(await readFile(join(skillDir, 'SKILL.md'), 'utf-8')).toBe('# New')
    expect(await exists(join(skillDir, 'stale.txt'))).toBe(false)
  })

  test('force removes stale inventory records that point at the replaced install directory', async () => {
    globalThis.fetch = installFetch({ 'SKILL.md': '# Team Demo' })
    const home = await mkdtemp(join(tmpdir(), 'skillhub-install-home-'))
    const rootDir = await mkdtemp(join(tmpdir(), 'skillhub-install-root-'))
    const skillDir = join(rootDir, 'demo')
    await mkdir(skillDir, { recursive: true })
    const inventoryPath = join(home, '.skillhub', 'inventory.json')
    await mkdir(join(home, '.skillhub'), { recursive: true })
    await writeFile(inventoryPath, JSON.stringify({
      items: [{
        registry: 'http://registry.test',
        namespace: 'global',
        slug: 'demo',
        version: '0.1.0',
        targets: [{
          agent: 'codex',
          rootDir,
          installDir: skillDir,
          installedAt: '2026-04-20T00:00:00.000Z'
        }]
      }]
    }))

    await installSkill({
      registry: 'http://registry.test',
      namespace: 'team',
      slug: 'demo',
      targets: [{ agent: 'codex', rootDir, scope: 'project', source: 'explicit' }],
      force: true,
      home
    })

    const inventory = JSON.parse(await readFile(inventoryPath, 'utf-8'))
    expect(inventory.items).toHaveLength(1)
    expect(inventory.items[0]).toMatchObject({ namespace: 'team', slug: 'demo' })
    expect(inventory.items[0].targets).toHaveLength(1)
    expect(inventory.items[0].targets[0].installDir).toBe(skillDir)
  })
})
