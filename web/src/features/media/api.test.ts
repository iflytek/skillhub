/** @vitest-environment jsdom */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mediaApi, mediaUrl } from './api'

describe('mediaUrl', () => {
  it('returns null for empty ids', () => {
    expect(mediaUrl(null)).toBeNull()
    expect(mediaUrl(undefined)).toBeNull()
    expect(mediaUrl(0)).toBeNull()
  })

  it('builds the canonical media path', () => {
    expect(mediaUrl(7)).toBe('/api/v1/media/7')
  })
})

describe('mediaApi.upload', () => {
  const originalFetch = globalThis.fetch

  beforeEach(() => {
    Object.defineProperty(document, 'cookie', { value: '', writable: true, configurable: true })
  })

  afterEach(() => {
    globalThis.fetch = originalFetch
    vi.restoreAllMocks()
  })

  it('posts a multipart body with all required fields and unwraps envelope', async () => {
    const mockFetch = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          code: 0,
          msg: 'ok',
          data: {
            id: 502,
            ownerType: 'SKILL_VERSION',
            ownerId: 7,
            mediaType: 'GIF',
            role: 'DEMO',
            url: '/api/v1/media/502',
            contentType: 'image/gif',
            sizeBytes: 8,
            altText: '演示效果',
            createdAt: 'now',
          },
          timestamp: '',
          requestId: '',
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    )
    globalThis.fetch = mockFetch as unknown as typeof fetch

    const file = new File([new Uint8Array([0x47, 0x49, 0x46, 0x38, 0x39, 0x61])], 'demo.gif', {
      type: 'image/gif',
    })

    const asset = await mediaApi.upload({
      file,
      ownerType: 'SKILL_VERSION',
      ownerId: 7,
      role: 'DEMO',
      altText: '演示效果',
    })

    expect(asset.url).toBe('/api/v1/media/502')
    const [url, init] = mockFetch.mock.calls[0]
    expect(String(url)).toBe('/api/v1/media')
    expect(init.method).toBe('POST')
    expect(init.body).toBeInstanceOf(FormData)
    const form = init.body as FormData
    expect(form.get('ownerType')).toBe('SKILL_VERSION')
    expect(form.get('ownerId')).toBe('7')
    expect(form.get('role')).toBe('DEMO')
    expect(form.get('altText')).toBe('演示效果')
    expect(form.get('file')).toBeInstanceOf(File)
  })

  it('throws on non-zero envelope codes', async () => {
    const mockFetch = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          code: 4002,
          msg: 'error.media.gif.invalidSignature',
          data: null,
          timestamp: '',
          requestId: '',
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    )
    globalThis.fetch = mockFetch as unknown as typeof fetch

    const file = new File([new Uint8Array([0x00])], 'demo.gif', { type: 'image/gif' })

    await expect(
      mediaApi.upload({ file, ownerType: 'SKILL_VERSION', ownerId: 7, role: 'DEMO' }),
    ).rejects.toThrow('error.media.gif.invalidSignature')
  })
})
