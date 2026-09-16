import { CliError } from '../shared/errors'
import { EXIT } from '../shared/constants'

export interface WhoAmIResponse {
  handle: string
  displayName: string
  email?: string
}

export interface SearchItem {
  namespace: string
  slug: string
  latestVersion: string
  summary: string
}

export interface SearchResponse {
  items: SearchItem[]
  total: number
  limit: number
}

export interface ResolveResponse {
  namespace: string
  slug: string
  version: string
  versionId: number
  fingerprint: string
  downloadUrl: string
}

export interface DeleteResponse {
  ok: boolean
  scope: string
  action: string
  namespace: string
  slug: string
}

export interface PublishResponse {
  namespace: string
  slug: string
  version: string
  visibility: string
  status: string
}

export interface NamespaceSyncItem {
  namespace: string
  slug: string
  version: string
  versionId: number
  fingerprint: string
  updatedAt: string
  visibility: string
  downloadUrl: string
}

export interface NamespaceSyncResponse {
  items: NamespaceSyncItem[]
  nextCursor?: string | null
}

export interface SubmitReviewResponse {
  skillId: number
  versionId: number
  action: string
  status: string
}

export interface DryRunResponse {
  valid: boolean
  errors: string[]
  warnings: string[]
  resolvedSlug: string | null
  resolvedVersion: string | null
}

export interface ServerMetadata {
  apiBase?: string
  capabilities?: string[]
}

export interface SuiteInstallMember {
  skillId: number
  skillVersionId: number
  namespace: string
  slug: string
  version: string
  fingerprint: string
  downloadUrl: string
  position: number
  entry: boolean
}

export interface SuiteInstallPlan {
  operationId: string
  namespace: string
  slug: string
  version: string
  fingerprint: string
  members: SuiteInstallMember[]
}

export interface SuiteDetailMember {
  skillId: number
  skillVersionId: number
  namespace: string
  slug: string
  version: string
  fingerprint: string
  position: number
  entry: boolean
  blockingReason?: string | null
}

export interface SuiteDetail {
  id: number
  versionId: number
  namespace: string
  slug: string
  displayName: string
  summary?: string | null
  version: string
  status: string
  visibility: string
  available: boolean
  members: SuiteDetailMember[]
}

interface PublicErrorFields {
  msg?: string
  requestId?: string
}

type ErrorResponseKind = 'json' | 'download'

export class SkillHubClient {
  constructor(
    readonly registry: string,
    readonly token?: string,
    private readonly fetchImpl: typeof fetch = fetch
  ) {}

  async whoami(): Promise<WhoAmIResponse> {
    return this.getJson('/auth/whoami')
  }

  async serverMetadata(): Promise<ServerMetadata> {
    let response: Response
    try {
      response = await this.fetchImpl(`${this.registry}/.well-known/clawhub.json`)
    } catch {
      throw new CliError('registry unreachable', EXIT.network, { registry: this.registry, next: 'check network or pass --registry' })
    }
    if (!response.ok) return {}
    let body: unknown
    try {
      body = await response.json()
    } catch {
      // Older registries and reverse proxies may return an HTML landing page at this path.
      return {}
    }
    return typeof body === 'object' && body !== null ? body as ServerMetadata : {}
  }

  async suiteInstallPlan(
    namespace: string,
    slug: string,
    version?: string,
    idempotencyKey?: string
  ): Promise<SuiteInstallPlan> {
    const params = version ? `?version=${encodeURIComponent(version)}` : ''
    const url = `${this.registry}/api/v1/suites/${encodeURIComponent(namespace)}/${encodeURIComponent(slug)}/install-plan${params}`
    for (let attempt = 0; attempt < 2; attempt += 1) {
      let response: Response
      try {
        response = await this.fetchImpl(url, {
          method: 'POST',
          headers: { ...this.headers(), ...(idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {}) }
        })
      } catch {
        if (attempt === 0) continue
        throw new CliError('registry unreachable', EXIT.network, { registry: this.registry, next: 'check network or pass --registry' })
      }
      if (attempt === 0 && [502, 503, 504].includes(response.status)) continue
      try {
        return await this.handleJsonResponse<SuiteInstallPlan>(response)
      } catch (error) {
        // A successful response whose body is truncated is safe to retry with the same key.
        if (attempt === 0 && !(error instanceof CliError)) continue
        throw error
      }
    }
    throw new CliError('registry unreachable', EXIT.network, { registry: this.registry })
  }

