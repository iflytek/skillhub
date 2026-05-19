import { afterEach, describe, expect, test } from 'bun:test'
import { startFakeRegistry } from '../helpers/fake-registry'
import { runCli } from '../helpers/run-cli'

let registry: Awaited<ReturnType<typeof startFakeRegistry>> | undefined

afterEach(() => {
  registry?.stop()
  registry = undefined
})

describe('search command', () => {
  test('prints compact search table', async () => {
    registry = await startFakeRegistry({
      searchItems: [{ namespace: 'global', slug: 'pdf-parser', latestVersion: '1.2.0', summary: 'Parse PDFs' }]
    })

    const result = await runCli(['search', 'pdf', '--registry', registry.url])

    expect(result.exitCode).toBe(0)
    expect(result.stdout).toContain('global/pdf-parser')
    expect(result.stdout).toContain('1.2.0')
  })

  test('search json output', async () => {
    registry = await startFakeRegistry({
      searchItems: [{ namespace: 'global', slug: 'pdf-parser', latestVersion: '1.2.0', summary: 'Parse PDFs' }]
    })

    const result = await runCli(['search', 'pdf', '--registry', registry.url, '--json'])

    expect(result.exitCode).toBe(0)
    const json = JSON.parse(result.stdout)
    expect(json.ok).toBe(true)
    expect(json.items).toHaveLength(1)
    expect(json.items[0].slug).toBe('pdf-parser')
  })

  test('search without query returns full result set', async () => {
    registry = await startFakeRegistry({
      searchItems: [
        { namespace: 'global', slug: 'pdf-parser', latestVersion: '1.2.0', summary: 'Parse PDFs' },
        { namespace: 'global', slug: 'doc-parser', latestVersion: '2.0.0', summary: 'Parse docs' }
      ]
    })

    const result = await runCli(['search', '--registry', registry.url])

    expect(result.exitCode).toBe(0)
    expect(result.stdout).toContain('global/pdf-parser')
    expect(result.stdout).toContain('global/doc-parser')
  })

  // P1: empty result set
  test('prints "No skills found." when registry returns empty list', async () => {
    registry = await startFakeRegistry({ searchItems: [] })

    const result = await runCli(['search', 'nonexistent', '--registry', registry.url])

    expect(result.exitCode).toBe(0)
    expect(result.stdout).toBe('No skills found.')
  })

  // P1: --limit forwarding — unit-level assertion that limit=5 appears in URL
  test('--limit 5 sends limit=5 in the search URL', async () => {
    // Capture the raw request URL inside the fake server by extending it
    // minimally: we start a real Bun server that records the last search URL.
    let capturedUrl = ''
    const server = Bun.serve({
      port: 0,
      fetch(req) {
        const url = new URL(req.url)
        if (url.pathname === '/api/cli/v1/skills/search') {
          capturedUrl = req.url
          return Response.json({
            code: 0,
            data: { items: [], total: 0, limit: 5 }
          })
        }
        return Response.json({ code: 404, message: 'not found' }, { status: 404 })
      }
    })
    const registryUrl = `http://localhost:${server.port}`

    try {
      const result = await runCli(['search', 'pdf', '--limit', '5', '--registry', registryUrl])
      expect(result.exitCode).toBe(0)
      expect(capturedUrl).toContain('limit=5')
    } finally {
      server.stop()
    }
  })

  // P1: network failure → EXIT.network. The exit code is the contract; the
  // exact message can be "registry unreachable" (when fetch throws) or
  // "registry returned 5xx" (when Bun returns a 5xx Response on connection
  // refusal). Both indicate the same network-class failure.
  test('exits with network error code when registry is unreachable', async () => {
    registry = await startFakeRegistry({ failures: { search: 'network' } })

    const result = await runCli(['search', 'pdf', '--registry', registry.url])

    expect(result.exitCode).toBe(3) // EXIT.network
    expect(result.stderr).toMatch(/registry unreachable|registry returned 5\d\d/)
  })

  // -------------------------------------------------------------------------
  // P2: query containing non-ASCII characters must be URL-encoded in the
  // outgoing request. We capture the raw URL via a custom Bun.serve and
  // assert the q parameter is the percent-encoded UTF-8 form of "中文测试".
  // -------------------------------------------------------------------------
  test('non-ASCII query is URL-encoded as UTF-8 percent escapes', async () => {
    let capturedUrl = ''
    const server = Bun.serve({
      port: 0,
      fetch(req) {
        const url = new URL(req.url)
        if (url.pathname === '/api/cli/v1/skills/search') {
          capturedUrl = req.url
          return Response.json({ code: 0, data: { items: [], total: 0, limit: 20 } })
        }
        return Response.json({ code: 404, message: 'not found' }, { status: 404 })
      }
    })
    const registryUrl = `http://localhost:${server.port}`
    try {
      const result = await runCli(['search', '中文测试', '--registry', registryUrl])
      expect(result.exitCode).toBe(0)
      // UTF-8 of 中文测试 = E4 B8 AD E6 96 87 E6 B5 8B E8 AF 95
      expect(capturedUrl).toContain('q=%E4%B8%AD%E6%96%87%E6%B5%8B%E8%AF%95')
    } finally {
      server.stop()
    }
  })

  // -------------------------------------------------------------------------
  // P2: queries containing special characters (script tags, ampersands,
  // equals signs) are percent-encoded so they don't break the query string.
  // -------------------------------------------------------------------------
  test('special-character query is encoded so the URL stays parseable', async () => {
    let capturedUrl = ''
    const server = Bun.serve({
      port: 0,
      fetch(req) {
        const url = new URL(req.url)
        if (url.pathname === '/api/cli/v1/skills/search') {
          capturedUrl = req.url
          return Response.json({ code: 0, data: { items: [], total: 0, limit: 20 } })
        }
        return Response.json({ code: 404, message: 'not found' }, { status: 404 })
      }
    })
    const registryUrl = `http://localhost:${server.port}`
    try {
      const result = await runCli(['search', '<script>&q=evil', '--registry', registryUrl])
      expect(result.exitCode).toBe(0)
      // Re-parse the captured URL and read q via URLSearchParams to confirm
      // the original payload survives a round-trip without splitting.
      const captured = new URL(capturedUrl)
      expect(captured.searchParams.get('q')).toBe('<script>&q=evil')
    } finally {
      server.stop()
    }
  })

  // -------------------------------------------------------------------------
  // P2: --limit 0 still forwards limit=0 to the registry. The CLI does not
  // validate boundary values; the server contract decides how to respond.
  // We assert the CLI forwards faithfully and exits cleanly when the server
  // returns an empty list.
  // -------------------------------------------------------------------------
  test('--limit 0 forwards limit=0 and renders no skills', async () => {
    let capturedUrl = ''
    const server = Bun.serve({
      port: 0,
      fetch(req) {
        const url = new URL(req.url)
        if (url.pathname === '/api/cli/v1/skills/search') {
          capturedUrl = req.url
          return Response.json({ code: 0, data: { items: [], total: 0, limit: 0 } })
        }
        return Response.json({ code: 404, message: 'not found' }, { status: 404 })
      }
    })
    const registryUrl = `http://localhost:${server.port}`
    try {
      const result = await runCli(['search', 'pdf', '--limit', '0', '--registry', registryUrl])
      expect(result.exitCode).toBe(0)
      expect(capturedUrl).toContain('limit=0')
      expect(result.stdout).toBe('No skills found.')
    } finally {
      server.stop()
    }
  })

  // -------------------------------------------------------------------------
  // P1: 5xx server response. Per commit a14d89d8 (refactor: unify
  // download/handleJsonResponse error mapping) non-2xx responses surface
  // as EXIT.generic (1), distinct from EXIT.network (3) which is reserved
  // for "couldn't even reach the registry". stderr still carries the HTTP
  // status so the user can debug.
  // -------------------------------------------------------------------------
  test('5xx server error returns EXIT.generic with status in stderr', async () => {
    registry = await startFakeRegistry({ failures: { search: 'server_error' } })

    const result = await runCli(['search', 'pdf', '--registry', registry.url])

    expect(result.exitCode).toBe(1) // EXIT.generic
    expect(result.stderr).toMatch(/registry returned 500/)
  })

  // -------------------------------------------------------------------------
  // P2: extra --limit values, including high integers and negative inputs.
  // CLI does not validate; server contract decides. We only assert the
  // outgoing URL faithfully reflects the user's input.
  // -------------------------------------------------------------------------
  test('--limit 100 forwards limit=100 in the search URL', async () => {
    let capturedUrl = ''
    const server = Bun.serve({
      port: 0,
      fetch(req) {
        const url = new URL(req.url)
        if (url.pathname === '/api/cli/v1/skills/search') {
          capturedUrl = req.url
          return Response.json({ code: 0, data: { items: [], total: 0, limit: 100 } })
        }
        return Response.json({ code: 404 }, { status: 404 })
      }
    })
    try {
      const result = await runCli(['search', 'pdf', '--limit', '100', '--registry', `http://localhost:${server.port}`])
      expect(result.exitCode).toBe(0)
      expect(capturedUrl).toContain('limit=100')
    } finally {
      server.stop()
    }
  })

  test('query with + and = characters round-trips faithfully through URL encoding', async () => {
    let capturedUrl = ''
    const server = Bun.serve({
      port: 0,
      fetch(req) {
        const url = new URL(req.url)
        if (url.pathname === '/api/cli/v1/skills/search') {
          capturedUrl = req.url
          return Response.json({ code: 0, data: { items: [], total: 0, limit: 20 } })
        }
        return Response.json({ code: 404 }, { status: 404 })
      }
    })
    try {
      const tricky = 'a+b=c&d e'
      const result = await runCli(['search', tricky, '--registry', `http://localhost:${server.port}`])
      expect(result.exitCode).toBe(0)
      const captured = new URL(capturedUrl)
      expect(captured.searchParams.get('q')).toBe(tricky)
    } finally {
      server.stop()
    }
  })

  test('1KB long query is forwarded without truncation', async () => {
    let capturedUrl = ''
    const server = Bun.serve({
      port: 0,
      fetch(req) {
        const url = new URL(req.url)
        if (url.pathname === '/api/cli/v1/skills/search') {
          capturedUrl = req.url
          return Response.json({ code: 0, data: { items: [], total: 0, limit: 20 } })
        }
        return Response.json({ code: 404 }, { status: 404 })
      }
    })
    try {
      const longQuery = 'q'.repeat(1024)
      const result = await runCli(['search', longQuery, '--registry', `http://localhost:${server.port}`])
      expect(result.exitCode).toBe(0)
      const captured = new URL(capturedUrl)
      expect(captured.searchParams.get('q')).toBe(longQuery)
    } finally {
      server.stop()
    }
  })

  test('literal % in query is encoded so it survives a round-trip without being mistaken for an escape', async () => {
    let capturedUrl = ''
    const server = Bun.serve({
      port: 0,
      fetch(req) {
        const url = new URL(req.url)
        if (url.pathname === '/api/cli/v1/skills/search') {
          capturedUrl = req.url
          return Response.json({ code: 0, data: { items: [], total: 0, limit: 20 } })
        }
        return Response.json({ code: 404 }, { status: 404 })
      }
    })
    try {
      // A literal '%' must be escaped as %25 so the server doesn't read it
      // as the start of an existing escape sequence.
      const tricky = '50% off'
      const result = await runCli(['search', tricky, '--registry', `http://localhost:${server.port}`])
      expect(result.exitCode).toBe(0)
      expect(capturedUrl).toContain('%25')
      const captured = new URL(capturedUrl)
      expect(captured.searchParams.get('q')).toBe(tricky)
    } finally {
      server.stop()
    }
  })

  test('query with multiple shell metacharacters survives both shell quoting and URL encoding', async () => {
    let capturedUrl = ''
    const server = Bun.serve({
      port: 0,
      fetch(req) {
        const url = new URL(req.url)
        if (url.pathname === '/api/cli/v1/skills/search') {
          capturedUrl = req.url
          return Response.json({ code: 0, data: { items: [], total: 0, limit: 20 } })
        }
        return Response.json({ code: 404 }, { status: 404 })
      }
    })
    try {
      const tricky = "$VAR `cmd` 'quote' \"dq\""
      const result = await runCli(['search', tricky, '--registry', `http://localhost:${server.port}`])
      expect(result.exitCode).toBe(0)
      const captured = new URL(capturedUrl)
      expect(captured.searchParams.get('q')).toBe(tricky)
    } finally {
      server.stop()
    }
  })
})
