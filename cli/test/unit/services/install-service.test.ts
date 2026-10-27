import { createHash } from 'node:crypto'
import { access, chmod, mkdir, mkdtemp, readFile, readdir, rm, symlink, utimes, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join, sep } from 'node:path'
import { afterEach, describe, expect, test } from 'bun:test'
import { zipSync } from 'fflate'
import type { InstallOptions } from '../../../src/services/install-service'
import type { Inventory } from '../../../src/stores/inventory-store'
import { InventoryStore, parseInventory } from '../../../src/stores/inventory-store'
import { installSkill } from '../../../src/services/install-service'
import { planSkillUpgrades } from '../../../src/services/upgrade-service'
import { removeLocalSkill } from '../../../src/services/remove-service'
import { skillTargetLockPath } from '../../../src/services/skill-target-lock'
import { EXIT } from '../../../src/shared/constants'

const originalFetch = globalThis.fetch

async function exists(path: string): Promise<boolean> {
  try {
    await access(path)
    return true
  } catch {
    return false
  }
}

function skillFingerprint(zipEntries: Record<string, string>): string {
  const aggregate = createHash('sha256')
  for (const [name, content] of Object.entries(zipEntries).sort(([left], [right]) => left < right ? -1 : left > right ? 1 : 0)) {
    const fileHash = createHash('sha256').update(content).digest('hex')
    aggregate.update(`${name}:${fileHash}\n`, 'utf8')
  }
  return `sha256:${aggregate.digest('hex')}`
}

function installFetch(zipEntries: Record<string, string>, fingerprint = skillFingerprint(zipEntries)): typeof fetch {
  const archive = zipSync(Object.fromEntries(
    Object.entries(zipEntries).map(([name, content]) => [name, new TextEncoder().encode(content)])
  ))

  return installFetchWithDownloadResponse(new Response(
    archive.buffer.slice(archive.byteOffset, archive.byteOffset + archive.byteLength) as ArrayBuffer,
    { status: 200 }
  ), fingerprint)
}