  async suiteDetail(namespace: string, slug: string, version?: string): Promise<SuiteDetail> {
    const params = version ? `?version=${encodeURIComponent(version)}` : ''
    let response: Response
    try {
      response = await this.fetchImpl(
        `${this.registry}/api/v1/suites/${encodeURIComponent(namespace)}/${encodeURIComponent(slug)}${params}`,
        { headers: this.headers() }
      )
    } catch {
      throw new CliError('registry unreachable', EXIT.network, { registry: this.registry, next: 'check network or pass --registry' })
    }
    if (response.status === 404) {
      const error = await this.createResponseError(response, 'json')
      throw new CliError(error.message, error.exitCode, { ...error.details, status: 404 })
    }
    return this.handleJsonResponse<SuiteDetail>(response)
  }

  async downloadFromUrl(downloadUrl: string): Promise<Response> {
    let response: Response
    try {
      response = await this.fetchImpl(new URL(downloadUrl, `${this.registry}/`).toString(), { headers: this.headers() })
    } catch {
      throw new CliError('registry unreachable', EXIT.network, { registry: this.registry, next: 'check network or pass --registry' })
    }
    if (!response.ok) throw await this.createResponseError(response, 'download')
    return response
  }

  async search(query: string, limit: number): Promise<SearchResponse> {
    const params = new URLSearchParams({ q: query, limit: String(limit) })
    return this.getJson(`/skills/search?${params}`)
  }

  async resolve(namespace: string, slug: string, version?: string): Promise<ResolveResponse> {
    const params = version ? `?version=${encodeURIComponent(version)}` : ''
    return this.getJson(`/skills/${namespace}/${slug}/resolve${params}`)
  }

  async listNamespaceSkills(namespace: string, cursor?: string, limit = 100): Promise<NamespaceSyncResponse> {
    const params = new URLSearchParams({ limit: String(limit) })
    if (cursor) params.set('cursor', cursor)
    return this.getJson(`/namespaces/${encodeURIComponent(namespace)}/skills?${params}`)
  }

  async downloadUrl(namespace: string, slug: string, version?: string): Promise<string> {
    if (version) {
      return `${this.registry}/api/cli/v1/skills/${namespace}/${slug}/versions/${version}/download`
    }
    return `${this.registry}/api/cli/v1/skills/${namespace}/${slug}/download`
  }

  async download(namespace: string, slug: string, version?: string): Promise<Response> {
    const url = await this.downloadUrl(namespace, slug, version)
    let response: Response
    try {
      response = await this.fetchImpl(url, { headers: this.headers() })
    } catch {
      throw new CliError('registry unreachable', EXIT.network, { registry: this.registry, next: 'check network or pass --registry' })
    }
    if (!response.ok) {
      throw await this.createResponseError(response, 'download')
    }
    return response
  }

  async deleteRemote(namespace: string, slug: string): Promise<DeleteResponse> {
    return this.deleteJson(`/skills/${namespace}/${slug}`)
  }

  async publish(
    namespace: string,
    file: Blob,
    visibility: string,
    fileName = 'skill.zip',
    rejectExistingVersion = false
  ): Promise<PublishResponse> {
    const formData = new FormData()
    formData.append('file', file, fileName)
    formData.append('visibility', visibility)
    if (rejectExistingVersion) formData.append('rejectExistingVersion', 'true')
    let response: Response
    try {
      response = await this.fetchImpl(`${this.registry}/api/cli/v1/skills/${namespace}/publish`, {
        method: 'POST',
        headers: this.token ? { Authorization: `Bearer ${this.token}` } : {},
        body: formData
      })
    } catch {
      throw new CliError('registry unreachable', EXIT.network, { registry: this.registry, next: 'check network or pass --registry' })
    }
    return this.handleJsonResponse<PublishResponse>(response)
  }

