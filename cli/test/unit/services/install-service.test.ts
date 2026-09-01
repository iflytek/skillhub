import { access, mkdir, mkdtemp, readFile, readdir, rm, symlink, utimes, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { afterEach, describe, expect, test } from 'bun:test'
import { zipSync } from 'fflate'
import { installSkill } from '../../../src/services/install-service'
import { planSkillUpgrades } from '../../../src/services/upgrade-service'
import { removeLocalSkill } from '../../../src/services/remove-service'
import { skillTargetLockPath } from '../../../src/services/skill-target-lock'

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

  test('migrates a canonical inventory target to its selected path alias without duplication', async () => {
    globalThis.fetch = installFetch({ 'SKILL.md': '# Reinstalled' })
    const home = await mkdtemp(join(tmpdir(), 'skillhub-install-home-'))
    const parent = await mkdtemp(join(tmpdir(), 'skillhub-install-alias-'))
    const realRoot = join(parent, 'real')
    const aliasRoot = join(parent, 'alias')
    const realSkillDir = join(realRoot, 'demo')
    const aliasSkillDir = join(aliasRoot, 'demo')
    await mkdir(realSkillDir, { recursive: true })
    await writeFile(join(realSkillDir, 'SKILL.md'), '# Old')
    await writeManagedMetadata(realSkillDir)
    await symlink(realRoot, aliasRoot, process.platform === 'win32' ? 'junction' : 'dir')

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
          rootDir: realRoot,
          installDir: realSkillDir,
          installedAt: '2026-09-01T00:00:00Z'
        }]
      }]
    }))

    await installSkill({
      registry: 'http://registry.test',
      namespace: 'global',
      slug: 'demo',
      targets: [{ agent: 'codex', rootDir: aliasRoot, scope: 'project', source: 'explicit' }],
      force: true,
      home
    })

    const inventory = JSON.parse(await readFile(inventoryPath, 'utf-8'))
    expect(inventory.items).toHaveLength(1)
    expect(inventory.items[0].targets).toEqual([
      expect.objectContaining({ rootDir: aliasRoot, installDir: aliasSkillDir })
    ])

    const plan = await planSkillUpgrades({
      coordinates: ['@global/demo'],
      registry: 'http://registry.test',
      force: false,
      home,
      tokenForRegistry: async () => undefined
    })
    expect(plan).toMatchObject({ blocked: 0, unchanged: 1 })

    await removeLocalSkill({
      registry: 'http://registry.test',
      namespace: 'global',
      slug: 'demo',
      home
    })
    expect(await exists(realSkillDir)).toBe(false)
    expect((JSON.parse(await readFile(inventoryPath, 'utf-8'))).items).toEqual([])
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

  test('reports lock cleanup failure as a warning after committing the installation', async () => {
    globalThis.fetch = installFetch({ 'SKILL.md': '# Committed' })
    const home = await mkdtemp(join(tmpdir(), 'skillhub-install-home-'))
    const rootDir = await mkdtemp(join(tmpdir(), 'skillhub-install-root-'))
    const skillDir = join(rootDir, 'demo')

    const result = await installSkill({
      registry: 'http://registry.test',
      namespace: 'global',
      slug: 'demo',
      targets: [{ agent: 'codex', rootDir, scope: 'project', source: 'explicit' }],
      force: false,
      home,
      acquireTargetLock: async () => async () => { throw new Error('simulated release failure') }
    })

    expect(result.warnings).toEqual(['target lock cleanup failed: simulated release failure'])
    expect(await readFile(join(skillDir, 'SKILL.md'), 'utf-8')).toBe('# Committed')
    const inventory = JSON.parse(await readFile(join(home, '.skillhub', 'inventory.json'), 'utf-8'))
    expect(inventory.items[0]).toMatchObject({ version: '1.0.0', fingerprint: 'fp' })
  })

  test('force rejects a different namespace at the same install directory', async () => {
    globalThis.fetch = installFetch({ 'SKILL.md': '# Team Demo' })
    const home = await mkdtemp(join(tmpdir(), 'skillhub-install-home-'))
    const rootDir = await mkdtemp(join(tmpdir(), 'skillhub-install-root-'))
    const skillDir = join(rootDir, 'demo')
    await mkdir(skillDir, { recursive: true })
    await writeFile(join(skillDir, 'SKILL.md'), '# Global Demo')
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
    expect(await readFile(join(skillDir, 'SKILL.md'), 'utf-8')).toBe('# Global Demo')
  })

  test('force rejects metadata explicitly owned by another installer', async () => {
    globalThis.fetch = installFetch({ 'SKILL.md': '# New' })
    const home = await mkdtemp(join(tmpdir(), 'skillhub-install-home-'))
    const rootDir = await mkdtemp(join(tmpdir(), 'skillhub-install-root-'))
    const skillDir = join(rootDir, 'demo')
    await mkdir(skillDir, { recursive: true })
    await writeFile(join(skillDir, 'SKILL.md'), '# Manual')
    await writeManagedMetadata(skillDir)
    const metadataPath = join(skillDir, '.skillhub', 'metadata.json')
    const metadata = JSON.parse(await readFile(metadataPath, 'utf-8'))
    metadata.source = 'manual'
    await writeFile(metadataPath, JSON.stringify(metadata))
    const metadataBefore = await readFile(metadataPath, 'utf-8')

    await expect(installSkill({
      registry: 'http://registry.test',
      namespace: 'global',
      slug: 'demo',
      targets: [{ agent: 'codex', rootDir, scope: 'project', source: 'explicit' }],
      force: true,
      home
    })).rejects.toThrow('cannot verify SkillHub ownership')
    expect(await readFile(join(skillDir, 'SKILL.md'), 'utf-8')).toBe('# Manual')
    expect(await readFile(metadataPath, 'utf-8')).toBe(metadataBefore)
    expect(await exists(join(home, '.skillhub', 'inventory.json'))).toBe(false)
    const entries = await readdir(rootDir)
    expect(entries.some(name => name.includes('skillhub-backup'))).toBe(false)
    expect(entries.some(name => name.includes('skillhub-install.lock'))).toBe(false)
  })

  test('force rejects a directory without installation metadata', async () => {
    globalThis.fetch = installFetch({ 'SKILL.md': '# New' })
    const home = await mkdtemp(join(tmpdir(), 'skillhub-install-home-'))
    const rootDir = await mkdtemp(join(tmpdir(), 'skillhub-install-root-'))
    const skillDir = join(rootDir, 'demo')
    await mkdir(skillDir, { recursive: true })
    await writeFile(join(skillDir, 'local.txt'), 'keep')

    await expect(installSkill({
      registry: 'http://registry.test', namespace: 'global', slug: 'demo',
      targets: [{ agent: 'codex', rootDir, scope: 'project', source: 'explicit' }],
      force: true, home
    })).rejects.toThrow('cannot verify SkillHub ownership')
    expect(await readFile(join(skillDir, 'local.txt'), 'utf-8')).toBe('keep')
    expect(await exists(join(home, '.skillhub', 'inventory.json'))).toBe(false)
  })

  test('force rejects a different slug in installation metadata', async () => {
    globalThis.fetch = installFetch({ 'SKILL.md': '# New' })
    const home = await mkdtemp(join(tmpdir(), 'skillhub-install-home-'))
    const rootDir = await mkdtemp(join(tmpdir(), 'skillhub-install-root-'))
    const skillDir = join(rootDir, 'demo')
    await mkdir(skillDir, { recursive: true })
    await writeFile(join(skillDir, 'SKILL.md'), '# Other')
    await writeManagedMetadata(skillDir, {
      registry: 'http://registry.test', namespace: 'global', slug: 'other'
    })

    await expect(installSkill({
      registry: 'http://registry.test', namespace: 'global', slug: 'demo',
      targets: [{ agent: 'codex', rootDir, scope: 'project', source: 'explicit' }],
      force: true, home
    })).rejects.toThrow('source conflict')
    expect(await readFile(join(skillDir, 'SKILL.md'), 'utf-8')).toBe('# Other')
    expect(await exists(join(home, '.skillhub', 'inventory.json'))).toBe(false)
  })

  test('revalidates ownership after download before replacing the target', async () => {
    const home = await mkdtemp(join(tmpdir(), 'skillhub-install-home-'))
    const rootDir = await mkdtemp(join(tmpdir(), 'skillhub-install-root-'))
    const skillDir = join(rootDir, 'demo')
    await mkdir(skillDir, { recursive: true })
    await writeFile(join(skillDir, 'SKILL.md'), '# Old')
    await writeManagedMetadata(skillDir)
    const archive = zipSync({ 'SKILL.md': new TextEncoder().encode('# New') })

    globalThis.fetch = (async (input: URL | RequestInfo) => {
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
        await writeManagedMetadata(skillDir, {
          registry: 'http://other-registry.test',
          namespace: 'global',
          slug: 'demo'
        })
        await writeFile(join(skillDir, 'SKILL.md'), '# Replaced during download')
        return new Response(
          archive.buffer.slice(archive.byteOffset, archive.byteOffset + archive.byteLength) as ArrayBuffer,
          { status: 200 }
        )
      }
      return Response.json({ code: 404 }, { status: 404 })
    }) as typeof fetch

    await expect(installSkill({
      registry: 'http://registry.test',
      namespace: 'global',
      slug: 'demo',
      targets: [{ agent: 'codex', rootDir, scope: 'project', source: 'explicit' }],
      force: true,
      home
    })).rejects.toThrow('source conflict')

    expect(await readFile(join(skillDir, 'SKILL.md'), 'utf-8')).toBe('# Replaced during download')
    const metadata = JSON.parse(await readFile(join(skillDir, '.skillhub', 'metadata.json'), 'utf-8'))
    expect(metadata.registry).toBe('http://other-registry.test')
    expect((await readdir(rootDir)).some(name => name.includes('skillhub-backup'))).toBe(false)
    expect(await exists(await skillTargetLockPath(rootDir, 'demo'))).toBe(false)
    expect(await exists(join(home, '.skillhub', 'inventory.json'))).toBe(false)
  })

  test('rolls back every target when a later target changes source before commit', async () => {
    const home = await mkdtemp(join(tmpdir(), 'skillhub-install-home-'))
    const parent = await mkdtemp(join(tmpdir(), 'skillhub-install-targets-'))
    const firstRoot = join(parent, 'a')
    const secondRoot = join(parent, 'b')
    const firstSkillDir = join(firstRoot, 'demo')
    const secondSkillDir = join(secondRoot, 'demo')
    for (const skillDir of [firstSkillDir, secondSkillDir]) {
      await mkdir(skillDir, { recursive: true })
      await writeFile(join(skillDir, 'SKILL.md'), `# Old ${skillDir === firstSkillDir ? 'A' : 'B'}`)
      await writeManagedMetadata(skillDir)
    }
    await mkdir(join(home, '.skillhub'), { recursive: true })
    await writeFile(join(home, '.skillhub', 'inventory.json'), JSON.stringify({
      items: [{
        registry: 'http://registry.test',
        namespace: 'global',
        slug: 'demo',
        version: '0.1.0',
        targets: [
          { agent: 'codex', rootDir: firstRoot, installDir: firstSkillDir, installedAt: '2026-09-01T00:00:00Z' },
          { agent: 'claude-code', rootDir: secondRoot, installDir: secondSkillDir, installedAt: '2026-09-01T00:00:00Z' }
        ]
      }]
    }))
    const archive = zipSync({ 'SKILL.md': new TextEncoder().encode('# New') })
    globalThis.fetch = (async (input: URL | RequestInfo) => {
      const path = new URL(String(input)).pathname
      if (path.endsWith('/resolve')) {
        return Response.json({ code: 0, data: {
          namespace: 'global', slug: 'demo', version: '1.0.0', versionId: 1,
          fingerprint: 'fp', downloadUrl: '/download'
        } })
      }
      if (path.endsWith('/download')) {
        await writeManagedMetadata(secondSkillDir, {
          registry: 'http://other-registry.test', namespace: 'global', slug: 'demo'
        })
        return new Response(
          archive.buffer.slice(archive.byteOffset, archive.byteOffset + archive.byteLength) as ArrayBuffer,
          { status: 200 }
        )
      }
      return Response.json({ code: 404 }, { status: 404 })
    }) as typeof fetch

    await expect(installSkill({
      registry: 'http://registry.test',
      namespace: 'global',
      slug: 'demo',
      targets: [
        { agent: 'codex', rootDir: firstRoot, scope: 'project', source: 'explicit' },
        { agent: 'claude-code', rootDir: secondRoot, scope: 'project', source: 'explicit' }
      ],
      force: true,
      home
    })).rejects.toThrow('source conflict')

    expect(await readFile(join(firstSkillDir, 'SKILL.md'), 'utf-8')).toBe('# Old A')
    expect(await readFile(join(secondSkillDir, 'SKILL.md'), 'utf-8')).toBe('# Old B')
    const inventory = JSON.parse(await readFile(join(home, '.skillhub', 'inventory.json'), 'utf-8'))
    expect(inventory.items[0]).toMatchObject({ version: '0.1.0' })
    expect(inventory.items[0].targets).toHaveLength(2)
    for (const rootDir of [firstRoot, secondRoot]) {
      const entries = await readdir(rootDir)
      expect(entries.some(name => name.includes('skillhub-backup'))).toBe(false)
      expect(await exists(await skillTargetLockPath(rootDir, 'demo'))).toBe(false)
    }
  })

  test('rejects an active target lock and recovers a dead-process lock', async () => {
    globalThis.fetch = installFetch({ 'SKILL.md': '# New' })
    const home = await mkdtemp(join(tmpdir(), 'skillhub-install-home-'))
    const rootDir = await mkdtemp(join(tmpdir(), 'skillhub-install-root-'))
    const lockPath = await skillTargetLockPath(rootDir, 'demo')
    await mkdir(lockPath)

    await expect(installSkill({
      registry: 'http://registry.test', namespace: 'global', slug: 'demo',
      targets: [{ agent: 'codex', rootDir, scope: 'project', source: 'explicit' }],
      force: false, home
    })).rejects.toThrow('install target is busy')
    expect(await exists(join(rootDir, 'demo'))).toBe(false)

    await rm(lockPath, { recursive: true })
    await mkdir(lockPath)
    const staleTime = new Date(Date.now() - 60_000)
    await utimes(lockPath, staleTime, staleTime)
    await installSkill({
      registry: 'http://registry.test', namespace: 'global', slug: 'demo',
      targets: [{ agent: 'codex', rootDir, scope: 'project', source: 'explicit' }],
      force: false, home
    })
    expect(await readFile(join(rootDir, 'demo', 'SKILL.md'), 'utf-8')).toBe('# New')
    expect(await exists(lockPath)).toBe(false)
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
