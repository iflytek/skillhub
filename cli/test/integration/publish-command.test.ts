import { mkdtemp, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { afterEach, describe, expect, test } from 'bun:test'
import { zipSync } from 'fflate'
import { createTempHome } from '../helpers/temp-env'
import { startFakeRegistry } from '../helpers/fake-registry'
import { runCli } from '../helpers/run-cli'

let registry: Awaited<ReturnType<typeof startFakeRegistry>> | undefined

afterEach(() => {
  registry?.stop()
  registry = undefined
})

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

async function login(env: { home: string }, registryUrl: string) {
  const result = await runCli(['login', '--registry', registryUrl, '--token', 'sk_ok'], {
    HOME: env.home,
    USERPROFILE: env.home
  })
  if (result.exitCode !== 0) {
    throw new Error(`login failed: ${result.stderr}`)
  }
}

async function makeTempDir(...files: Array<[string, string]>) {
  const dir = await mkdtemp(join(tmpdir(), 'skillhub-publish-dir-'))
  for (const [name, content] of files) {
    await writeFile(join(dir, name), content)
  }
  return dir
}

async function makeTempZip(name: string) {
  const dir = await mkdtemp(join(tmpdir(), 'skillhub-publish-zip-'))
  const zipPath = join(dir, name)
  const bytes = zipSync({ 'SKILL.md': new TextEncoder().encode('# Demo') })
  await writeFile(zipPath, bytes)
  return zipPath
}

async function makeTempTxt(name: string) {
  const dir = await mkdtemp(join(tmpdir(), 'skillhub-publish-txt-'))
  const txtPath = join(dir, name)
  await writeFile(txtPath, 'not a zip')
  return txtPath
}

// ---------------------------------------------------------------------------
// P0 — must-have
// ---------------------------------------------------------------------------

describe('publish command — P0', () => {
  test('unauthenticated publish is rejected with EXIT.auth', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({ token: 'sk_ok' })

    const dir = await makeTempDir(['SKILL.md', '# Demo'])
    const result = await runCli(['publish', dir, '--registry', registry.url], {
      HOME: env.home,
      USERPROFILE: env.home
    })

    expect(result.exitCode).toBe(2)
    expect(result.stderr).toContain('authentication')
  })

  test('path not found returns filesystem error', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({ token: 'sk_ok' })
    await login(env, registry.url)

    const result = await runCli(['publish', '/does/not/exist', '--registry', registry.url], {
      HOME: env.home,
      USERPROFILE: env.home
    })

    expect(result.exitCode).not.toBe(0)
    expect(result.stderr).toContain('not found')
  })

  test('non-zip file is rejected', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({ token: 'sk_ok' })
    await login(env, registry.url)

    const txtPath = await makeTempTxt('skill.txt')
    const result = await runCli(['publish', txtPath, '--registry', registry.url], {
      HOME: env.home,
      USERPROFILE: env.home
    })

    expect(result.exitCode).not.toBe(0)
    expect(result.stderr).toContain('zip')
  })

  test('directory happy path: exit 0, default namespace=global, fileName ends in .zip, visibility=PUBLIC', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({ token: 'sk_ok' })
    await login(env, registry.url)

    const dir = await makeTempDir(['SKILL.md', '# Demo'], ['index.js', 'console.log("hi")'])
    const result = await runCli(['publish', dir, '--registry', registry.url], {
      HOME: env.home,
      USERPROFILE: env.home
    })

    expect(result.exitCode).toBe(0)
    expect(registry.received.publish).not.toBeNull()
    expect(registry.received.publish!.namespace).toBe('global')
    expect(registry.received.publish!.fileName).toMatch(/\.zip$/)
    expect(registry.received.publish!.visibility).toBe('PUBLIC')
  })

  test('zip file happy path: exit 0, fileName matches passed file', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({ token: 'sk_ok' })
    await login(env, registry.url)

    const zipPath = await makeTempZip('my-skill.zip')
    const result = await runCli(['publish', zipPath, '--registry', registry.url], {
      HOME: env.home,
      USERPROFILE: env.home
    })

    expect(result.exitCode).toBe(0)
    expect(registry.received.publish).not.toBeNull()
    expect(registry.received.publish!.fileName).toBe('my-skill.zip')
  })

  describe('visibility mapping', () => {
    test('--visibility public → PUBLIC', async () => {
      const env = await createTempHome()
      registry = await startFakeRegistry({ token: 'sk_ok' })
      await login(env, registry.url)

      const dir = await makeTempDir(['SKILL.md', '# Demo'])
      await runCli(['publish', dir, '--registry', registry.url, '--visibility', 'public'], {
        HOME: env.home,
        USERPROFILE: env.home
      })

      expect(registry.received.publish!.visibility).toBe('PUBLIC')
    })

    test('--visibility namespace-only → NAMESPACE_ONLY', async () => {
      const env = await createTempHome()
      registry = await startFakeRegistry({ token: 'sk_ok' })
      await login(env, registry.url)

      const dir = await makeTempDir(['SKILL.md', '# Demo'])
      await runCli(['publish', dir, '--registry', registry.url, '--visibility', 'namespace-only'], {
        HOME: env.home,
        USERPROFILE: env.home
      })

      expect(registry.received.publish!.visibility).toBe('NAMESPACE_ONLY')
    })

    test('--visibility private → PRIVATE', async () => {
      const env = await createTempHome()
      registry = await startFakeRegistry({ token: 'sk_ok' })
      await login(env, registry.url)

      const dir = await makeTempDir(['SKILL.md', '# Demo'])
      await runCli(['publish', dir, '--registry', registry.url, '--visibility', 'private'], {
        HOME: env.home,
        USERPROFILE: env.home
      })

      expect(registry.received.publish!.visibility).toBe('PRIVATE')
    })
  })
})

