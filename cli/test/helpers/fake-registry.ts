type FakeHandler = (req: Request) => Response | Promise<Response>

export function createFakeRegistry(handlers: Record<string, FakeHandler>) {
  return async function fakeFetch(input: string | URL | Request, init?: RequestInit): Promise<Response> {
    const url = typeof input === 'string' ? input : input instanceof URL ? input.toString() : input.url
    const path = new URL(url).pathname

    for (const [pattern, handler] of Object.entries(handlers)) {
      if (path === pattern || path.startsWith(pattern)) {
        return handler(new Request(url, init))
      }
    }

    return new Response(JSON.stringify({ error: 'not found' }), { status: 404 })
  }
}

// ---------------------------------------------------------------------------
// Failure injection
// ---------------------------------------------------------------------------

/**
 * Controls how a specific endpoint behaves when a failure is injected:
 *   'auth'         => 401 { code: 401, message: 'unauthorized' }
 *   'forbidden'    => 403 { code: 403, message: 'forbidden' }
 *   'not_found'    => 404 { code: 404, message: 'not found' }
 *   'server_error' => 500 { code: 500, message: 'internal error' }
 *   'network'      => handler throws, causing fetch() to reject with a TypeError
 */
export type FailureMode = 'auth' | 'forbidden' | 'not_found' | 'server_error' | 'network'

function failureResponse(mode: FailureMode): Response {
  switch (mode) {
    case 'auth':
      return Response.json({ code: 401, message: 'unauthorized' }, { status: 401 })
    case 'forbidden':
      return Response.json({ code: 403, message: 'forbidden' }, { status: 403 })
    case 'not_found':
      return Response.json({ code: 404, message: 'not found' }, { status: 404 })
    case 'server_error':
      return Response.json({ code: 500, message: 'internal error' }, { status: 500 })
    case 'network':
      // Unreachable at runtime: startFakeRegistry intercepts 'network' failures
      // by returning a URL pointing at a TCP server that closes connections
      // immediately, so fetch() itself rejects. This case satisfies TypeScript.
      return new Response(null, { status: 503 })
  }
}

// ---------------------------------------------------------------------------
// Skill fixture shape
// ---------------------------------------------------------------------------

export interface FakeSkill {
  namespace: string
  slug: string
  /** Treated as the "latest" version string. Defaults to '1.0.0'. */
  version?: string
  /** Numeric version id returned in resolve. Defaults to 1. */
  versionId?: number
  /** SHA-256 fingerprint string. Defaults to 'deadbeef'. */
  fingerprint?: string
  /** Raw bytes served as the ZIP body. Defaults to a minimal valid ZIP. */
  zipBytes?: Uint8Array
}

// Minimal valid ZIP: local file header + end-of-central-directory record with
// zero entries. Enough for any consumer that just checks Content-Type / length.
const MINIMAL_ZIP = new Uint8Array([
  // End of central directory record (22 bytes, zero entries)
  0x50, 0x4b, 0x05, 0x06, // signature
  0x00, 0x00,             // disk number
  0x00, 0x00,             // disk with start of central directory
  0x00, 0x00,             // entries on this disk
  0x00, 0x00,             // total entries
  0x00, 0x00, 0x00, 0x00, // size of central directory
  0x00, 0x00, 0x00, 0x00, // offset of central directory
  0x00, 0x00,             // comment length
])

// ---------------------------------------------------------------------------
// Captured publish state
// ---------------------------------------------------------------------------

/**
 * Shape of the last publish request received by the fake registry.
 * Inspect via `registry.received.publish` in tests.
 */
export interface CapturedPublish {
  namespace: string
  /** Original file name from the multipart form field. */
  fileName: string
  /** Visibility string from the multipart form field. */
  visibility: string
}

export interface CapturedValidate {
  namespace: string
  fileName: string
  visibility: string
}

/** Last resolve GET: useful for verifying --version is forwarded as ?version=. */
export interface CapturedResolve {
  namespace: string
  slug: string
  version: string | null
  token: string | null
}

/** Last DELETE: useful for verifying remote hard-delete actually hit the server. */
export interface CapturedDelete {
  namespace: string
  slug: string
  token: string | null
}

// ---------------------------------------------------------------------------
// Options
// ---------------------------------------------------------------------------

interface FakeRegistryOptions {
  token?: string
  user?: { handle: string; displayName: string; email?: string }
  searchItems?: Array<{ namespace: string; slug: string; latestVersion: string; summary: string }>
  /** Skills available for resolve / download / delete / publish. */
  skills?: FakeSkill[]
  /** Response to return for publish/validate (dry-run) requests. */
  dryRunResponse?: { valid: boolean; errors: string[]; warnings: string[]; resolvedSlug: string | null; resolvedVersion: string | null }
  /**
   * Per-endpoint failure injection. When set for an endpoint, that endpoint
   * ignores all other logic and returns the specified failure (or throws for
   * 'network').
   */
  failures?: {
    whoami?: FailureMode
    search?: FailureMode
    resolve?: FailureMode
    download?: FailureMode
    deleteRemote?: FailureMode
    publish?: FailureMode
    validate?: FailureMode
  }
}

