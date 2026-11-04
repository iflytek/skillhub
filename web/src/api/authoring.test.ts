// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { authoringApi } from './client'
import { ApiError } from '@/shared/lib/api-error'
const fetchMock = vi.fn()
beforeEach(() => { vi.stubGlobal('fetch', fetchMock); fetchMock.mockReset(); document.cookie = 'XSRF-TOKEN=fixture-csrf; path=/' })
afterEach(() => { document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/'; vi.unstubAllGlobals() })
describe('authoring request boundary', () => {
  it('keeps binary encoding, revision and CSRF on file writes', async () => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify({ code: 0, msg: 'ok', data: { created: true } })))
    await authoringApi.saveFile(3, { path: 'assets/raw.bin', content: '/wAB', base64: true, expectedRevision: 7 })
    const [url, request] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/web/authoring/drafts/3/files')
    expect(request.method).toBe('PUT')
    expect(new Headers(request.headers).get('X-XSRF-TOKEN')).toBe('fixture-csrf')
    expect(JSON.parse(request.body)).toEqual({ path: 'assets/raw.bin', content: '/wAB', encoding: 'base64', expectedRevision: 7 })
  })
  it('encodes file paths without losing separators, unicode or query punctuation', async () => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify({ code: 0, msg: 'ok', data: { content: 'hello', encoding: 'text' } })))
    await authoringApi.readFile(3, 'references/示例 & notes.md')
    const url = new URL(fetchMock.mock.calls[0][0], 'http://localhost')
    expect(url.searchParams.get('path')).toBe('references/示例 & notes.md')
    expect([...url.searchParams.keys()]).toEqual(['path'])
  })
  it('preserves server conflict errors instead of treating saves as successful', async () => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify({ code: 409, msg: 'Revision conflict', data: null }), { status: 409 }))
    await expect(authoringApi.saveFile(3, { path: 'SKILL.md', content: 'edit', expectedRevision: 1 })).rejects.toMatchObject({ status: 409, message: 'Revision conflict' } satisfies Partial<ApiError>)
  })
})