function installFetchWithDownloadResponse(downloadResponse: Response, fingerprint = 'fp'): typeof fetch {
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
          fingerprint,
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

  test.each(['{"schemaVersion":1,"defaultLocale":"en","examples":{"en":[{"id":"one","title":"One","prompt":"Use {{skill}}"}]}}', '{invalid'])('installs authored examples unchanged and records their complete baseline: %s', async content => {
    const root = await mkdtemp(join(tmpdir(), 'skillhub-packaged-examples-'))
    try {
      globalThis.fetch = installFetch({ 'SKILL.md': '# Demo', '.skillhub/examples.json': content })
      await installSkill({ registry: 'http://registry.test', namespace: 'global', slug: 'demo',
        targets: [{ agent: 'dsh', rootDir: join(root, 'skills'), scope: 'project', source: 'explicit' }], force: false, home: join(root, 'home') })
      const target = join(root, 'skills/demo')
      expect(await readFile(join(target, '.skillhub/examples.json'), 'utf8')).toBe(content)
      const metadata = JSON.parse(await readFile(join(target, '.skillhub/metadata.json'), 'utf8'))
      expect(metadata.files['.skillhub/examples.json']).toBe(createHash('sha256').update(content).digest('hex'))
      expect(metadata.files['.skillhub/metadata.json']).toBeUndefined()
    } finally { await rm(root, { recursive: true, force: true }) }
  })

  test('rejects authored installation metadata without replacing target or inventory', async () => {
    const root = await mkdtemp(join(tmpdir(), 'skillhub-forged-metadata-'))
    try {
      globalThis.fetch = installFetch({ 'SKILL.md': '# Demo', '.skillhub/metadata.json': '{"registry":"evil"}' })
      await expect(installSkill({ registry: 'http://registry.test', namespace: 'global', slug: 'demo',
        targets: [{ agent: 'dsh', rootDir: join(root, 'skills'), scope: 'project', source: 'explicit' }], force: false, home: join(root, 'home') })).rejects.toThrow('Installer metadata')
      expect(await exists(join(root, 'skills/demo'))).toBe(false)
      expect((await new InventoryStore(join(root, 'home')).read()).items).toEqual([])
    } finally { await rm(root, { recursive: true, force: true }) }
  })

  test.each(['journal-failure', 'directory-collision'] as const)('staging intent precedes directory creation: %s', async failure => {
    const root = await mkdtemp(join(tmpdir(), 'skillhub-staging-intent-'))
    const rootDir = join(root, 'skills')
    let temporary = ''
    globalThis.fetch = installFetch({ 'SKILL.md': '# Demo' })
    try {
      await expect(installSkill({ registry: 'http://registry.test', namespace: 'global', slug: 'demo',
        targets: [{ agent: 'dsh', rootDir, scope: 'project', source: 'explicit' }], force: false, home: join(root, 'home'),
        transactionObserver: { beforeStaging: async stage => {
          temporary = stage.stagingDir
          expect(await exists(temporary)).toBe(false)
          expect(await exists(rootDir)).toBe(false)
          if (failure === 'journal-failure') throw new Error('Journal unavailable')
          await mkdir(temporary, { recursive: true })
          await writeFile(join(temporary, 'unrelated.txt'), 'Preserve collision')
        } }
      })).rejects.toThrow()
      expect(await exists(join(rootDir, 'demo'))).toBe(false)
      if (failure === 'journal-failure') expect(await exists(rootDir)).toBe(false)
      else expect(await readFile(join(temporary, 'unrelated.txt'), 'utf8')).toBe('Preserve collision')
    } finally { await rm(root, { recursive: true, force: true }) }
  })

  test.each(['staging', 'replace'] as const)('native policy denial at %s prevents publishing files', async deniedPhase => {
    const root = await mkdtemp(join(tmpdir(), 'skillhub-policy-denial-'))
    const rootDir = join(root, 'skills')
    const home = join(root, 'home')
    globalThis.fetch = installFetch({ 'SKILL.md': '# Demo' })
    const phases: string[] = []
    try {
      await expect(installSkill({ registry: 'http://registry.test', namespace: 'global', slug: 'demo',
        targets: [{ agent: 'dsh', rootDir, scope: 'project', source: 'explicit' }], force: false, home,
        authorizeTargetMutation: async (paths, phase) => {
          phases.push(phase)
          expect(paths.every(path => path === rootDir || path.startsWith(rootDir + sep))).toBe(true)
          if (phase === deniedPhase) throw new Error('policy denied')
        }
      })).rejects.toThrow('policy denied')
      expect(phases).toContain(deniedPhase)
      expect(await exists(join(rootDir, 'demo'))).toBe(false)
      expect((await new InventoryStore(home).read()).items).toEqual([])
      if (deniedPhase === 'staging') expect(await exists(rootDir)).toBe(false)
      else expect(await readdir(rootDir)).toEqual([])
    } finally { await rm(root, { recursive: true, force: true }) }
  })

  test('policy revocation after replacement retains files when rollback is denied', async () => {
    const root = await mkdtemp(join(tmpdir(), 'skillhub-policy-rollback-'))
    const rootDir = join(root, 'skills')
    const home = join(root, 'home')
    const options: InstallOptions = { registry: 'http://registry.test', namespace: 'global', slug: 'demo',
      targets: [{ agent: 'dsh', rootDir, scope: 'project', source: 'explicit' }], force: false, home }
    try {
      globalThis.fetch = installFetch({ 'SKILL.md': '# Old' })
      await installSkill(options)
      const before = await new InventoryStore(home).read()
      globalThis.fetch = installFetch({ 'SKILL.md': '# New' })
      let revoked = false
      await expect(installSkill({ ...options, force: true,
        transactionObserver: { beforeInventoryCommit: async () => { revoked = true; throw new Error('revoked') } },
        authorizeTargetMutation: async () => { if (revoked) throw new Error('policy denied') }
      })).rejects.toThrow('rollback was incomplete')
      expect(await readFile(join(rootDir, 'demo/SKILL.md'), 'utf8')).toBe('# New')
      const backup = (await readdir(rootDir)).find(name => name.startsWith('demo.skillhub-backup-'))!
      expect(await readFile(join(rootDir, backup, 'SKILL.md'), 'utf8')).toBe('# Old')
      expect(await new InventoryStore(home).read()).toEqual(before)
    } finally { await rm(root, { recursive: true, force: true }) }
  })

  test.each(['beforeMove', 'afterMove', 'beforeInventoryCommit'] as const)('observer %s failure restores the old files and inventory', async stage => {
    const root = await mkdtemp(join(tmpdir(), 'skillhub-observer-failure-'))
    const rootDir = join(root, 'skills')
    const home = join(root, 'home')
    const options: InstallOptions = { registry: 'http://registry.test', namespace: 'global', slug: 'demo',
      targets: [{ agent: 'codex', rootDir, scope: 'project', source: 'explicit' }], force: false, home }
    try {
      globalThis.fetch = installFetch({ 'SKILL.md': '# Old' })
      await installSkill(options)
      const before = await new InventoryStore(home).read()
      globalThis.fetch = installFetch({ 'SKILL.md': '# New' })
      await expect(installSkill({ ...options, force: true, transactionObserver: {
        [stage]: async () => { throw new Error('journal unavailable') }
      } })).rejects.toThrow('journal unavailable')
      expect(await readFile(join(rootDir, 'demo/SKILL.md'), 'utf8')).toBe('# Old')
      expect(await new InventoryStore(home).read()).toEqual(before)
      expect(await readdir(rootDir)).toEqual(['demo'])
    } finally { await rm(root, { recursive: true, force: true }) }
  })

  test('awaits beforeMove persistence before moving files', async () => {
    const root = await mkdtemp(join(tmpdir(), 'skillhub-observer-wait-'))
    const rootDir = join(root, 'skills')
    let entered!: () => void
    let resume!: () => void
    const reached = new Promise<void>(resolve => { entered = resolve })
    const release = new Promise<void>(resolve => { resume = resolve })
    globalThis.fetch = installFetch({ 'SKILL.md': '# Demo' })
    const pending = installSkill({ registry: 'http://registry.test', namespace: 'global', slug: 'demo',
      targets: [{ agent: 'codex', rootDir, scope: 'project', source: 'explicit' }], force: false, home: join(root, 'home'),
      transactionObserver: { beforeMove: async move => {
        expect(move.phase).toBe('install')
        expect(await exists(move.from)).toBe(true)
        entered()
        await release
      } }
    })
    try {
      await reached
      expect(await exists(join(rootDir, 'demo'))).toBe(false)
      resume()
      await pending
      expect(await readFile(join(rootDir, 'demo/SKILL.md'), 'utf8')).toBe('# Demo')
    } finally { resume(); await pending.catch(() => {}); await rm(root, { recursive: true, force: true }) }
  })

  test('after-commit and cleanup observer failures report warnings without rolling back committed files', async () => {
    const root = await mkdtemp(join(tmpdir(), 'skillhub-observer-committed-'))
    const rootDir = join(root, 'skills')
    const home = join(root, 'home')
    const options: InstallOptions = { registry: 'http://registry.test', namespace: 'global', slug: 'demo',
      targets: [{ agent: 'codex', rootDir, scope: 'project', source: 'explicit' }], force: false, home }
    try {
      globalThis.fetch = installFetch({ 'SKILL.md': '# Old' })
      await installSkill(options)
      const previous = await new InventoryStore(home).read()
      globalThis.fetch = installFetch({ 'SKILL.md': '# New' })
      const events: string[] = []
      const result = await installSkill({ ...options, force: true, transactionObserver: {
        beforeMove: async move => { events.push(`before:${move.phase}`); expect(await exists(move.from)).toBe(true); expect(await exists(move.to)).toBe(false) },
        afterMove: async move => { events.push(`after:${move.phase}`); expect(await exists(move.from)).toBe(false); expect(await exists(move.to)).toBe(true) },
        beforeInventoryCommit: async commit => {
          events.push('before:commit')
          expect(commit.before).toEqual(previous)
          expect(commit.after.items[0]?.targets[0]!.fingerprint).toBe(skillFingerprint({ 'SKILL.md': '# New' }))
          commit.after.items.length = 0 // Observers cannot mutate the actual pending inventory.
        },
        afterInventoryCommit: async () => {
          events.push('after:commit')
          expect((await new InventoryStore(home).read()).items[0]?.targets[0]!.fingerprint).toBe(skillFingerprint({ 'SKILL.md': '# New' }))
          throw new Error('journal completion failed')
        },
        cleanupResult: async cleanup => {
          events.push('cleanup')
          expect(cleanup.removed).toBe(true)
          expect(await exists(cleanup.path)).toBe(false)
          throw new Error('cleanup journal failed')
        }
      } })
      expect(events).toEqual(['before:backup', 'after:backup', 'before:install', 'after:install', 'before:commit', 'after:commit', 'cleanup'])
      expect(result.transactionWarnings?.map(warning => warning.stage)).toEqual(['afterInventoryCommit', 'cleanupResult'])
      expect(await readFile(join(rootDir, 'demo/SKILL.md'), 'utf8')).toBe('# New')
      expect((await new InventoryStore(home).read()).items).toHaveLength(1)
      expect(await readdir(rootDir)).toEqual(['demo'])
    } finally { await rm(root, { recursive: true, force: true }) }
  })

  test.skipIf(process.platform === 'win32' || process.getuid?.() === 0)('failed backup cleanup preserves the committed version and reports the retained path', async () => {
    const root = await mkdtemp(join(tmpdir(), 'skillhub-cleanup-failure-'))
    const rootDir = join(root, 'skills')
    const home = join(root, 'home')
    let backup = ''
    const options: InstallOptions = { registry: 'http://registry.test', namespace: 'global', slug: 'demo',
      targets: [{ agent: 'codex', rootDir, scope: 'project', source: 'explicit' }], force: false, home }
    try {
      globalThis.fetch = installFetch({ 'SKILL.md': '# Old' })
      await installSkill(options)
      globalThis.fetch = installFetch({ 'SKILL.md': '# New' })
      const result = await installSkill({ ...options, force: true, transactionObserver: {
        beforeMove: async move => { if (move.phase === 'backup') backup = move.to },
        afterInventoryCommit: async () => { await chmod(backup, 0o000) },
        cleanupResult: async cleanup => {
          expect(cleanup.path).toBe(backup)
          expect(cleanup.removed).toBe(false)
          expect(cleanup.error).toBeDefined()
        }
      } })
      expect(result.transactionWarnings).toHaveLength(1)
      expect(result.transactionWarnings?.[0]).toMatchObject({ stage: 'backupCleanup', path: backup })
      expect(await exists(backup)).toBe(true)
      expect(await readFile(join(rootDir, 'demo/SKILL.md'), 'utf8')).toBe('# New')
      expect((await new InventoryStore(home).read()).items[0]?.targets[0]!.fingerprint).toBe(skillFingerprint({ 'SKILL.md': '# New' }))
    } finally {
      if (backup) await chmod(backup, 0o700).catch(() => {})
      await rm(root, { recursive: true, force: true })
    }
  })

  test.each(['version', 'fingerprint', 'ownership', 'targets', 'source'] as const)('inventory CAS scopes %s drift introduced after moving files', async drift => {
    const root = await mkdtemp(join(tmpdir(), 'skillhub-cas-drift-'))
    const rootDir = join(root, 'skills')
    const home = join(root, 'home')
    const store = new InventoryStore(home)
    const options: InstallOptions = { registry: 'http://registry.test', namespace: 'global', slug: 'demo',
      targets: [{ agent: 'codex', rootDir, scope: 'project', source: 'explicit' }], force: false, home }
    let loggedCommit = false
    try {
      globalThis.fetch = installFetch({ 'SKILL.md': '# Old' })
      await installSkill(options)
      globalThis.fetch = installFetch({ 'SKILL.md': drift === 'targets' ? '# Old' : '# New' })
      const operation = installSkill({ ...options, force: true, transactionObserver: {
        afterMove: async move => {
          if (move.phase !== 'install') return
          await store.mutateAtomic(inventory => {
            const item = inventory.items[0]!
            if (drift === 'version') item.targets[0]!.version = '9.0.0'
            if (drift === 'fingerprint') item.targets[0]!.fingerprint = 'changed'
            if (drift === 'ownership') item.targets[0]!.installedBy = ['suite:other']
            if (drift === 'targets') item.targets.push({ ...item.targets[0]!, rootDir: join(root, 'other'), installDir: join(root, 'other/demo') })
            if (drift === 'source') item.namespace = 'another'
          })
        },
        beforeInventoryCommit: async () => { loggedCommit = true }
      } })
      if (drift === 'targets') {
        await operation
        expect(loggedCommit).toBe(true)
        expect((await store.read()).items[0]!.targets).toHaveLength(2)
        expect((await store.read()).items[0]!.targets.find(target => target.rootDir === join(root, 'other'))).toBeDefined()
        expect(await readFile(join(rootDir, 'demo/SKILL.md'), 'utf8')).toBe('# Old')
        return
      }
      await expect(operation).rejects.toThrow('installed skill records changed before commit')
      expect(loggedCommit).toBe(false)
      const changed = (await store.read()).items[0]!
      expect(drift === 'version' ? changed.targets[0]!.version : drift === 'fingerprint' ? changed.targets[0]!.fingerprint
        : drift === 'ownership' ? changed.targets[0]!.installedBy?.[0] : changed.namespace)
        .toBe(drift === 'version' ? '9.0.0' : drift === 'fingerprint' ? 'changed' : drift === 'ownership' ? 'suite:other' : 'another')
      expect(await readFile(join(rootDir, 'demo/SKILL.md'), 'utf8')).toBe('# Old')
    } finally { await rm(root, { recursive: true, force: true }) }
  })

  test('rejects a stale explicit plan before download but preserves unrelated inventory changes', async () => {
    const root = await mkdtemp(join(tmpdir(), 'skillhub-cas-plan-'))
    const rootDir = join(root, 'skills')
    const home = join(root, 'home')
    const store = new InventoryStore(home)
    const options: InstallOptions = { registry: 'http://registry.test', namespace: 'global', slug: 'demo',
      targets: [{ agent: 'codex', rootDir, scope: 'project', source: 'explicit' }], force: false, home }
    try {
      globalThis.fetch = installFetch({ 'SKILL.md': '# Old' })
      await installSkill(options)
      const plan = (await store.read()).items[0]!
      await store.mutateAtomic(inventory => { inventory.items[0]!.targets[0]!.version = '2.0.0' })
      globalThis.fetch = (() => { throw new Error('must not download') }) as unknown as typeof fetch
      await expect(installSkill({ ...options, force: true, expectedInventoryItem: plan })).rejects.toThrow('installed skill records changed')
      const current = (await store.read()).items[0]!
      globalThis.fetch = installFetch({ 'SKILL.md': '# New' })
      await installSkill({ ...options, force: true, expectedInventoryItem: current, transactionObserver: {
        afterMove: async move => {
          if (move.phase !== 'install') return
          await store.upsertTarget('http://other.test', 'team', 'other', '1.0.0', {
            agent: 'codex', rootDir: join(root, 'other'), installDir: join(root, 'other/other'), installedAt: 'now'
          })
        }
      } })
      expect((await store.read()).items.map(item => item.slug).sort()).toEqual(['demo', 'other'])
      expect(await readFile(join(rootDir, 'demo/SKILL.md'), 'utf8')).toBe('# New')
    } finally { await rm(root, { recursive: true, force: true }) }
  })

  test.each(['confirmed', 'unknown'] as const)('inventory response loss with %s result never rolls back a possibly committed replacement', async outcome => {
    class LostResponseStore extends InventoryStore {
      protected override async writeUnderLock(inventory: Inventory): Promise<void> {
        await super.writeUnderLock(inventory)
        if (outcome === 'unknown') await super.writeUnderLock({ items: [{ ...inventory.items[0]!, targets: inventory.items[0]!.targets.map(target => ({ ...target, version: 'unverifiable' })) }] })
        throw new Error('response lost after rename')
      }
    }
    const root = await mkdtemp(join(tmpdir(), 'skillhub-commit-outcome-'))
    const rootDir = join(root, 'skills')
    const home = join(root, 'home')
    const options: InstallOptions = { registry: 'http://registry.test', namespace: 'global', slug: 'demo',
      targets: [{ agent: 'codex', rootDir, scope: 'project', source: 'explicit' }], force: false, home }
    try {
      globalThis.fetch = installFetch({ 'SKILL.md': '# Old' })
      await installSkill(options)
      globalThis.fetch = installFetch({ 'SKILL.md': '# New' })
      const operation = installSkill({ ...options, force: true, inventoryStore: new LostResponseStore(home) })
      if (outcome === 'confirmed') {
        const result = await operation
        expect(result.installed).toHaveLength(1)
        expect(await readdir(rootDir)).toEqual(['demo'])
      } else {
        await expect(operation).rejects.toMatchObject({ details: { reason: 'INSTALL_COMMIT_UNKNOWN', recovery: 'unknown' } })
        const backups = (await readdir(rootDir)).filter(name => name.includes('.skillhub-backup-'))
        expect(backups).toHaveLength(1)
        expect(await readFile(join(rootDir, backups[0]!, 'SKILL.md'), 'utf8')).toBe('# Old')
        expect((await new InventoryStore(home).read()).items[0]?.targets[0]!.version).toBe('unverifiable')
      }
      expect(await readFile(join(rootDir, 'demo/SKILL.md'), 'utf8')).toBe('# New')
    } finally { await rm(root, { recursive: true, force: true }) }
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

  test('rejects a downloaded fingerprint mismatch before replacing files or inventory', async () => {
    globalThis.fetch = installFetch({ 'SKILL.md': '# Unexpected' }, 'sha256:unexpected')
    const home = await mkdtemp(join(tmpdir(), 'skillhub-install-home-'))
    const rootDir = await mkdtemp(join(tmpdir(), 'skillhub-install-root-'))
    const skillDir = join(rootDir, 'demo')
    const inventoryPath = join(home, '.skillhub', 'inventory.json')
    await mkdir(skillDir, { recursive: true })
    await writeFile(join(skillDir, 'SKILL.md'), '# Existing')
    await writeManagedMetadata(skillDir)
    await mkdir(join(home, '.skillhub'), { recursive: true })
    const inventoryBefore = JSON.stringify({
      items: [{
        registry: 'http://registry.test',
        namespace: 'global',
        slug: 'demo',
        version: '0.1.0',
        targets: [{
          agent: 'codex',
          rootDir,
          installDir: skillDir,
          installedAt: '2026-09-01T00:00:00Z'
        }]
      }]
    })
    await writeFile(inventoryPath, inventoryBefore)

    await expect(installSkill({
      registry: 'http://registry.test',
      namespace: 'global',
      slug: 'demo',
      targets: [{ agent: 'codex', rootDir, scope: 'project', source: 'explicit' }],
      force: true,
      home
    })).rejects.toMatchObject({
      exitCode: EXIT.validation,
      message: 'downloaded skill fingerprint does not match the resolved release'
    })

    expect(await readFile(join(skillDir, 'SKILL.md'), 'utf-8')).toBe('# Existing')
    expect(await new InventoryStore(home).read()).toEqual(parseInventory(JSON.parse(inventoryBefore)))
    const backup = (await readdir(join(home, '.skillhub'))).find(name => name.startsWith('inventory.v1.'))!
    expect(await readFile(join(home, '.skillhub', backup), 'utf8')).toBe(inventoryBefore)
    expect((await readdir(rootDir)).some(name => name.includes('.install-'))).toBe(false)
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
    expect(inventory.records).toHaveLength(1)
    expect(inventory.records[0].targets).toEqual([
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
    expect((JSON.parse(await readFile(inventoryPath, 'utf-8'))).records).toEqual([])
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
    expect(inventory.records[0].targets[0]).toMatchObject({
      version: '1.0.0',
      fingerprint: skillFingerprint({ 'SKILL.md': '# Committed' })
    })
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
    expect(inventory.records).toHaveLength(1)
    expect(inventory.records[0]).toMatchObject({ namespace: 'global', slug: 'demo' })
    expect(inventory.records[0].targets).toHaveLength(1)
    expect(inventory.records[0].targets[0]!.installDir).toBe(skillDir)
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
            fingerprint: skillFingerprint({ 'SKILL.md': '# New' }),
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
          fingerprint: skillFingerprint({ 'SKILL.md': '# New' }), downloadUrl: '/download'
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
    expect(inventory.records[0].targets[0]).toMatchObject({ version: '0.1.0' })
    expect(inventory.records[0].targets).toHaveLength(2)
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
    expect(inventory.records).toHaveLength(1)
    expect(inventory.records[0]).toMatchObject({ namespace: 'global', slug: 'demo' })
    expect(inventory.records[0].targets[0].version).toBe('0.1.0')
    expect(inventory.records[0].targets[0]!.installDir).toBe(skillDir)
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
      force: false,
      home: join(rootDir, 'home')
    })).rejects.toThrow('download exceeds maximum package size')
  })
})
