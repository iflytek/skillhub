import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { skillBundleApi } from './api'

describe('skillBundleApi', () => {
  const originalFetch = globalThis.fetch

  beforeEach(() => {
    Object.defineProperty(document, 'cookie', { value: '', writable: true, configurable: true })
  })

  afterEach(() => {
    globalThis.fetch = originalFetch
    vi.restoreAllMocks()
  })

  it('buildDraft posts JSON body and unwraps envelope', async () => {
    const mockFetch = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          code: 0,
          msg: 'ok',
          data: { bundleId: 89, bundleVersionId: 121, status: 'DRAFT' },
          timestamp: '',
          requestId: '',
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    )
    globalThis.fetch = mockFetch as unknown as typeof fetch

    const data = await skillBundleApi.buildDraft('team-a', {
      slug: 'ops',
      displayName: 'Ops',
      version: '1.0.0',
      type: 'CUSTOM',
      summary: 's',
      items: [{ skillId: 1, skillVersionId: 11, roleDescription: 'r', required: true, installOrder: 10 }],
    })

    expect(data.bundleId).toBe(89)
    const [url, init] = mockFetch.mock.calls[0]
    expect(String(url)).toContain('/api/v1/skill-bundles/team-a/drafts/build')
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body as string).slug).toBe('ops')
  })

  it('getDetail honours optional version query param', async () => {
    const mockFetch = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          code: 0,
          msg: 'ok',
          data: {
            id: 1,
            namespaceId: 1,
            slug: 'ops',
            displayName: 'Ops',
            type: 'CUSTOM',
            summary: 's',
            downloadCount: 0,
            starCount: 0,
            ratingCount: 0,
            commentCount: 0,
            items: [],
            updatedAt: 'now',
          },
          timestamp: '',
          requestId: '',
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    )
    globalThis.fetch = mockFetch as unknown as typeof fetch

    await skillBundleApi.getDetail('team-a', 'ops', '1.0.0')

    const [url] = mockFetch.mock.calls[0]
    expect(String(url)).toContain('?version=1.0.0')
  })

  it('throws when envelope code non-zero', async () => {
    const mockFetch = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          code: 4001,
          msg: 'error.skillBundle.item.versionNotPublished',
          data: null,
          timestamp: '',
          requestId: '',
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    )
    globalThis.fetch = mockFetch as unknown as typeof fetch

    await expect(skillBundleApi.recordDownload('team-a', 'ops')).rejects.toThrow(
      'error.skillBundle.item.versionNotPublished',
    )
  })

  it('approveReview sends comment in JSON body', async () => {
    const mockFetch = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ code: 0, msg: 'ok', data: 7, timestamp: '', requestId: '' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    globalThis.fetch = mockFetch as unknown as typeof fetch

    await skillBundleApi.approveReview(7, 'looks good')

    const [, init] = mockFetch.mock.calls[0]
    expect(JSON.parse(init.body as string)).toEqual({ comment: 'looks good' })
  })
})
