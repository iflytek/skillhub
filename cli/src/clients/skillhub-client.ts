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
}

export interface DryRunResponse {
  valid: boolean
  errors: string[]
  warnings: string[]
  resolvedSlug: string | null
  resolvedVersion: string | null
}

export class SkillHubClient {
  constructor(
    readonly registry: string,
    readonly token?: string,
    private readonly fetchImpl: typeof fetch = fetch
  ) {}

  async whoami(): Promise<WhoAmIResponse> {
    return this.getJson('/auth/whoami')
  }

  async search(query: string, limit: number): Promise<SearchResponse> {
    const params = new URLSearchParams({ q: query, limit: String(limit) })
    return this.getJson(`/skills/search?${params}`)
  }

  async resolve(namespace: string, slug: string, version?: string): Promise<ResolveResponse> {
    const params = version ? `?version=${encodeURIComponent(version)}` : ''
    return this.getJson(`/skills/${namespace}/${slug}/resolve${params}`)
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
    if (response.status === 401 || response.status === 403) {
      throw new CliError('authentication failed', EXIT.auth, { registry: this.registry, next: 'run `skillhub login`' })
    }
    if (response.status === 404) {
      throw new CliError('skill or version not found', EXIT.generic, { registry: this.registry })
    }
    if (!response.ok) {
      throw new CliError(`download failed with status ${response.status}`, EXIT.generic, { registry: this.registry })
    }
    return response
  }

  async deleteRemote(namespace: string, slug: string): Promise<DeleteResponse> {
    return this.deleteJson(`/skills/${namespace}/${slug}`)
  }

  async publish(namespace: string, file: Blob, visibility: string, fileName = 'skill.zip'): Promise<PublishResponse> {
    const formData = new FormData()
    formData.append('file', file, fileName)
    formData.append('visibility', visibility)
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

  async validatePublish(namespace: string, file: Blob, visibility: string, fileName = 'skill.zip'): Promise<DryRunResponse> {
    const formData = new FormData()
    formData.append('file', file, fileName)
    formData.append('visibility', visibility)
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
    if (response.status === 401) {
      throw new CliError('authentication failed', EXIT.auth, { registry: this.registry, next: 'run `skillhub login`' })
    }
    if (response.status === 403) {
      throw new CliError('access denied — token may lack required scope', EXIT.auth, { registry: this.registry, next: 'regenerate token with required scopes or run `skillhub login`' })
    }
    if (response.status === 404) {
      throw new CliError('resource not found', EXIT.generic, { registry: this.registry })
    }
    // 502/503 indicate network-level failures (connection refused, service unavailable)
    if (response.status === 502 || response.status === 503) {
      throw new CliError(`registry returned ${response.status}`, EXIT.network, { registry: this.registry })
    }
    if (!response.ok) {
      const text = await response.text().catch(() => '')
      throw new CliError(`registry returned ${response.status}`, EXIT.generic, { registry: this.registry, detail: text })
    }
    const body = await response.json()
    return body.data as T
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
