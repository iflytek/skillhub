import { afterEach, describe, expect, test } from 'bun:test'
import { mkdir, readFile, readdir, realpath, rm, symlink, writeFile } from 'node:fs/promises'
import { join, normalize } from 'node:path'
import { createTempHome } from '../../helpers/temp-env'
import { runHealth } from '../../../src/services/health-service'
import { snapshotSkillDirectory } from '../../../src/services/skill-fingerprint'

const homes: string[] = []
afterEach(async () => { for (const home of homes.splice(0)) await rm(home, { recursive: true, force: true }) })
async function fixture() {
  const { home, cwd } = await createTempHome()
  homes.push(home, cwd)
  await mkdir(join(home, '.skillhub'))
  return { home, cwd }
}
async function save(home: string, name: string, value: unknown) {
  await writeFile(join(home, '.skillhub', name + '.json'), JSON.stringify(value))
}
async function install(home: string, cwd: string, options: { modified?: boolean; disabled?: boolean; legacy?: boolean; registry?: string } = {}) {
  const root = join(await realpath(cwd), 'skills'), dir = join(root, 'example')
  await mkdir(join(dir, '.skillhub'), { recursive: true })
  await writeFile(join(dir, 'SKILL.md'), 'original content')
  const snapshot = await snapshotSkillDirectory(dir)
  const target = { rootDir: root, installDir: dir, agent: 'codex', version: '1.0.0', installedAt: '2026-01-01',
    ...(options.legacy ? {} : { fingerprint: snapshot.fingerprint }) }
  const metadata = { ...target, registry: options.registry ?? 'https://registry.example.com', namespace: 'global', slug: 'example',
    ...(options.legacy ? {} : { files: snapshot.files }) }
  await writeFile(join(dir, '.skillhub', 'metadata.json'), JSON.stringify(metadata))
  if (options.modified) await writeFile(join(dir, 'SKILL.md'), 'user changes must survive')
  if (options.disabled) { await writeFile(join(dir, 'SKILL.md.disabled'), 'original content'); await rm(join(dir, 'SKILL.md')) }
  await save(home, 'inventory', { schemaVersion: 2, records: [{ registry: metadata.registry, namespace: 'global', slug: 'example', targets: [target] }] })
  return dir
}
const env = { SKILLHUB_REGISTRY: 'https://registry.example.com' }

