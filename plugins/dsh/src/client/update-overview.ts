import type { InstalledUpdateCheck } from '../host/types.js';

export type UpdateResult = InstalledUpdateCheck | { state: 'failed' };
/** Bounded read-only queue. Cancellation and timeouts settle even if a transport ignores abort. */
export async function checkInstalledUpdates(keys: string[], check: (key: string, signal: AbortSignal) => Promise<InstalledUpdateCheck>, signal: AbortSignal,
  receive: (key: string, result: UpdateResult) => void, timeoutMs = 30_000) {
  let cursor = 0;
  async function worker() {
    while (!signal.aborted && cursor < keys.length) {
      const key = keys[cursor++]!;
      const controller = new AbortController();
      let timer: ReturnType<typeof setTimeout> | undefined;
      let cancel = () => {};
      try {
        const interrupted = new Promise<never>((_, reject) => {
          cancel = () => { controller.abort(); reject(new Error('cancelled')); };
          signal.addEventListener('abort', cancel, { once: true });
          timer = setTimeout(cancel, timeoutMs);
        });
        const result = await Promise.race([check(key, controller.signal), interrupted]);
        if (!signal.aborted) receive(key, result);
      } catch {
        if (!signal.aborted) receive(key, { state: 'failed' });
      } finally {
        clearTimeout(timer); signal.removeEventListener('abort', cancel);
      }
    }
  }
  await Promise.all(Array.from({ length: Math.min(3, keys.length) }, worker));
}
export const updateFailed = (result?: UpdateResult) => result?.state === 'failed' || result?.state === 'unavailable';