// ---------------------------------------------------------------------------
// P1 — should-have
// ---------------------------------------------------------------------------

describe('publish command — P1', () => {
  test('--namespace override is forwarded to server', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({ token: 'sk_ok' })
    await login(env, registry.url)

    const dir = await makeTempDir(['SKILL.md', '# Demo'])
    const result = await runCli(['publish', dir, '--registry', registry.url, '--namespace', 'myteam'], {
      HOME: env.home,
      USERPROFILE: env.home
    })

    expect(result.exitCode).toBe(0)
    expect(registry.received.publish!.namespace).toBe('myteam')
  })

  test('--json output has correct shape including detailUrl', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({ token: 'sk_ok' })
    await login(env, registry.url)

    const dir = await makeTempDir(['SKILL.md', '# Demo'])
    const result = await runCli(['publish', dir, '--registry', registry.url, '--json'], {
      HOME: env.home,
      USERPROFILE: env.home
    })

    expect(result.exitCode).toBe(0)
    const json = JSON.parse(result.stdout)
    expect(json.ok).toBe(true)
    expect(json.namespace).toBe('global')
    expect(typeof json.slug).toBe('string')
    expect(typeof json.version).toBe('string')
    expect(typeof json.visibility).toBe('string')
    expect(json.detailUrl).toContain(registry.url)
    expect(json.detailUrl).toContain('global')
    expect(json.detailUrl).toContain(encodeURIComponent(json.slug))
  })

  test('server error during publish returns EXIT.generic', async () => {
    const env = await createTempHome()
    // 'server_error' returns HTTP 500; request reached registry but failed, so EXIT.generic.
    registry = await startFakeRegistry({ token: 'sk_ok', failures: { publish: 'server_error' } })
    await login(env, registry.url)

    const dir = await makeTempDir(['SKILL.md', '# Demo'])
    const result = await runCli(['publish', dir, '--registry', registry.url], {
      HOME: env.home,
      USERPROFILE: env.home
    })

    expect(result.exitCode).toBe(1)
    expect(result.stderr).toContain('registry')
  })
})

// ---------------------------------------------------------------------------
// P1 — content shape: directory layout and edge files
// ---------------------------------------------------------------------------

import { mkdir } from 'node:fs/promises'
import { unzipSync, strFromU8 } from 'fflate'