  async validatePublish(
    namespace: string,
    file: Blob,
    visibility: string,
    fileName = 'skill.zip',
    rejectExistingVersion = false
  ): Promise<DryRunResponse> {
    const formData = new FormData()
    formData.append('file', file, fileName)
    formData.append('visibility', visibility)
    if (rejectExistingVersion) formData.append('rejectExistingVersion', 'true')
    let response: Response
    try {
      response = await this.fetchImpl(`${this.registry}/api/cli/v1/skills/${namespace}/publish/validate`, {
        method: 'POST',
        headers: this.token ? { Authorization: `Bearer ${this.token}` } : {},
        body: formData
      })
    } catch {
      throw new CliError('registry unreachable', EXIT.network, { registry: this.registry, next: 'check network or pass --registry' })
    }
    return this.handleJsonResponse<DryRunResponse>(response)
  }

  async submitReview(
    namespace: string,
    slug: string,
    version: string,
    targetVisibility: 'PUBLIC' | 'NAMESPACE_ONLY'
  ): Promise<SubmitReviewResponse> {
    let response: Response
    try {
      response = await this.fetchImpl(
        `${this.registry}/api/v1/skills/${encodeURIComponent(namespace)}/${encodeURIComponent(slug)}/submit-review`,
        {
          method: 'POST',
          headers: { ...this.headers(), 'Content-Type': 'application/json' },
          body: JSON.stringify({ version, targetVisibility })
        }
      )
    } catch {
      throw new CliError('registry unreachable', EXIT.network, { registry: this.registry, next: 'check network or pass --registry' })
    }
    return this.handleJsonResponse<SubmitReviewResponse>(response)
  }

  private async getJson<T>(path: string): Promise<T> {
    let response: Response
    try {
      response = await this.fetchImpl(`${this.registry}/api/cli/v1${path}`, {
        headers: this.headers()
      })
    } catch (err) {
      throw new CliError('registry unreachable', EXIT.network, { registry: this.registry, next: 'check network or pass --registry' })
    }
    return this.handleJsonResponse<T>(response)
  }

  private async handleJsonResponse<T>(response: Response): Promise<T> {
    if (!response.ok) {
      throw await this.createResponseError(response, 'json')
    }
    const body = await response.json()
    return body.data as T
  }

  private async createResponseError(response: Response, kind: ErrorResponseKind): Promise<CliError> {
    const publicFields = await this.readPublicErrorFields(response)
    const details: Record<string, unknown> = { registry: this.registry }
    if (publicFields.requestId) {
      details.requestId = publicFields.requestId
    }

    let fallback: string
    let exitCode: number = EXIT.generic

    if (response.status === 401) {
      fallback = 'authentication failed'
      exitCode = EXIT.auth
      details.next = 'run `skillhub login`'
    } else if (response.status === 403) {
      fallback = 'access denied'
      exitCode = EXIT.auth
    } else if (response.status === 404) {
      fallback = kind === 'download' ? 'skill or version not found' : 'resource not found'
    } else if (response.status === 502 || response.status === 503 || response.status === 504) {
      fallback = kind === 'download'
        ? `download failed with status ${response.status}`
        : `registry returned ${response.status}`
      exitCode = EXIT.network
    } else {
      fallback = kind === 'download'
        ? `download failed with status ${response.status}`
        : `registry returned ${response.status}`
    }

    return new CliError(publicFields.msg ?? fallback, exitCode, details)
  }

  private async readPublicErrorFields(response: Response): Promise<PublicErrorFields> {
    let body: unknown
    try {
      body = await response.json()
    } catch {
      return {}
    }

    if (typeof body !== 'object' || body === null || Array.isArray(body)) {
      return {}
    }

    const record = body as Record<string, unknown>
    const msg = typeof record.msg === 'string' ? record.msg.trim() : ''
    const requestId = typeof record.requestId === 'string' ? record.requestId.trim() : ''
    return {
      ...(msg ? { msg } : {}),
      ...(requestId ? { requestId } : {})
    }
  }

  private headers(): HeadersInit {
    return this.token ? { Authorization: `Bearer ${this.token}` } : {}
  }

  private async deleteJson<T>(path: string): Promise<T> {
    let response: Response
    try {
      response = await this.fetchImpl(`${this.registry}/api/cli/v1${path}`, {
        method: 'DELETE',
        headers: this.headers()
      })
    } catch {
      throw new CliError('registry unreachable', EXIT.network, { registry: this.registry, next: 'check network or pass --registry' })
    }
    return this.handleJsonResponse<T>(response)
  }
}
