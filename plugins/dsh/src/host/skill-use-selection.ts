import { isDeepStrictEqual } from 'node:util';
import type { InstalledSkillList, SkillUsePreparation, SkillUseSelection } from './types.js';

/** A bounded observation of every managed candidate, never an invocation authorization. */
export async function selectUsableInstallation(input: {
  namespace: string; slug: string; signal: AbortSignal; valid(): void;
  list(signal: AbortSignal): Promise<InstalledSkillList>;
  prepare(itemKey: string, signal: AbortSignal): Promise<SkillUsePreparation>;
}, timeoutMs = 5000): Promise<SkillUseSelection> {
  const deadline = new AbortController();
  const signal = AbortSignal.any([input.signal, deadline.signal]);
  let timer: ReturnType<typeof setTimeout> | undefined;
  let onAbort: (() => void) | undefined;
  const valid = () => { input.signal.throwIfAborted(); input.valid(); signal.throwIfAborted(); };
  const blocked = (reason: Extract<SkillUseSelection, { status: 'blocked' }>['reason']): SkillUseSelection => ({ status: 'blocked', reason });
  const read = async () => {
    valid(); const list = await input.list(signal); valid();
    if (!list.complete || list.unavailable.length || list.issues.length) return undefined;
    return list.items.filter(item => item.namespace === input.namespace && item.slug === input.slug)
      .sort((a, b) => a.itemKey.localeCompare(b.itemKey));
  };
  const run = async (): Promise<SkillUseSelection> => {
    const candidates = await read();
    if (!candidates || candidates.length > 16) return blocked('selection-unverified');
    if (!candidates.length) return blocked('no-installation');
    if (new Set(candidates.map(item => item.itemKey)).size !== candidates.length) return blocked('selection-unverified');
    const observe = async () => {
      const results: SkillUsePreparation[] = new Array(candidates.length);
      let cursor = 0;
      await Promise.all(Array.from({ length: Math.min(4, candidates.length) }, async () => {
        while (cursor < candidates.length) {
          const index = cursor++;
          valid(); results[index] = await input.prepare(candidates[index].itemKey, signal); valid();
        }
      }));
      return results;
    };
    const first = await observe();
    if (!isDeepStrictEqual(candidates, await read())) return blocked('selection-unverified');
    const second = await observe();
    if (!isDeepStrictEqual(first, second) || !isDeepStrictEqual(candidates, await read())) return blocked('selection-unverified');
    const ready = second.flatMap((result, index) => result.status === 'ready' ? [candidates[index]] : []);
    if (ready.length > 1) return blocked('multiple-usable-installations');
    // A modified/unreadable/unknown sibling may hide another runtime name. Never guess around it.
    if (second.some(result => result.status === 'blocked'
      && !['installation-disabled', 'skill-shadowed', 'user-invocation-disabled'].includes(result.reason))) {
      return blocked('selection-unverified');
    }
    return ready.length ? { status: 'ready', itemKey: ready[0].itemKey } : blocked('no-usable-installation');
  };
  try {
    const expired = new Promise<never>((_, reject) => {
      onAbort = () => reject(signal.reason);
      signal.addEventListener('abort', onAbort, { once: true });
      if (signal.aborted) onAbort();
      timer = setTimeout(() => deadline.abort(), timeoutMs);
    });
    return await Promise.race([run(), expired]);
  } catch {
    input.signal.throwIfAborted(); input.valid();
    return blocked(deadline.signal.aborted ? 'selection-timeout' : 'selection-unverified');
  } finally { clearTimeout(timer); if (onAbort) signal.removeEventListener('abort', onAbort); deadline.abort(); }
}