describe('publish command — content shape', () => {
  /**
   * Spin up a publish endpoint that captures the raw zip body and lets us
   * inspect entries server-side. Returns the captured bytes alongside a
   * stop() handle so tests can assert what the CLI actually packaged.
   */
  async function startCapturingPublishServer() {
    let capturedBytes: Uint8Array | null = null
    const server = Bun.serve({
      port: 0,
      async fetch(req) {
        const url = new URL(req.url)
        if (url.pathname === '/api/cli/v1/auth/whoami') {
          return Response.json({ code: 0, data: { handle: 'u', displayName: 'U' } })
        }
        if (url.pathname.endsWith('/publish') && req.method === 'POST') {
          const form = await req.formData()
          const file = form.get('file')
          if (file instanceof File) {
            capturedBytes = new Uint8Array(await file.arrayBuffer())
          }
          return Response.json({
            code: 0,
            data: { namespace: 'global', slug: 'captured', version: '1.0.0', visibility: 'PUBLIC' }
          })
        }
        return Response.json({ code: 404, message: 'not found' }, { status: 404 })
      }
    })
    return {
      url: `http://localhost:${server.port}`,
      stop: () => server.stop(),
      getCaptured: () => capturedBytes
    }
  }

  test('publishing a directory with subdirs packages every file at its relative path', async () => {
    const env = await createTempHome()
    const server = await startCapturingPublishServer()
    try {
      await login(env, server.url)

      const dir = await mkdtemp(join(tmpdir(), 'skillhub-publish-nested-'))
      await writeFile(join(dir, 'SKILL.md'), '# nested')
      await mkdir(join(dir, 'references'), { recursive: true })
      await writeFile(join(dir, 'references', 'a.md'), 'aa')
      await mkdir(join(dir, 'scripts'), { recursive: true })
      await writeFile(join(dir, 'scripts', 'run.sh'), '#!/bin/sh\necho ok\n')

      const result = await runCli(['publish', dir, '--registry', server.url], {
        HOME: env.home, USERPROFILE: env.home
      })
      expect(result.exitCode).toBe(0)

      const captured = server.getCaptured()
      expect(captured).not.toBeNull()
      const rawEntries = unzipSync(captured!)
      // Normalize all entry keys to use forward slashes for cross-platform compatibility
      const entries = Object.fromEntries(
        Object.entries(rawEntries).map(([key, value]) => [key.replace(/\\/g, '/'), value])
      )
      // Filter out directory marker entries (zip records empty entries for
      // dirs with a trailing slash); we only care about file entries.
      const files = Object.keys(entries).filter(k => !k.endsWith('/')).sort()
      expect(files).toEqual([
        'SKILL.md',
        'references/a.md',
        'scripts/run.sh'
      ])
      expect(strFromU8(entries['SKILL.md']!)).toBe('# nested')
      expect(strFromU8(entries['references/a.md']!)).toBe('aa')
    } finally {
      server.stop()
    }
  })

  test('publishing a directory with hidden dotfiles packages them as-is', async () => {
    const env = await createTempHome()
    const server = await startCapturingPublishServer()
    try {
      await login(env, server.url)

      const dir = await mkdtemp(join(tmpdir(), 'skillhub-publish-hidden-'))
      await writeFile(join(dir, 'SKILL.md'), '# h')
      await writeFile(join(dir, '.DS_Store'), 'macos junk')
      await writeFile(join(dir, '.editorconfig'), 'root = true\n')

      const result = await runCli(['publish', dir, '--registry', server.url], {
        HOME: env.home, USERPROFILE: env.home
      })
      expect(result.exitCode).toBe(0)

      const captured = server.getCaptured()
      expect(captured).not.toBeNull()
      const rawEntries = unzipSync(captured!)
      // Normalize all entry keys to use forward slashes for cross-platform compatibility
      const entries = Object.fromEntries(
        Object.entries(rawEntries).map(([key, value]) => [key.replace(/\\/g, '/'), value])
      )
      // Pin current behavior so future filtering changes are intentional.
      expect(Object.keys(entries).sort()).toEqual(['.DS_Store', '.editorconfig', 'SKILL.md'])
    } finally {
      server.stop()
    }
  })

  test('publishing an empty directory still issues a request and reports the server outcome', async () => {
    const env = await createTempHome()
    // Fake registry accepts publish unconditionally; CLI is not authoritative
    // on SKILL.md presence (server is). We assert only that the CLI does not
    // crash client-side and exits with whatever the server returned.
    registry = await startFakeRegistry({ token: 'sk_ok' })
    await login(env, registry.url)

    const dir = await mkdtemp(join(tmpdir(), 'skillhub-publish-empty-'))

    const result = await runCli(['publish', dir, '--registry', registry.url], {
      HOME: env.home, USERPROFILE: env.home
    })
    // Today's contract: empty dir → empty zip uploaded → server returns 200.
    // If the server adds client-side or server-side validation later this
    // assertion will need to flip; that's intentional and traceable.
    expect(result.exitCode).toBe(0)
  })

  test('server 422 with a JSON validation body surfaces a non-zero exit and stderr', async () => {
    const env = await createTempHome()
    const server = Bun.serve({
      port: 0,
      async fetch(req) {
        const url = new URL(req.url)
        if (url.pathname === '/api/cli/v1/auth/whoami') {
          return Response.json({ code: 0, data: { handle: 'u', displayName: 'U' } })
        }
        if (url.pathname.endsWith('/publish') && req.method === 'POST') {
          return Response.json(
            { code: 422, message: 'validation.token.name.size', errors: ['name exceeds 64 chars'] },
            { status: 422 }
          )
        }
        return Response.json({ code: 404, message: 'not found' }, { status: 404 })
      }
    })
    try {
      const url = `http://localhost:${server.port}`
      await login(env, url)
      const dir = await makeTempDir(['SKILL.md', '# x'])
      const result = await runCli(['publish', dir, '--registry', url], {
        HOME: env.home, USERPROFILE: env.home
      })
      expect(result.exitCode).not.toBe(0)
      // The server's HTTP status should propagate visibly so a CI log
      // shows what happened.
      expect(result.stderr).toMatch(/422|registry|validation/i)
    } finally {
      server.stop()
    }
  })

  // 502/503 are special-cased to EXIT.network because they indicate
  // infrastructure-level unavailability (gateway/proxy failure).
  test('server 503 Service Unavailable maps to EXIT.network with status in stderr', async () => {
    const env = await createTempHome()
    const server = Bun.serve({
      port: 0,
      async fetch(req) {
        const url = new URL(req.url)
        if (url.pathname === '/api/cli/v1/auth/whoami') {
          return Response.json({ code: 0, data: { handle: 'u', displayName: 'U' } })
        }
        if (url.pathname.endsWith('/publish') && req.method === 'POST') {
          return Response.json({ code: 503, message: 'service unavailable' }, { status: 503 })
        }
        return Response.json({ code: 404, message: 'not found' }, { status: 404 })
      }
    })
    try {
      const url = `http://localhost:${server.port}`
      await login(env, url)
      const dir = await makeTempDir(['SKILL.md', '# x'])
      const result = await runCli(['publish', dir, '--registry', url], {
        HOME: env.home, USERPROFILE: env.home
      })
      expect(result.exitCode).toBe(3) // EXIT.network
      expect(result.stderr).toMatch(/503|registry/i)
    } finally {
      server.stop()
    }
  })

  test('server 401 mid-session (token revoked) maps to EXIT.auth', async () => {
    const env = await createTempHome()
    const server = Bun.serve({
      port: 0,
      async fetch(req) {
        const url = new URL(req.url)
        // Whoami succeeds (login step). Publish then returns 401 as if the
        // server revoked the token between the login + publish calls.
        if (url.pathname === '/api/cli/v1/auth/whoami') {
          return Response.json({ code: 0, data: { handle: 'u', displayName: 'U' } })
        }
        if (url.pathname.endsWith('/publish') && req.method === 'POST') {
          return Response.json({ code: 401, message: 'unauthorized' }, { status: 401 })
        }
        return Response.json({ code: 404, message: 'not found' }, { status: 404 })
      }
    })
    try {
      const url = `http://localhost:${server.port}`
      await login(env, url)
      const dir = await makeTempDir(['SKILL.md', '# x'])
      const result = await runCli(['publish', dir, '--registry', url], {
        HOME: env.home, USERPROFILE: env.home
      })
      expect(result.exitCode).toBe(2) // EXIT.auth
      expect(result.stderr.toLowerCase()).toMatch(/auth|401|unauthorized/)
    } finally {
      server.stop()
    }
  })

  test('publish response missing required fields is handled without crash', async () => {
    const env = await createTempHome()
    const server = Bun.serve({
      port: 0,
      async fetch(req) {
        const url = new URL(req.url)
        if (url.pathname === '/api/cli/v1/auth/whoami') {
          return Response.json({ code: 0, data: { handle: 'u', displayName: 'U' } })
        }
        if (url.pathname.endsWith('/publish') && req.method === 'POST') {
          // 200 OK but body shape doesn't match the expected schema.
          return Response.json({ code: 0, data: { unexpected: true } })
        }
        return Response.json({ code: 404, message: 'not found' }, { status: 404 })
      }
    })
    try {
      const url = `http://localhost:${server.port}`
      await login(env, url)
      const dir = await makeTempDir(['SKILL.md', '# x'])
      const result = await runCli(['publish', dir, '--registry', url, '--json'], {
        HOME: env.home, USERPROFILE: env.home
      })
      // Either parses with placeholder values or fails — the contract we
      // want is "no crash". Pin: exit 0 means current behavior accepts
      // partial responses; flip if/when stricter validation lands.
      expect([0, 1, 2, 3]).toContain(result.exitCode)
      // Either way, output is bounded — no stack trace dump.
      expect((result.stdout + result.stderr).length).toBeLessThan(2000)
    } finally {
      server.stop()
    }
  })

  test('server 413 Payload Too Large maps to a network-class non-zero exit', async () => {
    const env = await createTempHome()
    const server = Bun.serve({
      port: 0,
      async fetch(req) {
        const url = new URL(req.url)
        if (url.pathname === '/api/cli/v1/auth/whoami') {
          return Response.json({ code: 0, data: { handle: 'u', displayName: 'U' } })
        }
        if (url.pathname.endsWith('/publish') && req.method === 'POST') {
          return Response.json({ code: 413, message: 'payload too large' }, { status: 413 })
        }
        return Response.json({ code: 404, message: 'not found' }, { status: 404 })
      }
    })
    try {
      const url = `http://localhost:${server.port}`
      await login(env, url)
      const dir = await makeTempDir(['SKILL.md', '# x'])
      const result = await runCli(['publish', dir, '--registry', url], {
        HOME: env.home, USERPROFILE: env.home
      })
      expect(result.exitCode).not.toBe(0)
      expect(result.stderr).toMatch(/413|registry/i)
    } finally {
      server.stop()
    }
  })
})
