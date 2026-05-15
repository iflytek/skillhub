import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { promotionCampaignApi } from './api'

/**
 * Verifies the thin envelope-aware fetch wrapper behind {@link promotionCampaignApi}.
 *
 * <p>Backend always responds with the {@code ApiResponse} envelope; the wrapper must
 * unwrap on success and surface {@code msg} on non-zero codes.
 */
describe('promotionCampaignApi', () => {
  const originalFetch = globalThis.fetch

  beforeEach(() => {
    Object.defineProperty(document, 'cookie', { value: '', writable: true, configurable: true })
  })

  afterEach(() => {
    globalThis.fetch = originalFetch
    vi.restoreAllMocks()
  })

  it('listSlotItems unwraps the envelope to data', async () => {
    const mockFetch = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          code: 0,
          msg: 'ok',
          data: [{ campaignId: 1, slotCode: 'HOME_HERO', targetType: 'SKILL', targetId: 7, title: 'T' }],
          timestamp: 'now',
          requestId: 'r1',
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    )
    globalThis.fetch = mockFetch as unknown as typeof fetch

    const items = await promotionCampaignApi.listSlotItems('HOME_HERO')

    expect(items).toEqual([
      { campaignId: 1, slotCode: 'HOME_HERO', targetType: 'SKILL', targetId: 7, title: 'T' },
    ])
    const [url] = mockFetch.mock.calls[0]
    expect(String(url)).toContain('/api/v1/promotion-slots/HOME_HERO')
  })

  it('approve sends POST with comment in JSON body', async () => {
    const mockFetch = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ code: 0, msg: 'ok', data: { id: 1 }, timestamp: '', requestId: '' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    globalThis.fetch = mockFetch as unknown as typeof fetch

    await promotionCampaignApi.approve(1, 'looks good')

    const [, init] = mockFetch.mock.calls[0]
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body as string)).toEqual({ comment: 'looks good' })
    const headers = init.headers as Headers
    expect(headers.get('Content-Type')).toBe('application/json')
  })

  it('throws when envelope code is non-zero', async () => {
    const mockFetch = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          code: 4001,
          msg: 'error.promotion.target.notPublic',
          data: null,
          timestamp: '',
          requestId: '',
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    )
    globalThis.fetch = mockFetch as unknown as typeof fetch

    await expect(
      promotionCampaignApi.create({
        targetType: 'SKILL',
        targetId: 1,
        slotCode: 'HOME_HERO',
        title: 't',
        priority: 50,
        startsAt: '2026-06-01T00:00:00Z',
        endsAt: '2026-06-30T23:59:59Z',
      }),
    ).rejects.toThrow('error.promotion.target.notPublic')
  })

  it('throws on HTTP failure', async () => {
    const mockFetch = vi.fn().mockResolvedValue(new Response('boom', { status: 500 }))
    globalThis.fetch = mockFetch as unknown as typeof fetch

    await expect(promotionCampaignApi.listSlotItems('HOME_HERO')).rejects.toThrow(/Request failed: 500/)
  })

  it('attaches X-XSRF-TOKEN when cookie present', async () => {
    Object.defineProperty(document, 'cookie', { value: 'XSRF-TOKEN=abc%2D123', writable: true, configurable: true })
    const mockFetch = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ code: 0, msg: 'ok', data: null, timestamp: '', requestId: '' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    globalThis.fetch = mockFetch as unknown as typeof fetch

    await promotionCampaignApi.recordEvent(7, 'CLICK')

    const [, init] = mockFetch.mock.calls[0]
    const headers = init.headers as Headers
    expect(headers.get('X-XSRF-TOKEN')).toBe('abc-123')
  })
})