// ---------------------------------------------------------------------------
// startFakeRegistry
// ---------------------------------------------------------------------------

/**
 * Allocate a port that terminates every accepted connection. We keep the
 * listener alive so the OS cannot reassign the port to another process
 * between bind and fetch — otherwise the client might connect to someone
 * else instead of failing. Any fetch reaches open() and gets terminated,
 * which surfaces as a socket error and maps to
 * CliError('registry unreachable', EXIT.network) in the SkillHub client.
 */
async function startNetworkFailureServer(): Promise<{ url: string; stop: () => void }> {
  const listener = Bun.listen<unknown>({
    hostname: "127.0.0.1",
    port: 0,
    socket: {
      open(socket) { socket.terminate() },
      data() {},
      error() {},
      close() {},
    }
  })
  const port = listener.port
  return {
    url: `http://127.0.0.1:${port}`,
    stop: () => { try { listener.stop(true) } catch { /* already stopped */ } }
  }
}

export async function startFakeRegistry(options: FakeRegistryOptions = {}) {
  // Mutable state captured from incoming requests — readable by tests.
  const state: {
    publish: CapturedPublish | null
    resolve: CapturedResolve | null
    delete: CapturedDelete | null
    validate: CapturedValidate | null
  } = { publish: null, resolve: null, delete: null, validate: null }

  // If any endpoint is configured with 'network' failure mode, we need a real
  // TCP-level failure. Start a connection-dropping server and return its URL
  // so that fetch() itself rejects (ECONNRESET/ECONNREFUSED).
  const hasNetworkFailure = options.failures &&
    Object.values(options.failures).some(m => m === 'network')

  if (hasNetworkFailure) {
    const { url, stop } = await startNetworkFailureServer()
    return {
      url,
      stop,
      received: state
    }
  }

  // Helper: check bearer token. Returns a 401 Response if auth fails, null if ok.
  function checkAuth(req: Request): Response | null {
    if (options.token) {
      const auth = req.headers.get('Authorization')
      if (auth !== `Bearer ${options.token}`) {
        return Response.json({ code: 401, message: 'unauthorized' }, { status: 401 })
      }
    }
    return null
  }

  // Helper: look up a skill by namespace + slug.
  function findSkill(namespace: string, slug: string): FakeSkill | undefined {
    return options.skills?.find(s => s.namespace === namespace && s.slug === slug)
  }

  // Helper: build the download URL that resolve returns.
  function buildDownloadUrl(baseUrl: string, namespace: string, slug: string, version: string): string {
    return `${baseUrl}/api/cli/v1/skills/${namespace}/${slug}/versions/${version}/download`
  }

  const server = Bun.serve({
    port: 0,
    fetch(req) {
      const url = new URL(req.url)
      const path = url.pathname
      const baseUrl = `${url.protocol}//${url.host}`

      // ------------------------------------------------------------------ //
      // GET /api/cli/v1/auth/whoami
      // ------------------------------------------------------------------ //
      if (path === '/api/cli/v1/auth/whoami') {
        if (options.failures?.whoami) return failureResponse(options.failures.whoami)
        const authErr = checkAuth(req)
        if (authErr) return authErr
        return Response.json({
          code: 0,
          data: options.user ?? { handle: 'test-user', displayName: 'Test User', email: 'test@example.com' }
        })
      }

      // ------------------------------------------------------------------ //
      // GET /api/cli/v1/skills/search
      // ------------------------------------------------------------------ //
      if (path === '/api/cli/v1/skills/search') {
        if (options.failures?.search) return failureResponse(options.failures.search)
        return Response.json({
          code: 0,
          data: {
            items: options.searchItems ?? [],
            total: options.searchItems?.length ?? 0,
            limit: 20
          }
        })
      }

      // ------------------------------------------------------------------ //
      // Route: /api/cli/v1/skills/:namespace/:slug/...
      // ------------------------------------------------------------------ //

      // Resolve: GET /api/cli/v1/skills/:namespace/:slug/resolve
      const resolveMatch = path.match(/^\/api\/cli\/v1\/skills\/([^/]+)\/([^/]+)\/resolve$/)
      if (resolveMatch && req.method === 'GET') {
        if (options.failures?.resolve) return failureResponse(options.failures.resolve)
        const namespace = resolveMatch[1]!
        const slug = resolveMatch[2]!
        state.resolve = {
          namespace,
          slug,
          version: url.searchParams.get('version'),
          token: req.headers.get('authorization') ?? null
        }
        const skill = findSkill(namespace, slug)
        if (!skill) {
          return Response.json({ code: 404, message: 'not found' }, { status: 404 })
        }
        const version = skill.version ?? '1.0.0'
        return Response.json({
          code: 0,
          data: {
            namespace,
            slug,
            version,
            versionId: skill.versionId ?? 1,
            fingerprint: skill.fingerprint ?? 'deadbeef',
            downloadUrl: buildDownloadUrl(baseUrl, namespace, slug, version)
          }
        })
      }

      // Download (latest): GET /api/cli/v1/skills/:namespace/:slug/download
      const downloadLatestMatch = path.match(/^\/api\/cli\/v1\/skills\/([^/]+)\/([^/]+)\/download$/)
      if (downloadLatestMatch && req.method === 'GET') {
        if (options.failures?.download) return failureResponse(options.failures.download)
        const namespace = downloadLatestMatch[1]!
        const slug = downloadLatestMatch[2]!
        const skill = findSkill(namespace, slug)
        if (!skill) {
          return Response.json({ code: 404, message: 'not found' }, { status: 404 })
        }
        const bytes = skill.zipBytes ?? MINIMAL_ZIP
        return new Response(bytes as BodyInit, {
          status: 200,
          headers: { 'Content-Type': 'application/zip' }
        })
      }

      // Download (versioned): GET /api/cli/v1/skills/:namespace/:slug/versions/:version/download
      const downloadVersionedMatch = path.match(
        /^\/api\/cli\/v1\/skills\/([^/]+)\/([^/]+)\/versions\/([^/]+)\/download$/
      )
      if (downloadVersionedMatch && req.method === 'GET') {
        if (options.failures?.download) return failureResponse(options.failures.download)
        const namespace = downloadVersionedMatch[1]!
        const slug = downloadVersionedMatch[2]!
        const skill = findSkill(namespace, slug)
        if (!skill) {
          return Response.json({ code: 404, message: 'not found' }, { status: 404 })
        }
        const bytes = skill.zipBytes ?? MINIMAL_ZIP
        return new Response(bytes as BodyInit, {
          status: 200,
          headers: { 'Content-Type': 'application/zip' }
        })
      }

      // Delete: DELETE /api/cli/v1/skills/:namespace/:slug
      const deleteMatch = path.match(/^\/api\/cli\/v1\/skills\/([^/]+)\/([^/]+)$/)
      if (deleteMatch && req.method === 'DELETE') {
        if (options.failures?.deleteRemote) return failureResponse(options.failures.deleteRemote)
        const authErr = checkAuth(req)
        if (authErr) return authErr
        const namespace = deleteMatch[1]!
        const slug = deleteMatch[2]!
        state.delete = {
          namespace,
          slug,
          token: req.headers.get('authorization') ?? null
        }
        const skill = findSkill(namespace, slug)
        if (!skill) {
          return Response.json({ code: 404, message: 'not found' }, { status: 404 })
        }
        return Response.json({
          code: 0,
          data: {
            ok: true,
            scope: namespace,
            action: 'deleted',
            namespace,
            slug
          }
        })
      }

      // Validate (dry-run): POST /api/cli/v1/skills/:namespace/publish/validate
      const validateMatch = path.match(/^\/api\/cli\/v1\/skills\/([^/]+)\/publish\/validate$/)
      if (validateMatch && req.method === 'POST') {
        if (options.failures?.validate) return failureResponse(options.failures.validate)
        const authErr = checkAuth(req)
        if (authErr) return authErr
        const namespace = validateMatch[1]!

        return req.formData().then(form => {
          const fileField = form.get('file')
          const visibility = (form.get('visibility') as string | null) ?? 'PUBLIC'
          let fileName = 'skill.zip'
          if (fileField instanceof File) {
            fileName = fileField.name || fileName
          }
          state.validate = { namespace, fileName, visibility }

          const dryRunData = options.dryRunResponse ?? {
            valid: true,
            errors: [],
            warnings: [],
            resolvedSlug: fileName.replace(/\.zip$/, ''),
            resolvedVersion: '1.0.0'
          }
          return Response.json({ code: 0, data: dryRunData })
        })
      }

      // Publish: POST /api/cli/v1/skills/:namespace/publish
      const publishMatch = path.match(/^\/api\/cli\/v1\/skills\/([^/]+)\/publish$/)
      if (publishMatch && req.method === 'POST') {
        if (options.failures?.publish) return failureResponse(options.failures.publish)
        const authErr = checkAuth(req)
        if (authErr) return authErr
        const namespace = publishMatch[1]!

        // Parse multipart form data asynchronously — return a Promise<Response>.
        return req.formData().then(form => {
          const fileField = form.get('file')
          const visibility = (form.get('visibility') as string | null) ?? 'PUBLIC'

          // Capture file name from the File object if available, else fallback.
          let fileName = 'skill.zip'
          if (fileField instanceof File) {
            fileName = fileField.name || fileName
          }

          // Record for test assertions.
          state.publish = { namespace, fileName, visibility }

          return Response.json({
            code: 0,
            data: {
              namespace,
              slug: fileName.replace(/\.zip$/, ''),
              version: '1.0.0',
              visibility
            }
          })
        })
      }

      // ------------------------------------------------------------------ //
      // Fallthrough
      // ------------------------------------------------------------------ //
      return Response.json({ code: 404, message: 'not found' }, { status: 404 })
    }
  })

  return {
    url: `http://localhost:${server.port}`,
    stop: () => server.stop(),
    /**
     * Inspect state captured from incoming requests.
     *
     * `received.publish` holds the last publish request's parsed fields:
     *   { namespace, fileName, visibility }
     * It is null until a publish request has been received.
     */
    received: state
  }
}
