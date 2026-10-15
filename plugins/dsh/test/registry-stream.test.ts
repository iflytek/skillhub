import { describe, expect, test } from 'bun:test';
import { RegistryAdapter } from '../src/host/registry-adapter.ts';

function client(fetchImpl: typeof fetch): RegistryAdapter {
  return new RegistryAdapter('https://registry.example', fetchImpl);
}

function transport(handler: (input: string | URL | Request, init?: RequestInit) => Response | Promise<Response>): typeof fetch {
  return Object.assign(async (input: string | URL | Request, init?: RequestInit) => handler(input, init), {
    preconnect: fetch.preconnect,
  });
}

describe('anonymous Registry response boundaries', () => {
  test('cancelling one concurrent search does not cancel another', async () => {
    const controller = new AbortController();
    let rejectSlow: ((reason: unknown) => void) | undefined;
    const fetchImpl = transport(async (input, init) => {
      if (new URL(String(input)).searchParams.get('q') === 'slow') {
        return new Promise<Response>((_resolve, reject) => {
          rejectSlow = reject;
          init?.signal?.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')), { once: true });
        });
      }
      return Response.json({ data: { items: [], total: 0, limit: 20 } });
    });
    const registry = client(fetchImpl);

    const slow = registry.search('slow', controller.signal);
    const fast = registry.search('fast');
    controller.abort();

    await expect(slow).rejects.toMatchObject({ code: 'CANCELLED', retryable: false });
    expect(await fast).toEqual({ items: [], total: 0, limit: 20 });
    expect(rejectSlow).toBeDefined();
  });

  test('decodes UTF-8 documentation split across response chunks', async () => {
    const bytes = new TextEncoder().encode('# 中文技能');
    const body = new ReadableStream<Uint8Array>({
      start(stream) {
        stream.enqueue(bytes.subarray(0, 3));
        stream.enqueue(bytes.subarray(3, 5));
        stream.enqueue(bytes.subarray(5));
        stream.close();
      },
    });
    const registry = client(transport(() => new Response(body)));

    expect(await registry.documentation('team', 'writer', '1.0.0')).toBe('# 中文技能');
  });

  test('stops reading and cancels an oversized streamed response', async () => {
    let cancelled = false;
    const body = new ReadableStream<Uint8Array>({
      pull(stream) {
        stream.enqueue(new Uint8Array(600_000));
      },
      cancel() {
        cancelled = true;
      },
    });
    const registry = client(transport(() => new Response(body)));

    await expect(registry.documentation('team', 'writer', '1.0.0')).rejects.toMatchObject({
      code: 'RESPONSE_TOO_LARGE', retryable: false,
    });
    expect(cancelled).toBe(true);
  });
});
