import { AsyncLocalStorage } from 'node:async_hooks';

export const registryCredentials = new AsyncLocalStorage<((registry: string) => string | undefined) | undefined>();

/** DSH-only transport. Credentials stay on the selected Registry's API paths. */
export function registryFetch(registry: string, token: string | undefined, fetchImpl: typeof fetch): typeof fetch {
  const api = new URL('api/', registry.replace(/\/+$/u, '') + '/');
  return Object.assign(async (input: string | URL | Request, init?: RequestInit) => {
    let url = new URL(input instanceof Request ? input.url : String(input));
    let trusted = true;
    for (let redirects = 0; ; redirects++) {
      trusted = trusted && url.origin === api.origin && url.pathname.startsWith(api.pathname);
      const headers = new Headers(init?.headers);
      headers.delete('Authorization');
      if (trusted && token) headers.set('Authorization', 'Bearer ' + token);
      const response = await fetchImpl(url, { ...init, headers, redirect: 'manual' });
      if (![301, 302, 303, 307, 308].includes(response.status)) return response;
      const location = response.headers.get('location');
      await response.body?.cancel();
      if (!location || redirects >= 10 || init?.method && init.method !== 'GET') throw new Error('Invalid Registry redirect');
      url = new URL(location, url);
      if (!['https:', 'http:'].includes(url.protocol) || url.username || url.password) throw new Error('Invalid download URL');
    }
  }, { preconnect: fetchImpl.preconnect });
}
