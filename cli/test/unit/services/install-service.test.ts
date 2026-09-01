import { access, mkdir, mkdtemp, readFile, rm, symlink, writeFile } from 'node:fs/promises'
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

  return installFetchWithDownloadResponse(new Response(
    archive.buffer.slice(archive.byteOffset, archive.byteOffset + archive.byteLength) as ArrayBuffer,
    { status: 200 }
  ))
}

function installFetchWithDownloadResponse(downloadResponse: Response): typeof fetch {
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
      return downloadResponse.clone()
    }
    return Response.json({ code: 404 }, { status: 404 })
  }
  return fakeFetch as unknown as typeof fetch
}

async function writeManagedMetadata(
  skillDir: string,
  identity: { registry: string; namespace: string; slug: string } = {
    registry: 'http://registry.test',
    namespace: 'global',
    slug: 'demo'
  }
): Promise<void> {
  await mkdir(join(skillDir, '.skillhub'), { recursive: true })
  await writeFile(join(skillDir, '.skillhub', 'metadata.json'), JSON.stringify({
    ...identity,
    version: '0.1.0',
    source: 'skillhub'
  }))
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

  test('preflights all targets before writing when a later target is occupied', async () => {
    globalThis.fetch = installFetch({ 'SKILL.md': '# Demo' })
    const home = await mkdtemp(join(tmpdir(), 'skillhub-install-home-'))
    const firstRoot = await mkdtemp(join(tmpdir(), 'skillhub-install-first-root-'))
    const secondRoot = await mkdtemp(join(tmpdir(), 'skillhub-install-second-root-'))
    const firstSkillDir = join(firstRoot, 'demo')
    const secondSkillDir = join(secondRoot, 'demo')
    await mkdir(secondSkillDir, { recursive: true })

    await expect(installSkill({
      registry: 'http://registry.test',
      namespace: 'global',
      slug: 'demo',
      targets: [
        { agent: 'codex', rootDir: firstRoot, scope: 'project', source: 'explicit' },
        { agent: 'claude-code', rootDir: secondRoot, scope: 'project', source: 'explicit' }
      ],
      force: false,
      home
    })).rejects.toThrow(`skill already installed at ${secondSkillDir}`)

    expect(await exists(firstSkillDir)).toBe(false)
    expect(await exists(join(home, '.skillhub', 'inventory.json'))).toBe(false)
  })

  test('rejects canonical target aliases before writing any installation', async () => {
    globalThis.fetch = installFetch({ 'SKILL.md': '# Demo' })
    const home = await mkdtemp(join(tmpdir(), 'skillhub-install-home-'))
    const targetParent = await mkdtemp(join(tmpdir(), 'skillhub-install-targets-'))
    const genericRoot = join(targetParent, 'generic')
    const codexRoot = join(targetParent, 'codex')
    const skillDir = join(genericRoot, 'demo')
    try {
      await mkdir(genericRoot, { recursive: true })
      await symlink(genericRoot, codexRoot, process.platform === 'win32' ? 'junction' : 'dir')

      await expect(installSkill({
        registry: 'http://registry.test',
        namespace: 'global',
        slug: 'demo',
        targets: [
          { agent: 'codex', rootDir: codexRoot, scope: 'user', source: 'detected' },
          { agent: 'generic', rootDir: genericRoot, scope: 'user', source: 'fallback' }
        ],
        force: false,
        home
      })).rejects.toThrow('multiple install targets resolve to')

      expect(await exists(skillDir)).toBe(false)
      expect(await exists(join(home, '.skillhub', 'inventory.json'))).toBe(false)
    } finally {
      await rm(home, { recursive: true, force: true })
      await rm(targetParent, { recursive: true, force: true })
    }
  })

  test('force replaces the old skill directory instead of overlaying files', async () => {
    globalThis.fetch = installFetch({ 'SKILL.md': '# New' })
    const home = await mkdtemp(join(tmpdir(), 'skillhub-install-home-'))
    const rootDir = await mkdtemp(join(tmpdir(), 'skillhub-install-root-'))
    const skillDir = join(rootDir, 'demo')
    await mkdir(skillDir, { recursive: true })
    await writeFile(join(skillDir, 'stale.txt'), 'old')
    await writeManagedMetadata(skillDir)

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

  test('force rejects a different namespace at the same install directory', async () => {
    globalThis.fetch = installFetch({ 'SKILL.md': '# Team Demo' })
    const home = await mkdtemp(join(tmpdir(), 'skillhub-install-home-'))
    const rootDir = await mkdtemp(join(tmpdir(), 'skillhub-install-root-'))
    const skillDir = join(rootDir, 'demo')
    await mkdir(skillDir, { recursive: true })
    await writeManagedMetadata(skillDir)
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

    await expect(installSkill({
      registry: 'http://registry.test',
      namespace: 'team',
      slug: 'demo',
      targets: [{ agent: 'codex', rootDir, scope: 'project', source: 'explicit' }],
      force: true,
      home
    })).rejects.toThrow('source conflict')

    const inventory = JSON.parse(await readFile(inventoryPath, 'utf-8'))
    expect(inventory.items).toHaveLength(1)
    expect(inventory.items[0]).toMatchObject({ namespace: 'global', slug: 'demo' })
    expect(inventory.items[0].targets).toHaveLength(1)
    expect(inventory.items[0].targets[0].installDir).toBe(skillDir)
  })

  test('force keeps old installation and inventory when replacement extraction fails', async () => {
    globalThis.fetch = installFetchWithDownloadResponse(new Response(new TextEncoder().encode('not a zip'), { status: 200 }))
    const home = await mkdtemp(join(tmpdir(), 'skillhub-install-home-'))
    const rootDir = await mkdtemp(join(tmpdir(), 'skillhub-install-root-'))
    const skillDir = join(rootDir, 'demo')
    await mkdir(skillDir, { recursive: true })
    await writeFile(join(skillDir, 'SKILL.md'), '# Old')
    await writeManagedMetadata(skillDir)
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
    }, null, 2))

    await expect(installSkill({
      registry: 'http://registry.test',
      namespace: 'global',
      slug: 'demo',
      targets: [{ agent: 'codex', rootDir, scope: 'project', source: 'explicit' }],
      force: true,
      home
    })).rejects.toThrow('invalid zip central directory')

    expect(await readFile(join(skillDir, 'SKILL.md'), 'utf-8')).toBe('# Old')
    const inventory = JSON.parse(await readFile(inventoryPath, 'utf-8'))
    expect(inventory.items).toHaveLength(1)
    expect(inventory.items[0]).toMatchObject({ namespace: 'global', slug: 'demo', version: '0.1.0' })
    expect(inventory.items[0].targets[0].installDir).toBe(skillDir)
  })

  test('force restores the old installation when inventory persistence fails', async () => {
    globalThis.fetch = installFetch({ 'SKILL.md': '# New' })
    const rootDir = await mkdtemp(join(tmpdir(), 'skillhub-install-root-'))
    const skillDir = join(rootDir, 'demo')
    await mkdir(skillDir, { recursive: true })
    await writeFile(join(skillDir, 'SKILL.md'), '# Old')
    await writeManagedMetadata(skillDir)
    const invalidHome = join(rootDir, 'home-is-a-file')
    await writeFile(invalidHome, 'not a directory')

    await expect(installSkill({
      registry: 'http://registry.test',
      namespace: 'global',
      slug: 'demo',
      targets: [{ agent: 'codex', rootDir, scope: 'project', source: 'explicit' }],
      force: true,
      home: invalidHome
    })).rejects.toThrow()

    expect(await readFile(join(skillDir, 'SKILL.md'), 'utf-8')).toBe('# Old')
  })

  test('rejects downloads whose content-length exceeds the package limit', async () => {
    globalThis.fetch = installFetchWithDownloadResponse(new Response(new Uint8Array(0), {
      status: 200,
      headers: { 'Content-Length': String(100 * 1024 * 1024 + 1) }
    }))
    const rootDir = await mkdtemp(join(tmpdir(), 'skillhub-install-root-'))

    await expect(installSkill({
      registry: 'http://registry.test',
      namespace: 'global',
      slug: 'demo',
      targets: [{ agent: 'codex', rootDir, scope: 'project', source: 'explicit' }],
      force: false
    })).rejects.toThrow('download exceeds maximum package size')
  })
})