describe('read-only health diagnosis', () => {
  test('an empty HOME falls back to USERPROFILE without creating state', async () => {
    const { home, cwd } = await createTempHome(); homes.push(home, cwd)
    const result = await runHealth({ offline: true }, { env: { ...env, HOME: '', USERPROFILE: home } })
    expect(result.exitCode).toBe(0)
    expect(normalize(result.paths.config ?? '')).toBe(join(home, '.skillhub/config.json'))
    expect(await readdir(home)).toEqual([])
  })
  test('an unavailable home reports skipped remote checks without any requests', async () => {
    let calls = 0
    const result = await runHealth({}, { env: {}, fetch: async () => { calls++; throw new Error('unexpected') } })
    expect(result.exitCode).toBe(4)
    expect(result.checks.find(x => x.id === 'home')?.code).toBe('HOME_UNAVAILABLE')
    expect(result.checks.filter(x => x.status === 'skipped' && x.code === 'REGISTRY_UNRESOLVED')).toHaveLength(2)
    expect(calls).toBe(0)
  })
  test('empty home remains empty and offline performs zero network calls', async () => {
    const { home, cwd } = await createTempHome(); homes.push(home, cwd)
    let calls = 0
    const result = await runHealth({ offline: true }, { home, env, fetch: async () => { calls++; throw new Error('unexpected') } })
    expect(result.ok).toBe(true)
    expect(result.checks.filter(x => x.code === 'OFFLINE')).toHaveLength(2)
    expect(calls).toBe(0)
    expect(await readdir(home)).toEqual([])
  })
  test('source precedence is preserved without reading another registry installation', async () => {
    const { home, cwd } = await fixture()
    await save(home, 'config', { registry: 'https://configured.example.com', unknown: 'retained' })
    await save(home, 'credentials', { tokens: { 'https://selected.example.com': 'sk_stored_secret', 'https://other.example.com': 'sk_other_secret' } })
    const dir = await install(home, cwd, { registry: 'https://other.example.com' }); await rm(dir, { recursive: true })
    const result = await runHealth({ offline: true, registry: 'https://selected.example.com', token: 'sk_explicit_secret' }, { home, env: { ...env, SKILLHUB_TOKEN: 'sk_env_secret' } })
    expect(result.registrySource).toBe('argument'); expect(result.credentialSource).toBe('argument')
    expect(result.ok).toBe(true)
    expect(JSON.stringify(result)).not.toContain('sk_')
    expect(result.checks.some(x => x.code === 'NO_REGISTERED_INSTALLATIONS')).toBe(true)
  })
  test('malformed config never probes the default registry or changes bytes', async () => {
    const { home } = await fixture(); await writeFile(join(home, '.skillhub/config.json'), '{secret: broken}')
    let calls = 0
    const result = await runHealth({}, { home, env: {}, fetch: async () => { calls++; throw new Error('unexpected') } })
    expect(result.exitCode).toBe(4); expect(result.registry).toBeNull(); expect(calls).toBe(0)
    expect(await readFile(join(home, '.skillhub/config.json'), 'utf8')).toBe('{secret: broken}')
    expect(JSON.stringify(result)).not.toContain('secret: broken')
  })
  test('unreadable configuration is distinct from missing, including on Windows', async () => {
    const { home } = await fixture()
    const result = await runHealth({ offline: true }, { home, env, readStateFile: async path => {
      if (path.endsWith('config.json')) throw Object.assign(new Error('private failure'), { code: 'EACCES' })
      return readFile(path, 'utf8')
    } })
    expect(result.exitCode).toBe(4)
    expect(result.checks.find(x => x.id === 'config')?.code).toBe('STATE_UNREADABLE')
    expect(JSON.stringify(result)).not.toContain('private failure')
  })
  test.each(['file:///secret', 'https://user:password@example.com', 'https://example.com?token=secret', 'https://example.com#secret'])('unsafe registry %s is not echoed or contacted', async registry => {
    const { home } = await fixture(); let calls = 0
    const result = await runHealth({ registry }, { home, env: {}, fetch: async () => { calls++; throw new Error('unexpected') } })
    expect(result.exitCode).toBe(1); expect(result.registry).toBeNull(); expect(calls).toBe(0)
    expect(JSON.stringify(result)).not.toContain(registry)
  })
  test('registered modified content is a warning, with no backups or locks', async () => {
    const { home, cwd } = await fixture(); const dir = await install(home, cwd, { modified: true })
    const before = await readFile(join(home, '.skillhub/inventory.json'))
    const result = await runHealth({ offline: true }, { home, env })
    expect(result.exitCode).toBe(0); expect(result.checks.some(x => x.code === 'CONTENT_MODIFIED')).toBe(true)
    expect(await readFile(join(home, '.skillhub/inventory.json'))).toEqual(before)
    expect(await readFile(join(dir, 'SKILL.md'), 'utf8')).toBe('user changes must survive')
    expect(await readdir(join(home, '.skillhub'))).toEqual(['inventory.json'])
    expect(await readdir(join(cwd, 'skills'))).toEqual(['example'])
  })
  test('disabled content is normalized for comparison and remains disabled', async () => {
    const { home, cwd } = await fixture(); const dir = await install(home, cwd, { disabled: true })
    const result = await runHealth({ offline: true }, { home, env })
    expect(result.checks.some(x => x.code === 'INSTALLATION_VERIFIED')).toBe(true)
    expect((await readdir(dir)).sort()).toEqual(['.skillhub', 'SKILL.md.disabled'])
  })
  test('legacy missing baseline is not synthesized or certified', async () => {
    const { home, cwd } = await fixture(); await install(home, cwd, { legacy: true })
    const before = await readFile(join(home, '.skillhub/inventory.json'))
    const result = await runHealth({ offline: true }, { home, env })
    expect(result.ok).toBe(true); expect(result.checks.some(x => x.code === 'INTEGRITY_UNVERIFIED')).toBe(true)
    expect(await readFile(join(home, '.skillhub/inventory.json'))).toEqual(before)
  })
  test('v3 recorded activation state must agree with its files', async () => {
    const { home, cwd } = await fixture(); await install(home, cwd, { disabled: true })
    const path = join(home, '.skillhub/inventory.json')
    const inventory = JSON.parse(await readFile(path, 'utf8'))
    inventory.schemaVersion = 3; inventory.records[0].targets[0].installationState = 'enabled'
    await writeFile(path, JSON.stringify(inventory))
    const result = await runHealth({ offline: true }, { home, env })
    expect(result.exitCode).toBe(4); expect(result.checks.some(x => x.code === 'INSTALLATION_UNVERIFIED')).toBe(true)
  })
  test('a missing installation is a filesystem failure', async () => {
    const { home, cwd } = await fixture(); const dir = await install(home, cwd); await rm(dir, { recursive: true })
    const result = await runHealth({ offline: true }, { home, env })
    expect(result.exitCode).toBe(4); expect(result.checks.some(x => x.code === 'INSTALLATION_UNVERIFIED')).toBe(true)
  })
  test('symlinked content is not inspected as a healthy installation', async () => {
    const { home, cwd } = await fixture(); const dir = await install(home, cwd)
    await symlink(join(cwd, 'unrelated'), join(dir, 'untrusted'), process.platform === 'win32' ? 'junction' : 'dir')
    const result = await runHealth({ offline: true }, { home, env })
    expect(result.exitCode).toBe(4)
  })
})
