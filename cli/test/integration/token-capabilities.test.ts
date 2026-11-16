import { afterEach, describe, expect, test } from 'bun:test'
import { readFile } from 'node:fs/promises'
import { runCli } from '../helpers/run-cli'
import { createTempHome } from '../helpers/temp-env'

let server: ReturnType<typeof Bun.serve> | undefined

afterEach(() => {
  server?.stop()
  server = undefined
})

describe('token capability CLI compatibility', () => {
  test('whoami exposes capabilities in human and JSON output', async () => {
    const env = await createTempHome()
    const identity = {
      handle: 'candidate', displayName: 'Candidate', capabilities: ['skill:read', 'skill:submit'],
      legacyCompat: false, expiresAt: '2027-01-01T00:00:00Z'
    }
    server = Bun.serve({ port: 0, hostname: '127.0.0.1', fetch: () => Response.json({ data: identity }) })
    const args = ['whoami', '--registry', `http://127.0.0.1:${server.port}`, '--token', 'test-token']
    const human = await runCli(args, { HOME: env.home, USERPROFILE: env.home })
    expect(human.exitCode).toBe(0)
    expect(human.stdout).toContain('Capabilities: skill:read, skill:submit')
    const json = await runCli([...args, '--json'], { HOME: env.home, USERPROFILE: env.home })
    expect(JSON.parse(json.stdout)).toMatchObject(identity)
  })

  test('localized missing-delete error provides explicit authorization without token reissuance', async () => {
    const env = await createTempHome()
    const paths: string[] = []
    server = Bun.serve({
      port: 0, hostname: '127.0.0.1',
      fetch(req) {
        paths.push(`${req.method} ${new URL(req.url).pathname}`)
        if (req.method === 'DELETE') {
          return Response.json({
            code: 403, msg: '缺少删除能力', errorCode: 'error.apiToken.scope.missing',
            errorArgs: ['skill:delete'], requestId: 'capability-test'
          }, { status: 403 })
        }
        return Response.json({ data: { handle: 'candidate', displayName: 'Candidate' } })
      }
    })
    const registry = `http://127.0.0.1:${server.port}`
    const runtime = { HOME: env.home, USERPROFILE: env.home }
    await runCli(['login', '--registry', registry, '--token', 'test-token'], runtime)
    const before = await readFile(`${env.home}/.skillhub/credentials.json`, 'utf8')
    for (const output of [[], ['--json']]) {
      const denied = await runCli(['remove', 'sample', '--remote', '--hard', '--registry', registry, ...output], runtime)
      expect(denied.exitCode).toBe(2)
      expect(denied.stdout).toBe('')
      expect(denied.stderr).toContain('skill:delete')
      expect(denied.stderr).toContain('skillhub login --token')
      if (output.length) expect(JSON.parse(denied.stderr).details.errorCode).toBe('error.apiToken.scope.missing')
    }
    expect(await readFile(`${env.home}/.skillhub/credentials.json`, 'utf8')).toBe(before)
    expect(paths.filter(path => path.startsWith('POST'))).toEqual([])
    expect(paths.filter(path => path.startsWith('DELETE'))).toHaveLength(2)
  })

  test('unrelated denials do not claim missing delete capability', async () => {
    const env = await createTempHome()
    server = Bun.serve({ port: 0, hostname: '127.0.0.1', fetch: () => Response.json({
      msg: 'not the owner', errorCode: 'other.reason', errorArgs: ['skill:delete']
    }, { status: 403 }) })
    const result = await runCli([
      'remove', 'sample', '--remote', '--hard', '--registry', `http://127.0.0.1:${server.port}`, '--token', 'test-token'
    ], { HOME: env.home, USERPROFILE: env.home })
    expect(result.stderr).not.toContain('Explicit authorization')
  })
})
