import { createHash } from 'node:crypto'
import { access, mkdir, mkdtemp, readFile, readdir, rename, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { describe, expect, test } from 'bun:test'
import { zipSync } from 'fflate'
import { SkillHubClient, type SuiteInstallPlan } from '../../../src/clients/skillhub-client'
import { InventoryStore } from '../../../src/stores/inventory-store'
import {
  checkSuite,
  installSuite,
  planSuiteUpgrade,
  removeSuite,
  upgradeSuite
} from '../../../src/services/suite-service'

const registry = 'http://registry.test'

function archive(content: string): { bytes: Uint8Array; fingerprint: string } {
  const bytes = zipSync({ 'SKILL.md': new TextEncoder().encode(content) })
  const aggregate = createHash('sha256')
  const fileHash = createHash('sha256').update(content).digest('hex')
  aggregate.update(`SKILL.md:${fileHash}\n`, 'utf8')
  return { bytes, fingerprint: `sha256:${aggregate.digest('hex')}` }
}

function clientFor(
  plan: SuiteInstallPlan,
  downloads: Record<string, Uint8Array>,
  capabilities: string[] = ['skill-suite-v1'],
  available = true
): SkillHubClient {
  const fetchImpl = (async (input: URL | RequestInfo) => {
    const url = new URL(String(input))
    if (url.pathname === '/.well-known/clawhub.json') {
      return Response.json({ apiBase: '/api/v1', capabilities })
    }
    if (url.pathname.endsWith('/install-plan')) return Response.json({ code: 0, data: plan })
    if (url.pathname === `/api/v1/suites/${plan.namespace}/${plan.slug}`) {
      return Response.json({ code: 0, data: {
        id: 1,
        versionId: 2,
        namespace: plan.namespace,
        slug: plan.slug,
        displayName: plan.slug,
        version: plan.version,
        status: 'PUBLISHED',
        visibility: 'PUBLIC',
        available,
        members: plan.members.map(member => ({
          ...member,
          blockingReason: available ? null : 'member_unavailable'
        }))
      } })
    }
    const bytes = downloads[url.pathname]
    return bytes
      ? new Response(bytes.slice().buffer as ArrayBuffer, { status: 200 })
      : Response.json({ code: 404 }, { status: 404 })
  }) as unknown as typeof fetch
  return new SkillHubClient(registry, undefined, fetchImpl)
}

function clientWithPlanFailure(status: number, message: string): SkillHubClient {
  const fetchImpl = (async (input: URL | RequestInfo) => {
    const url = new URL(String(input))
    if (url.pathname === '/.well-known/clawhub.json') {
      return Response.json({ apiBase: '/api/v1', capabilities: ['skill-suite-v1'] })
    }
    if (url.pathname.endsWith('/install-plan')) {
      return Response.json({ code: status, msg: message }, { status })
    }
    return Response.json({ code: 404 }, { status: 404 })
  }) as unknown as typeof fetch
  return new SkillHubClient(registry, undefined, fetchImpl)
}

function makePlan(): { plan: SuiteInstallPlan; downloads: Record<string, Uint8Array> } {
  const alpha = archive('# Alpha')
  const beta = archive('# Beta')
  return {
    plan: {
      operationId: 'operation-1',
      namespace: 'global',
      slug: 'starter-pack',
      version: '1.0.0',
      fingerprint: 'sha256:suite',
      members: [
        {
          skillId: 10,
          skillVersionId: 11,
          namespace: 'global',
          slug: 'alpha',
          version: '1.0.0',
          fingerprint: alpha.fingerprint,
          downloadUrl: '/downloads/alpha',
          position: 0,
          entry: true
        },
        {
          skillId: 20,
          skillVersionId: 21,
          namespace: 'global',
          slug: 'beta',
          version: '2.0.0',
          fingerprint: beta.fingerprint,
          downloadUrl: '/downloads/beta',
          position: 1,
          entry: false
        }
      ]
    },
    downloads: { '/downloads/alpha': alpha.bytes, '/downloads/beta': beta.bytes }
  }
}

async function exists(path: string): Promise<boolean> {
  try {
    await access(path)
    return true
  } catch {
    return false
  }
}

describe('Suite local lifecycle', () => {
  test('installs every exact member and writes one Suite inventory snapshot', async () => {
    const home = await mkdtemp(join(tmpdir(), 'skillhub-suite-home-'))
    const rootDir = await mkdtemp(join(tmpdir(), 'skillhub-suite-root-'))
    const { plan, downloads } = makePlan()

    const result = await installSuite({
      registry,
      namespace: 'global',
      slug: 'starter-pack',
      targets: [{ agent: 'codex', rootDir, scope: 'project', source: 'explicit' }],
      force: false,
      home,
      client: clientFor(plan, downloads)
    })

    expect(result.installed).toHaveLength(2)
    expect(await readFile(join(rootDir, 'alpha', 'SKILL.md'), 'utf8')).toBe('# Alpha')
    expect(await readFile(join(rootDir, 'beta', 'SKILL.md'), 'utf8')).toBe('# Beta')
    const inventory = JSON.parse(await readFile(join(home, '.skillhub', 'inventory.json'), 'utf8'))
    expect(inventory.suites).toEqual([expect.objectContaining({ slug: 'starter-pack', version: '1.0.0' })])
    expect(inventory.items).toHaveLength(2)
    expect(inventory.items[0].installedBy).toEqual(['suite:@global/starter-pack@1.0.0'])

    const checked = await checkSuite({
      registry,
      namespace: 'global',
      slug: 'starter-pack',
      home,
      client: clientFor(plan, downloads)
    })
    expect(checked.current).toBe(true)
    expect(checked.members.every(member => member.status === 'ok')).toBe(true)
  })

  test('serializes the same Suite across different Agent targets', async () => {
    const home = await mkdtemp(join(tmpdir(), 'skillhub-suite-home-'))
    const firstRoot = await mkdtemp(join(tmpdir(), 'skillhub-suite-first-root-'))
    const secondRoot = await mkdtemp(join(tmpdir(), 'skillhub-suite-second-root-'))
    const { plan, downloads } = makePlan()
    let signalFirstLocked: (() => void) | undefined
    let releaseFirst: (() => void) | undefined
    const firstLocked = new Promise<void>((resolvePromise) => { signalFirstLocked = resolvePromise })
    const holdFirst = new Promise<void>((resolvePromise) => { releaseFirst = resolvePromise })

    const firstInstall = installSuite({
      registry,
      namespace: 'global',
      slug: 'starter-pack',
      targets: [{ agent: 'codex', rootDir: firstRoot, scope: 'project', source: 'explicit' }],
      force: false,
      home,
      client: clientFor(plan, downloads),
      afterTargetLocksAcquired: async () => {
        signalFirstLocked?.()
        await holdFirst
      }
    })

    await firstLocked
    try {
      await expect(installSuite({
        registry,
        namespace: 'global',
        slug: 'starter-pack',
        targets: [{ agent: 'claude', rootDir: secondRoot, scope: 'project', source: 'explicit' }],
        force: false,
        home,
        client: clientFor(plan, downloads)
      })).rejects.toThrow('Suite operation is busy')
    } finally {
      releaseFirst?.()
      await firstInstall
    }

    const inventory = await new InventoryStore(home).read()
    expect(inventory.suites).toHaveLength(1)
    expect(inventory.suites?.[0]?.members.every(member =>
      member.installDirs.every(dir => dir.startsWith(firstRoot)))).toBe(true)
    expect(await exists(join(secondRoot, 'alpha'))).toBe(false)
  })

  test('does not change live directories or inventory when a member fingerprint fails', async () => {
    const home = await mkdtemp(join(tmpdir(), 'skillhub-suite-home-'))
    const rootDir = await mkdtemp(join(tmpdir(), 'skillhub-suite-root-'))
    const { plan, downloads } = makePlan()
    plan.members[1]!.fingerprint = 'sha256:wrong'

    await expect(installSuite({
      registry,
      namespace: 'global',
      slug: 'starter-pack',
      targets: [{ agent: 'codex', rootDir, scope: 'project', source: 'explicit' }],
      force: false,
      home,
      client: clientFor(plan, downloads)
    })).rejects.toThrow('fingerprint does not match')

    expect(await exists(join(rootDir, 'alpha'))).toBe(false)
    expect(await exists(join(rootDir, 'beta'))).toBe(false)
    expect(await exists(join(home, '.skillhub', 'inventory.json'))).toBe(false)
  })

  test('sends one idempotency key with the install-plan request', async () => {
    const home = await mkdtemp(join(tmpdir(), 'skillhub-suite-home-'))
    const rootDir = await mkdtemp(join(tmpdir(), 'skillhub-suite-root-'))
    const { plan, downloads } = makePlan()
    let idempotencyKey: string | null = null
    const fetchImpl = (async (input: URL | RequestInfo, init?: RequestInit) => {
      const url = new URL(String(input))
      if (url.pathname === '/.well-known/clawhub.json') {
        return Response.json({ capabilities: ['skill-suite-v1'] })
      }
      if (url.pathname.endsWith('/install-plan')) {
        idempotencyKey = new Headers(init?.headers).get('Idempotency-Key')
        return Response.json({ code: 0, data: plan })
      }
      const bytes = downloads[url.pathname]
      return bytes
        ? new Response(bytes.slice().buffer as ArrayBuffer)
        : Response.json({ code: 404 }, { status: 404 })
    }) as unknown as typeof fetch

    await installSuite({
      registry,
      namespace: 'global',
      slug: 'starter-pack',
      targets: [{ agent: 'codex', rootDir, scope: 'project', source: 'explicit' }],
      force: false,
      home,
      client: new SkillHubClient(registry, undefined, fetchImpl)
    })

    expect(idempotencyKey).toMatch(/^[0-9a-f-]{36}$/)
  })

  test('retries a transient install-plan failure with the same idempotency key', async () => {
    const home = await mkdtemp(join(tmpdir(), 'skillhub-suite-home-'))
    const rootDir = await mkdtemp(join(tmpdir(), 'skillhub-suite-root-'))
    const { plan, downloads } = makePlan()
    const keys: Array<string | null> = []
    let planAttempts = 0
    const fetchImpl = (async (input: URL | RequestInfo, init?: RequestInit) => {
      const url = new URL(String(input))
      if (url.pathname === '/.well-known/clawhub.json') {
        return Response.json({ capabilities: ['skill-suite-v1'] })
      }
      if (url.pathname.endsWith('/install-plan')) {
        keys.push(new Headers(init?.headers).get('Idempotency-Key'))
        planAttempts += 1
        if (planAttempts === 1) return Response.json({ msg: 'temporary outage' }, { status: 503 })
        return Response.json({ code: 0, data: plan })
      }
      const bytes = downloads[url.pathname]
      return bytes
        ? new Response(bytes.slice().buffer as ArrayBuffer)
        : Response.json({ code: 404 }, { status: 404 })
    }) as unknown as typeof fetch

    await installSuite({
      registry,
      namespace: 'global',
      slug: 'starter-pack',
      targets: [{ agent: 'codex', rootDir, scope: 'project', source: 'explicit' }],
      force: false,
      home,
      client: new SkillHubClient(registry, undefined, fetchImpl)
    })

    expect(keys).toHaveLength(2)
    expect(keys[0]).toMatch(/^[0-9a-f-]{36}$/)
    expect(keys[1]).toBe(keys[0])
  })

  test('reports an installed exact version as stale when a member becomes unavailable', async () => {
    const home = await mkdtemp(join(tmpdir(), 'skillhub-suite-home-'))
    const rootDir = await mkdtemp(join(tmpdir(), 'skillhub-suite-root-'))
    const { plan, downloads } = makePlan()
    await installSuite({
      registry,
      namespace: 'global',
      slug: 'starter-pack',
      targets: [{ agent: 'codex', rootDir, scope: 'project', source: 'explicit' }],
      force: false,
      home,
      client: clientFor(plan, downloads)
    })

    const checked = await checkSuite({
      registry,
      namespace: 'global',
      slug: 'starter-pack',
      home,
      client: clientFor(plan, downloads, ['skill-suite-v1'], false)
    })

    expect(checked.current).toBe(false)
    expect(checked.installedVersionAvailable).toBe(false)
    expect(checked.blockingReasons).toContain('member_unavailable')
  })

  test('checks the installed version when the suite has no latest published version', async () => {
    const home = await mkdtemp(join(tmpdir(), 'skillhub-suite-home-'))
    const rootDir = await mkdtemp(join(tmpdir(), 'skillhub-suite-root-'))
    const { plan, downloads } = makePlan()
    await installSuite({
      registry,
      namespace: 'global',
      slug: 'starter-pack',
      targets: [{ agent: 'codex', rootDir, scope: 'project', source: 'explicit' }],
      force: false,
      home,
      client: clientFor(plan, downloads)
    })
    const fetchImpl = (async (input: URL | RequestInfo) => {
      const url = new URL(String(input))
      if (url.pathname === '/.well-known/clawhub.json') {
        return Response.json({ capabilities: ['skill-suite-v1'] })
      }
      if (url.pathname === `/api/v1/suites/${plan.namespace}/${plan.slug}`) {
        if (!url.searchParams.has('version')) {
          return Response.json({ code: 404, msg: 'no latest version' }, { status: 404 })
        }
        return Response.json({ code: 0, data: {
          id: 1,
          versionId: 2,
          namespace: plan.namespace,
          slug: plan.slug,
          displayName: plan.slug,
          version: plan.version,
          status: 'YANKED',
          visibility: 'PUBLIC',
          available: false,
          members: plan.members.map(member => ({ ...member, blockingReason: 'VERSION_UNAVAILABLE' }))
        } })
      }
      return Response.json({ code: 404 }, { status: 404 })
    }) as unknown as typeof fetch

    const checked = await checkSuite({
      registry,
      namespace: 'global',
      slug: 'starter-pack',
      home,
      client: new SkillHubClient(registry, undefined, fetchImpl)
    })

    expect(checked.remoteVersion).toBeUndefined()
    expect(checked.current).toBe(false)
    expect(checked.installedVersionAvailable).toBe(false)
    expect(checked.blockingReasons).toContain('VERSION_UNAVAILABLE')
  })

  test('rejects an old Server before resolving or writing Suite members', async () => {
    const home = await mkdtemp(join(tmpdir(), 'skillhub-suite-home-'))
    const rootDir = await mkdtemp(join(tmpdir(), 'skillhub-suite-root-'))
    const { plan, downloads } = makePlan()

    await expect(installSuite({
      registry,
      namespace: 'global',
      slug: 'starter-pack',
      targets: [{ agent: 'codex', rootDir, scope: 'project', source: 'explicit' }],
      force: false,
      home,
      client: clientFor(plan, downloads, [])
    })).rejects.toThrow('registry does not support Skill Suites')

    expect(await exists(join(rootDir, 'alpha'))).toBe(false)
    expect(await exists(join(home, '.skillhub', 'inventory.json'))).toBe(false)
  })

  test('treats a non-JSON metadata response as an unsupported old Server', async () => {
    const home = await mkdtemp(join(tmpdir(), 'skillhub-suite-home-'))
    const rootDir = await mkdtemp(join(tmpdir(), 'skillhub-suite-root-'))
    const fetchImpl = (async () => new Response('<html>legacy registry</html>', {
      status: 200,
      headers: { 'Content-Type': 'text/html' }
    })) as unknown as typeof fetch

    await expect(installSuite({
      registry,
      namespace: 'global',
      slug: 'starter-pack',
      targets: [{ agent: 'codex', rootDir, scope: 'project', source: 'explicit' }],
      force: false,
      home,
      client: new SkillHubClient(registry, undefined, fetchImpl)
    })).rejects.toThrow('registry does not support Skill Suites')

    expect(await exists(join(rootDir, 'starter-pack'))).toBe(false)
    expect(await exists(join(home, '.skillhub', 'inventory.json'))).toBe(false)
  })

  test('keeps every target untouched when the Server denies a member plan', async () => {
    const home = await mkdtemp(join(tmpdir(), 'skillhub-suite-home-'))
    const rootDir = await mkdtemp(join(tmpdir(), 'skillhub-suite-root-'))

    await expect(installSuite({
      registry,
      namespace: 'private-team',
      slug: 'restricted-pack',
      targets: [{ agent: 'codex', rootDir, scope: 'project', source: 'explicit' }],
      force: false,
      home,
      client: clientWithPlanFailure(403, 'access denied')
    })).rejects.toThrow('access denied')

    expect(await readdir(rootDir)).toEqual([])
    expect(await exists(join(home, '.skillhub', 'inventory.json'))).toBe(false)
  })

  test('blocks an upgrade when the exact remote snapshot has an unavailable member', async () => {
    const home = await mkdtemp(join(tmpdir(), 'skillhub-suite-home-'))
    const rootDir = await mkdtemp(join(tmpdir(), 'skillhub-suite-root-'))
    const { plan, downloads } = makePlan()
    await installSuite({
      registry,
      namespace: 'global',
      slug: 'starter-pack',
      targets: [{ agent: 'codex', rootDir, scope: 'project', source: 'explicit' }],
      force: false,
      home,
      client: clientFor(plan, downloads)
    })

    await expect(planSuiteUpgrade({
      registry,
      namespace: 'global',
      slug: 'starter-pack',
      home,
      client: clientFor(plan, downloads, ['skill-suite-v1'], false)
    })).rejects.toThrow('is unavailable')

    expect(await readFile(join(rootDir, 'alpha', 'SKILL.md'), 'utf8')).toBe('# Alpha')
  })

  test('rolls back all live members when a filesystem commit fails midway', async () => {
    const home = await mkdtemp(join(tmpdir(), 'skillhub-suite-home-'))
    const rootDir = await mkdtemp(join(tmpdir(), 'skillhub-suite-root-'))
    const { plan, downloads } = makePlan()
    const renameOperation: typeof rename = async (source, target): Promise<void> => {
      if (String(source).includes('.skillhub-suite-stage-') && String(target) === join(rootDir, 'beta')) {
        throw new Error('injected rename failure')
      }
      await rename(source, target)
    }

    await expect(installSuite({
      registry,
      namespace: 'global',
      slug: 'starter-pack',
      targets: [{ agent: 'codex', rootDir, scope: 'project', source: 'explicit' }],
      force: false,
      home,
      client: clientFor(plan, downloads),
      renameOperation
    })).rejects.toThrow('injected rename failure')

    expect(await exists(join(rootDir, 'alpha'))).toBe(false)
    expect(await exists(join(rootDir, 'beta'))).toBe(false)
    expect(await exists(join(home, '.skillhub', 'inventory.json'))).toBe(false)
  })

  test('rolls back the first Agent target when the second target commit fails', async () => {
    const home = await mkdtemp(join(tmpdir(), 'skillhub-suite-home-'))
    const codexRoot = await mkdtemp(join(tmpdir(), 'skillhub-suite-codex-'))
    const claudeRoot = await mkdtemp(join(tmpdir(), 'skillhub-suite-claude-'))
    const { plan, downloads } = makePlan()
    const renameOperation: typeof rename = async (source, target): Promise<void> => {
      if (String(source).includes('.skillhub-suite-stage-')
        && String(target) === join(claudeRoot, 'alpha')) {
        throw new Error('injected second target failure')
      }
      await rename(source, target)
    }

    await expect(installSuite({
      registry,
      namespace: 'global',
      slug: 'starter-pack',
      targets: [
        { agent: 'codex', rootDir: codexRoot, scope: 'project', source: 'explicit' },
        { agent: 'claude', rootDir: claudeRoot, scope: 'project', source: 'explicit' }
      ],
      force: false,
      home,
      client: clientFor(plan, downloads),
      renameOperation
    })).rejects.toThrow('injected second target failure')

    expect(await readdir(codexRoot)).toEqual([])
    expect(await readdir(claudeRoot)).toEqual([])
    expect(await exists(join(home, '.skillhub', 'inventory.json'))).toBe(false)
  })

  test('reports retained backup paths when rollback cannot restore a replaced member', async () => {
    const home = await mkdtemp(join(tmpdir(), 'skillhub-suite-home-'))
    const rootDir = await mkdtemp(join(tmpdir(), 'skillhub-suite-root-'))
    const { plan, downloads } = makePlan()
    const old = archive('# Old Alpha')
    const alphaDir = join(rootDir, 'alpha')
    await mkdir(alphaDir, { recursive: true })
    await writeFile(join(alphaDir, 'SKILL.md'), '# Old Alpha')
    await mkdir(join(home, '.skillhub'), { recursive: true })
    await writeFile(join(home, '.skillhub', 'inventory.json'), JSON.stringify({
      items: [{
        registry,
        namespace: 'global',
        slug: 'alpha',
        version: '0.9.0',
        fingerprint: old.fingerprint,
        installedBy: ['direct'],
        targets: [{ agent: 'codex', rootDir, installDir: alphaDir, installedAt: '2026-09-01T00:00:00Z' }]
      }],
      suites: []
    }))
    const renameOperation: typeof rename = async (source, target): Promise<void> => {
      const from = String(source)
      const to = String(target)
      if (from.includes('.skillhub-suite-stage-') && to === join(rootDir, 'beta')) {
        throw new Error('injected commit failure')
      }
      if (from.includes('.skillhub-suite-backup-') && to === alphaDir) {
        throw new Error('injected restore failure')
      }
      await rename(source, target)
    }

    await expect(installSuite({
      registry,
      namespace: 'global',
      slug: 'starter-pack',
      targets: [{ agent: 'codex', rootDir, scope: 'project', source: 'explicit' }],
      force: true,
      home,
      client: clientFor(plan, downloads),
      renameOperation
    })).rejects.toThrow('rollback was incomplete')

    const retainedBackup = (await readdir(rootDir)).find(name => name.startsWith('alpha.skillhub-suite-backup-'))
    expect(retainedBackup).toBeDefined()
    expect(await readFile(join(rootDir, retainedBackup!, 'SKILL.md'), 'utf8')).toBe('# Old Alpha')
    const inventory = JSON.parse(await readFile(join(home, '.skillhub', 'inventory.json'), 'utf8'))
    expect(inventory.items[0]).toMatchObject({ slug: 'alpha', version: '0.9.0', installedBy: ['direct'] })
  })

  test('installs one exact snapshot across multiple Agent targets', async () => {
    const home = await mkdtemp(join(tmpdir(), 'skillhub-suite-home-'))
    const codexRoot = await mkdtemp(join(tmpdir(), 'skillhub-suite-codex-'))
    const claudeRoot = await mkdtemp(join(tmpdir(), 'skillhub-suite-claude-'))
    const { plan, downloads } = makePlan()

    const result = await installSuite({
      registry,
      namespace: 'global',
      slug: 'starter-pack',
      targets: [
        { agent: 'codex', rootDir: codexRoot, scope: 'project', source: 'explicit' },
        { agent: 'claude', rootDir: claudeRoot, scope: 'project', source: 'explicit' }
      ],
      force: false,
      home,
      client: clientFor(plan, downloads)
    })

    expect(result.installed).toHaveLength(4)
    expect(await readFile(join(codexRoot, 'alpha', 'SKILL.md'), 'utf8')).toBe('# Alpha')
    expect(await readFile(join(claudeRoot, 'beta', 'SKILL.md'), 'utf8')).toBe('# Beta')
    const inventory = JSON.parse(await readFile(join(home, '.skillhub', 'inventory.json'), 'utf8'))
    expect(inventory.suites[0].members[0].installDirs).toHaveLength(2)
  })

  test('rejects member coordinates that collide in the local directory layout', async () => {
    const home = await mkdtemp(join(tmpdir(), 'skillhub-suite-home-'))
    const rootDir = await mkdtemp(join(tmpdir(), 'skillhub-suite-root-'))
    const { plan, downloads } = makePlan()
    plan.members[1] = { ...plan.members[1]!, namespace: 'team', slug: 'alpha' }

    await expect(installSuite({
      registry,
      namespace: 'global',
      slug: 'starter-pack',
      targets: [{ agent: 'codex', rootDir, scope: 'project', source: 'explicit' }],
      force: false,
      home,
      client: clientFor(plan, downloads)
    })).rejects.toThrow('use the same local directory')

    expect(await exists(join(rootDir, 'alpha'))).toBe(false)
  })

  test('does not create a Suite directory when a Skill and Suite share one coordinate', async () => {
    const home = await mkdtemp(join(tmpdir(), 'skillhub-suite-home-'))
    const rootDir = await mkdtemp(join(tmpdir(), 'skillhub-suite-root-'))
    const { plan, downloads } = makePlan()
    plan.slug = 'marketing'
    plan.members = [plan.members[1]!]
    const directSkillDir = join(rootDir, 'marketing')
    await mkdir(directSkillDir, { recursive: true })
    await writeFile(join(directSkillDir, 'SKILL.md'), '# Direct marketing Skill')

    await installSuite({
      registry,
      namespace: 'global',
      slug: 'marketing',
      targets: [{ agent: 'codex', rootDir, scope: 'project', source: 'explicit' }],
      force: false,
      home,
      client: clientFor(plan, downloads)
    })

    expect(await readFile(join(directSkillDir, 'SKILL.md'), 'utf8')).toBe('# Direct marketing Skill')
    expect(await readFile(join(rootDir, 'beta', 'SKILL.md'), 'utf8')).toBe('# Beta')
  })

  test('preserves a member shared by two installed Suites', async () => {
    const home = await mkdtemp(join(tmpdir(), 'skillhub-suite-home-'))
    const rootDir = await mkdtemp(join(tmpdir(), 'skillhub-suite-root-'))
    const first = makePlan()
    first.plan.members = [first.plan.members[0]!]
    await installSuite({
      registry,
      namespace: 'global',
      slug: first.plan.slug,
      targets: [{ agent: 'codex', rootDir, scope: 'project', source: 'explicit' }],
      force: false,
      home,
      client: clientFor(first.plan, first.downloads)
    })
    const secondPlan = { ...first.plan, slug: 'editor-pack', fingerprint: 'sha256:editor-pack' }
    await installSuite({
      registry,
      namespace: 'global',
      slug: secondPlan.slug,
      targets: [{ agent: 'codex', rootDir, scope: 'project', source: 'explicit' }],
      force: false,
      home,
      client: clientFor(secondPlan, first.downloads)
    })

    const removed = await removeSuite({
      registry,
      namespace: 'global',
      slug: first.plan.slug,
      home
    })

    expect(removed.preserved).toEqual([{ dir: join(rootDir, 'alpha'), reason: 'shared' }])
    expect(await readFile(join(rootDir, 'alpha', 'SKILL.md'), 'utf8')).toBe('# Alpha')
    const inventory = JSON.parse(await readFile(join(home, '.skillhub', 'inventory.json'), 'utf8'))
    expect(inventory.items[0].installedBy).toEqual(['suite:@global/editor-pack@1.0.0'])
  })

  test('removing a Suite does not rewrite the same coordinate from another registry', async () => {
    const home = await mkdtemp(join(tmpdir(), 'skillhub-suite-home-'))
    const firstRoot = await mkdtemp(join(tmpdir(), 'skillhub-suite-registry-a-'))
    const secondRoot = await mkdtemp(join(tmpdir(), 'skillhub-suite-registry-b-'))
    const secondRegistry = 'http://registry-b.test'
    const { plan, downloads } = makePlan()
    plan.members = [plan.members[0]!]

    await installSuite({
      registry,
      namespace: 'global',
      slug: 'starter-pack',
      targets: [{ agent: 'codex', rootDir: firstRoot, scope: 'project', source: 'explicit' }],
      force: false,
      home,
      client: clientFor(plan, downloads)
    })
    await installSuite({
      registry: secondRegistry,
      namespace: 'global',
      slug: 'starter-pack',
      targets: [{ agent: 'claude', rootDir: secondRoot, scope: 'project', source: 'explicit' }],
      force: false,
      home,
      client: clientFor(plan, downloads)
    })

    await removeSuite({ registry, namespace: 'global', slug: 'starter-pack', home })

    const afterFirstRemoval = await new InventoryStore(home).read()
    expect(afterFirstRemoval.suites).toEqual([
      expect.objectContaining({ registry: secondRegistry, slug: 'starter-pack' })
    ])
    expect(afterFirstRemoval.items).toEqual([
      expect.objectContaining({
        registry: secondRegistry,
        slug: 'alpha',
        installedBy: ['suite:@global/starter-pack@1.0.0']
      })
    ])
    expect(await readFile(join(secondRoot, 'alpha', 'SKILL.md'), 'utf8')).toBe('# Alpha')

    const secondRemoval = await removeSuite({
      registry: secondRegistry,
      namespace: 'global',
      slug: 'starter-pack',
      home
    })
    expect(secondRemoval.removed).toEqual([join(secondRoot, 'alpha')])
    expect(await exists(join(secondRoot, 'alpha'))).toBe(false)
  })

  test('reuses a matching legacy direct install and preserves it when Suite is removed', async () => {
    const home = await mkdtemp(join(tmpdir(), 'skillhub-suite-home-'))
    const rootDir = await mkdtemp(join(tmpdir(), 'skillhub-suite-root-'))
    const { plan, downloads } = makePlan()
    plan.members = [plan.members[0]!]
    const skillDir = join(rootDir, 'alpha')
    await mkdir(skillDir, { recursive: true })
    await writeFile(join(skillDir, 'SKILL.md'), '# Alpha')
    await mkdir(join(home, '.skillhub'), { recursive: true })
    await writeFile(join(home, '.skillhub', 'inventory.json'), JSON.stringify({
      items: [{
        registry,
        namespace: 'global',
        slug: 'alpha',
        version: '1.0.0',
        fingerprint: plan.members[0]!.fingerprint,
        targets: [{ agent: 'codex', rootDir, installDir: skillDir, installedAt: '2026-09-01T00:00:00Z' }]
      }]
    }))

    const installed = await installSuite({
      registry,
      namespace: 'global',
      slug: 'starter-pack',
      targets: [{ agent: 'codex', rootDir, scope: 'project', source: 'explicit' }],
      force: false,
      home,
      client: clientFor(plan, downloads)
    })
    expect(installed.reused).toHaveLength(1)

    const removed = await removeSuite({ registry, namespace: 'global', slug: 'starter-pack', home })
    expect(removed.preserved).toEqual([{ dir: skillDir, reason: 'shared' }])
    expect(await exists(skillDir)).toBe(true)
    const inventory = JSON.parse(await readFile(join(home, '.skillhub', 'inventory.json'), 'utf8'))
    expect(inventory.suites).toEqual([])
    expect(inventory.items[0].installedBy).toEqual(['direct'])
  })

  test('requires force before replacing a locally modified same-version member', async () => {
    const home = await mkdtemp(join(tmpdir(), 'skillhub-suite-home-'))
    const rootDir = await mkdtemp(join(tmpdir(), 'skillhub-suite-root-'))
    const { plan, downloads } = makePlan()
    plan.members = [plan.members[0]!]
    const member = plan.members[0]!
    const skillDir = join(rootDir, member.slug)
    await mkdir(skillDir, { recursive: true })
    await writeFile(join(skillDir, 'SKILL.md'), '# Alpha')
    const store = new InventoryStore(home)
    await store.upsertTarget(registry, member.namespace, member.slug, member.version, {
      agent: 'codex', rootDir, installDir: skillDir, installedAt: new Date().toISOString()
    }, member.fingerprint)
    await writeFile(join(skillDir, 'SKILL.md'), '# Locally modified Alpha')

    await expect(installSuite({
      registry,
      namespace: 'global',
      slug: 'starter-pack',
      targets: [{ agent: 'codex', rootDir, scope: 'project', source: 'explicit' }],
      force: false,
      home,
      client: clientFor(plan, downloads)
    })).rejects.toThrow('local changes')

    expect(await readFile(join(skillDir, 'SKILL.md'), 'utf8')).toBe('# Locally modified Alpha')
    expect((await store.read()).suites ?? []).toEqual([])
  })

  test('tracks direct and Suite ownership independently for each Agent target', async () => {
    const home = await mkdtemp(join(tmpdir(), 'skillhub-suite-home-'))
    const directRoot = await mkdtemp(join(tmpdir(), 'skillhub-suite-direct-root-'))
    const suiteOnlyRoot = await mkdtemp(join(tmpdir(), 'skillhub-suite-only-root-'))
    const { plan, downloads } = makePlan()
    const alpha = plan.members[0]!
    await mkdir(join(directRoot, alpha.slug), { recursive: true })
    await writeFile(join(directRoot, alpha.slug, 'SKILL.md'), '# Alpha')
    const store = new InventoryStore(home)
    await store.upsertTarget(registry, alpha.namespace, alpha.slug, alpha.version, {
      agent: 'codex', rootDir: directRoot, installDir: join(directRoot, alpha.slug),
      installedAt: new Date().toISOString()
    }, alpha.fingerprint)

    await installSuite({
      registry,
      namespace: 'global',
      slug: 'starter-pack',
      targets: [
        { agent: 'codex', rootDir: directRoot, scope: 'project', source: 'explicit' },
        { agent: 'claude', rootDir: suiteOnlyRoot, scope: 'project', source: 'explicit' }
      ],
      force: false,
      home,
      client: clientFor(plan, downloads)
    })
    await removeSuite({ registry, namespace: 'global', slug: 'starter-pack', home })

    expect(await access(join(directRoot, 'alpha')).then(() => true)).toBe(true)
    await expect(access(join(suiteOnlyRoot, 'alpha'))).rejects.toThrow()
    const inventory = await store.read()
    const alphaItem = inventory.items.find(item => item.slug === 'alpha')!
    expect(alphaItem.targets).toHaveLength(1)
    expect(alphaItem.targets[0]!.installedBy).toEqual(['direct'])
  })

  test('removes unmodified members that are owned only by the Suite', async () => {
    const home = await mkdtemp(join(tmpdir(), 'skillhub-suite-home-'))
    const rootDir = await mkdtemp(join(tmpdir(), 'skillhub-suite-root-'))
    const { plan, downloads } = makePlan()
    await installSuite({
      registry,
      namespace: 'global',
      slug: 'starter-pack',
      targets: [{ agent: 'codex', rootDir, scope: 'project', source: 'explicit' }],
      force: false,
      home,
      client: clientFor(plan, downloads)
    })

    const removed = await removeSuite({ registry, namespace: 'global', slug: 'starter-pack', home })

    expect(removed.removed.sort()).toEqual([join(rootDir, 'alpha'), join(rootDir, 'beta')].sort())
    expect(await exists(join(rootDir, 'alpha'))).toBe(false)
    expect(await exists(join(rootDir, 'beta'))).toBe(false)
    const inventory = JSON.parse(await readFile(join(home, '.skillhub', 'inventory.json'), 'utf8'))
    expect(inventory).toMatchObject({ items: [], suites: [] })
  })

  test('upgrades the exact snapshot and retires members removed by the new Suite version', async () => {
    const home = await mkdtemp(join(tmpdir(), 'skillhub-suite-home-'))
    const rootDir = await mkdtemp(join(tmpdir(), 'skillhub-suite-root-'))
    const first = makePlan()
    await installSuite({
      registry,
      namespace: 'global',
      slug: 'starter-pack',
      targets: [{ agent: 'codex', rootDir, scope: 'project', source: 'explicit' }],
      force: false,
      home,
      client: clientFor(first.plan, first.downloads)
    })

    const alphaV2 = archive('# Alpha v2')
    const gamma = archive('# Gamma')
    const second: SuiteInstallPlan = {
      ...first.plan,
      operationId: 'operation-2',
      version: '2.0.0',
      fingerprint: 'sha256:suite-v2',
      members: [
        { ...first.plan.members[0]!, skillVersionId: 12, version: '2.0.0', fingerprint: alphaV2.fingerprint },
        {
          skillId: 30,
          skillVersionId: 31,
          namespace: 'global',
          slug: 'gamma',
          version: '1.0.0',
          fingerprint: gamma.fingerprint,
          downloadUrl: '/downloads/gamma',
          position: 1,
          entry: false
        }
      ]
    }
    const client = clientFor(second, {
      '/downloads/alpha': alphaV2.bytes,
      '/downloads/gamma': gamma.bytes
    })

    const upgraded = await upgradeSuite({ registry, namespace: 'global', slug: 'starter-pack', home, client })

    expect(upgraded.upgrade.changes.map(change => change.action).sort()).toEqual(['add', 'change', 'remove'])
    expect(await readFile(join(rootDir, 'alpha', 'SKILL.md'), 'utf8')).toBe('# Alpha v2')
    expect(await exists(join(rootDir, 'beta'))).toBe(false)
    expect(await readFile(join(rootDir, 'gamma', 'SKILL.md'), 'utf8')).toBe('# Gamma')
    const inventory = JSON.parse(await readFile(join(home, '.skillhub', 'inventory.json'), 'utf8'))
    expect(inventory.suites[0]).toMatchObject({ version: '2.0.0', fingerprint: 'sha256:suite-v2' })
    expect(inventory.items.map((item: { slug: string }) => item.slug).sort()).toEqual(['alpha', 'gamma'])
  })

  test('does not overwrite a locally modified member during Suite upgrade by default', async () => {
    const home = await mkdtemp(join(tmpdir(), 'skillhub-suite-home-'))
    const rootDir = await mkdtemp(join(tmpdir(), 'skillhub-suite-root-'))
    const first = makePlan()
    await installSuite({
      registry,
      namespace: 'global',
      slug: 'starter-pack',
      targets: [{ agent: 'codex', rootDir, scope: 'project', source: 'explicit' }],
      force: false,
      home,
      client: clientFor(first.plan, first.downloads)
    })
    await writeFile(join(rootDir, 'alpha', 'SKILL.md'), '# Locally modified Alpha')

    const alphaV2 = archive('# Alpha v2')
    const second: SuiteInstallPlan = {
      ...first.plan,
      operationId: 'operation-2',
      version: '2.0.0',
      fingerprint: 'sha256:suite-v2',
      members: [
        { ...first.plan.members[0]!, skillVersionId: 12, version: '2.0.0', fingerprint: alphaV2.fingerprint },
        first.plan.members[1]!
      ]
    }

    await expect(upgradeSuite({
      registry,
      namespace: 'global',
      slug: 'starter-pack',
      home,
      client: clientFor(second, { ...first.downloads, '/downloads/alpha': alphaV2.bytes })
    })).rejects.toThrow('local changes')

    expect(await readFile(join(rootDir, 'alpha', 'SKILL.md'), 'utf8')).toBe('# Locally modified Alpha')
    const unchangedInventory = JSON.parse(await readFile(join(home, '.skillhub', 'inventory.json'), 'utf8'))
    expect(unchangedInventory.suites[0]).toMatchObject({ version: '1.0.0', fingerprint: 'sha256:suite' })

    await upgradeSuite({
      registry,
      namespace: 'global',
      slug: 'starter-pack',
      force: true,
      home,
      client: clientFor(second, { ...first.downloads, '/downloads/alpha': alphaV2.bytes })
    })

    expect(await readFile(join(rootDir, 'alpha', 'SKILL.md'), 'utf8')).toBe('# Alpha v2')
    const upgradedInventory = JSON.parse(await readFile(join(home, '.skillhub', 'inventory.json'), 'utf8'))
    expect(upgradedInventory.suites[0]).toMatchObject({ version: '2.0.0', fingerprint: 'sha256:suite-v2' })
  })

  test('does not reinstall a Suite removed after upgrade planning', async () => {
    const home = await mkdtemp(join(tmpdir(), 'skillhub-suite-home-'))
    const rootDir = await mkdtemp(join(tmpdir(), 'skillhub-suite-root-'))
    const first = makePlan()
    await installSuite({
      registry,
      namespace: 'global',
      slug: 'starter-pack',
      targets: [{ agent: 'codex', rootDir, scope: 'project', source: 'explicit' }],
      force: false,
      home,
      client: clientFor(first.plan, first.downloads)
    })

    const nextPlan = { ...first.plan, operationId: 'operation-2', version: '2.0.0' }
    const client = clientFor(nextPlan, first.downloads)
    const fetchDetail = client.suiteDetail.bind(client)
    let signalPlanRead: (() => void) | undefined
    let releasePlan: (() => void) | undefined
    const planRead = new Promise<void>((resolvePromise) => { signalPlanRead = resolvePromise })
    const holdPlan = new Promise<void>((resolvePromise) => { releasePlan = resolvePromise })
    client.suiteDetail = async (...args) => {
      signalPlanRead?.()
      await holdPlan
      return fetchDetail(...args)
    }

    const upgrading = upgradeSuite({ registry, namespace: 'global', slug: 'starter-pack', home, client })
    await planRead
    await removeSuite({ registry, namespace: 'global', slug: 'starter-pack', home })
    releasePlan?.()

    await expect(upgrading).rejects.toThrow('installed Suite changed while waiting for target locks')
    expect(await readdir(rootDir)).toEqual([])
    const inventory = JSON.parse(await readFile(join(home, '.skillhub', 'inventory.json'), 'utf8'))
    expect(inventory).toMatchObject({ items: [], suites: [] })
  })

  test('rejects stale upgrade targets after the same Suite is reinstalled elsewhere', async () => {
    const home = await mkdtemp(join(tmpdir(), 'skillhub-suite-home-'))
    const originalRoot = await mkdtemp(join(tmpdir(), 'skillhub-suite-original-'))
    const replacementRoot = await mkdtemp(join(tmpdir(), 'skillhub-suite-replacement-'))
    const first = makePlan()
    await installSuite({
      registry,
      namespace: 'global',
      slug: 'starter-pack',
      targets: [{ agent: 'codex', rootDir: originalRoot, scope: 'project', source: 'explicit' }],
      force: false,
      home,
      client: clientFor(first.plan, first.downloads)
    })

    const nextPlan = { ...first.plan, operationId: 'operation-2', version: '2.0.0' }
    const upgradeClient = clientFor(nextPlan, first.downloads)
    const fetchDetail = upgradeClient.suiteDetail.bind(upgradeClient)
    let signalPlanRead: (() => void) | undefined
    let releasePlan: (() => void) | undefined
    const planRead = new Promise<void>((resolvePromise) => { signalPlanRead = resolvePromise })
    const holdPlan = new Promise<void>((resolvePromise) => { releasePlan = resolvePromise })
    upgradeClient.suiteDetail = async (...args) => {
      signalPlanRead?.()
      await holdPlan
      return fetchDetail(...args)
    }

    const upgrading = upgradeSuite({
      registry, namespace: 'global', slug: 'starter-pack', home, client: upgradeClient
    })
    await planRead
    try {
      await removeSuite({ registry, namespace: 'global', slug: 'starter-pack', home })
      await installSuite({
        registry,
        namespace: 'global',
        slug: 'starter-pack',
        targets: [{ agent: 'claude', rootDir: replacementRoot, scope: 'project', source: 'explicit' }],
        force: false,
        home,
        client: clientFor(first.plan, first.downloads)
      })
    } finally {
      releasePlan?.()
    }

    await expect(upgrading).rejects.toThrow('installed Suite changed while waiting for target locks')
    expect(await readdir(originalRoot)).toEqual([])
    expect(await readFile(join(replacementRoot, 'alpha', 'SKILL.md'), 'utf8')).toBe('# Alpha')
    const inventory = JSON.parse(await readFile(join(home, '.skillhub', 'inventory.json'), 'utf8'))
    expect(inventory.suites[0]).toMatchObject({ version: '1.0.0', fingerprint: 'sha256:suite' })
    expect(inventory.suites[0].members.every((member: { installDirs: string[] }) =>
      member.installDirs.every(dir => dir.startsWith(replacementRoot)))).toBe(true)
  })

  test('preserves a member modified after removal starts but before locked validation', async () => {
    const home = await mkdtemp(join(tmpdir(), 'skillhub-suite-home-'))
    const rootDir = await mkdtemp(join(tmpdir(), 'skillhub-suite-root-'))
    const { plan, downloads } = makePlan()
    await installSuite({
      registry,
      namespace: 'global',
      slug: 'starter-pack',
      targets: [{ agent: 'codex', rootDir, scope: 'project', source: 'explicit' }],
      force: false,
      home,
      client: clientFor(plan, downloads)
    })

    const removed = await removeSuite({
      registry,
      namespace: 'global',
      slug: 'starter-pack',
      home,
      afterTargetLocksAcquired: async () => {
        await writeFile(join(rootDir, 'beta', 'SKILL.md'), '# Locally modified Beta')
      }
    })

    expect(removed.removed).toEqual([join(rootDir, 'alpha')])
    expect(removed.preserved).toEqual([{ dir: join(rootDir, 'beta'), reason: 'modified' }])
    expect(await readFile(join(rootDir, 'beta', 'SKILL.md'), 'utf8')).toBe('# Locally modified Beta')
    const inventory = await new InventoryStore(home).read()
    expect(inventory.items).toEqual([expect.objectContaining({
      slug: 'beta',
      installedBy: ['direct']
    })])
  })

  test('preserves a retired member that gains direct ownership before locked upgrade validation', async () => {
    const home = await mkdtemp(join(tmpdir(), 'skillhub-suite-home-'))
    const rootDir = await mkdtemp(join(tmpdir(), 'skillhub-suite-root-'))
    const first = makePlan()
    await installSuite({
      registry,
      namespace: 'global',
      slug: 'starter-pack',
      targets: [{ agent: 'codex', rootDir, scope: 'project', source: 'explicit' }],
      force: false,
      home,
      client: clientFor(first.plan, first.downloads)
    })

    const second: SuiteInstallPlan = {
      ...first.plan,
      operationId: 'operation-2',
      version: '2.0.0',
      fingerprint: 'sha256:suite-v2',
      members: [first.plan.members[0]!]
    }
    const beta = first.plan.members[1]!
    const betaDir = join(rootDir, beta.slug)
    await installSuite({
      registry,
      namespace: 'global',
      slug: 'starter-pack',
      version: '2.0.0',
      targets: [{ agent: 'codex', rootDir, scope: 'project', source: 'explicit' }],
      force: true,
      home,
      client: clientFor(second, first.downloads),
      afterTargetLocksAcquired: async () => {
        await new InventoryStore(home).upsertTarget(
          registry, beta.namespace, beta.slug, beta.version,
          { agent: 'codex', rootDir, installDir: betaDir, installedAt: new Date().toISOString() },
          beta.fingerprint)
      }
    })

    expect(await readFile(join(betaDir, 'SKILL.md'), 'utf8')).toBe('# Beta')
    const inventory = await new InventoryStore(home).read()
    expect(inventory.items.find(item => item.slug === 'beta')).toMatchObject({ installedBy: ['direct'] })
  })
})
