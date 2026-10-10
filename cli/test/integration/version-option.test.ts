import { mkdir } from 'node:fs/promises'
import { join } from 'node:path'
import { afterEach, describe, expect, test } from 'bun:test'
import { strToU8, zipSync } from 'fflate'
import { createTempHome } from '../helpers/temp-env'
import { startFakeRegistry } from '../helpers/fake-registry'
import { runCli } from '../helpers/run-cli'

const TIMESTAMP_VERSION = '20260910.021100'

let stopServer: (() => void) | undefined

afterEach(() => {
  stopServer?.()
  stopServer = undefined
})

describe('--version parsing', () => {
  test('install preserves trailing zeros in a numeric-looking version', async () => {
    const env = await createTempHome()
    const registry = await startFakeRegistry({
      token: 'sk_ok',
      skills: [{
        namespace: 'global',
        slug: 'timestamped',
        version: TIMESTAMP_VERSION,
        zipBytes: zipSync({ 'SKILL.md': strToU8('# timestamped') })
      }]
    })
    stopServer = registry.stop
    const installDir = join(env.cwd, 'skills')
    await mkdir(installDir, { recursive: true })

    const result = await runCli([
      'install', '@global/timestamped',
      '--version', TIMESTAMP_VERSION,
      '--dir', installDir,
      '--registry', registry.url,
      '--token', 'sk_ok'
    ], { HOME: env.home, USERPROFILE: env.home })

    expect(result.exitCode).toBe(0)
    expect(registry.received.resolve?.version).toBe(TIMESTAMP_VERSION)
  })

  test('install preserves trailing zeros with the --version=value form', async () => {
    const env = await createTempHome()
    const registry = await startFakeRegistry({
      token: 'sk_ok',
      skills: [{
        namespace: 'global',
        slug: 'timestamped-equals',
        version: TIMESTAMP_VERSION,
        zipBytes: zipSync({ 'SKILL.md': strToU8('# timestamped equals') })
      }]
    })
    stopServer = registry.stop
    const installDir = join(env.cwd, 'skills-equals')
    await mkdir(installDir, { recursive: true })

    const result = await runCli([
      'install', '@global/timestamped-equals',
      `--version=${TIMESTAMP_VERSION}`,
      '--dir', installDir,
      '--registry', registry.url,
      '--token', 'sk_ok'
    ], { HOME: env.home, USERPROFILE: env.home })

    expect(result.exitCode).toBe(0)
    expect(registry.received.resolve?.version).toBe(TIMESTAMP_VERSION)
  })

  test('install preserves an ordinary text version', async () => {
    const env = await createTempHome()
    const registry = await startFakeRegistry({
      token: 'sk_ok',
      skills: [{
        namespace: 'global',
        slug: 'text-version',
        version: 'release-a',
        zipBytes: zipSync({ 'SKILL.md': strToU8('# text version') })
      }]
    })
    stopServer = registry.stop
    const installDir = join(env.cwd, 'skills-text-version')
    await mkdir(installDir, { recursive: true })

    const result = await runCli([
      'install', '@global/text-version',
      '--version', 'release-a',
      '--dir', installDir,
      '--registry', registry.url,
      '--token', 'sk_ok'
    ], { HOME: env.home, USERPROFILE: env.home })

    expect(result.exitCode).toBe(0)
    expect(registry.received.resolve?.version).toBe('release-a')
  })

  test('ignores --version after the option terminator', async () => {
    const env = await createTempHome()
    const registry = await startFakeRegistry({
      token: 'sk_ok',
      skills: [{
        namespace: 'global',
        slug: 'latest',
        version: '1.0.0',
        zipBytes: zipSync({ 'SKILL.md': strToU8('# latest') })
      }]
    })
    stopServer = registry.stop
    const installDir = join(env.cwd, 'skills-latest')
    await mkdir(installDir, { recursive: true })

    const result = await runCli([
      'install', '@global/latest',
      '--dir', installDir,
      '--registry', registry.url,
      '--token', 'sk_ok',
      '--', '--version', TIMESTAMP_VERSION
    ], { HOME: env.home, USERPROFILE: env.home })

    expect(result.exitCode).toBe(0)
    expect(registry.received.resolve?.version).toBeNull()
  })

  test.each(['separate', 'equals'])('suite install preserves trailing zeros with %s syntax', async syntax => {
    const env = await createTempHome()
    const received = { version: null as string | null }
    const server = Bun.serve({
      port: 0,
      fetch(request) {
        const url = new URL(request.url)
        if (url.pathname === '/.well-known/clawhub.json') {
          return Response.json({ apiBase: '/api/v1', capabilities: ['skill-suite-v1'] })
        }
        if (url.pathname === '/api/v1/suites/global/starter-pack/install-plan') {
          received.version = url.searchParams.get('version')
          return Response.json({ code: 404, message: 'stop after capturing version' }, { status: 404 })
        }
        return Response.json({ code: 404, message: 'not found' }, { status: 404 })
      }
    })
    stopServer = () => server.stop(true)
    const installDir = join(env.cwd, 'suite-skills')
    await mkdir(installDir, { recursive: true })

    const result = await runCli([
      'suite', 'install', '@global/starter-pack',
      ...(syntax === 'equals' ? [`--version=${TIMESTAMP_VERSION}`] : ['--version', TIMESTAMP_VERSION]),
      '--dir', installDir,
      '--registry', `http://localhost:${server.port}`,
      '--token', 'sk_ok'
    ], { HOME: env.home, USERPROFILE: env.home })

    expect(result.exitCode).not.toBe(0)
    expect(received.version).toBe(TIMESTAMP_VERSION)
  })

  test.each([
    ['a missing value', ['--version']],
    ['a missing repeated value', ['--version', '1.0.0', '--version']],
    ['repeated values', ['--version', '1.0.0', '--version', '2.0.0']],
    ['an empty equals value', ['--version=']]
  ])('rejects %s before contacting the registry', async (_description, versionArgs) => {
    const env = await createTempHome()
    const registry = await startFakeRegistry({
      token: 'sk_ok',
      skills: [{
        namespace: 'global',
        slug: 'rejected',
        version: '1.0.0',
        zipBytes: zipSync({ 'SKILL.md': strToU8('# rejected') })
      }]
    })
    stopServer = registry.stop

    const result = await runCli([
      'install', '@global/rejected',
      ...versionArgs,
      '--json',
      '--registry', registry.url,
      '--token', 'sk_ok'
    ], { HOME: env.home, USERPROFILE: env.home })

    expect(result.exitCode).toBe(5)
    expect(registry.received.resolves).toBe(0)
  })
})
