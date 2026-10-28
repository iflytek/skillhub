import type { Context } from '@deepseek-ai/cordis';
import { FileSystemSkillProvider } from '@deepseek-ai/dsh-skill-filesystem';
import { lstat } from 'node:fs/promises';
import { join } from 'node:path';
import { isDeepStrictEqual } from 'node:util';
import { sameDshSkill, type LoadedSkill } from './skill-discovery.js';
import type { RemovalBaseline } from './generated/native-contract.js';
import type { SkillAvailability } from './types.js';

export interface AvailabilityReader {
  snapshot?(signal?: AbortSignal): Promise<{ complete: boolean; skills: readonly { name: string }[] }>;
  get(name: string, signal?: AbortSignal): Promise<LoadedSkill | undefined>;
}
export const skillNamePattern = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;

/** Whitelisted observations only. Neither a successful check nor a name lookup authorizes changes. */
export async function inspectSkillAvailability(input: {
  ctx: Context; reader?: AvailabilityReader; signal: AbortSignal; valid(): void; name?: string;
  managed?: { directory: string; fingerprint?: string; baseline(): Promise<RemovalBaseline> };
}): Promise<SkillAvailability> {
  const { reader, managed, signal } = input;
  const valid = () => { signal.throwIfAborted(); input.valid(); };
  const read = async <T>(work: () => Promise<T>): Promise<T> => {
    valid(); try { return await work(); } finally { valid(); }
  };
  const result: SkillAvailability = { checkedAt: new Date().toISOString(),
    association: managed ? 'managed-reference' : 'not-associated', integrity: 'unknown', discovery: 'unknown', reason: 'host-unavailable' };
  let expected: LoadedSkill | undefined;
  let baseline: RemovalBaseline | undefined;
  let name = input.name;
  valid();
  try {
    if (managed) {
      // Only the Host-validated exact installation is read. ENOENT is the only missing proof.
      try {
        for (const path of [managed.directory, join(managed.directory, 'SKILL.md')]) {
          const stat = await read(() => lstat(path));
          if (stat.isSymbolicLink() || (path === managed.directory ? !stat.isDirectory() : !stat.isFile())) { result.reason = 'files-unreadable'; return result; }
        }
      } catch (error) {
        valid();
        result.integrity = (error as NodeJS.ErrnoException).code === 'ENOENT' ? 'missing' : 'unknown';
        result.reason = result.integrity === 'missing' ? 'files-missing' : 'files-unreadable';
        return result;
      }
      baseline = await read(managed.baseline);
      if (baseline.status === 'blocked') {
        result.integrity = baseline.reason === 'local-changes' ? 'changed' : 'unknown';
        result.reason = baseline.reason === 'local-changes' ? 'local-changes' : 'baseline-unverified';
      } else if (baseline.kind === 'uninstall' && baseline.target.path === managed.directory && managed.fingerprint
        && baseline.inventoryItem.targets.some(target => target.installDir === managed.directory && target.fingerprint === managed.fingerprint)) {
        result.integrity = 'verified';
      } else { result.reason = 'baseline-unverified'; }
      const provider = new FileSystemSkillProvider(input.ctx, { signal, invalidate: () => {} }, {
        providerName: 'skillhub-availability', includeDefaultRoots: false, customSkillDirs: [managed.directory], watch: false,
      });
      try {
        const listed = await read(() => provider.list({ signal }));
        if (!Array.isArray(listed) && !listed.complete) { result.reason = 'files-unreadable'; return result; }
        const candidate = (Array.isArray(listed) ? listed : listed.candidates).find(value => value.path === join(managed.directory, 'SKILL.md'));
        if (candidate) expected = await read(() => provider.get(candidate, { signal }));
      } finally {
        try { await provider.dispose(); } finally { valid(); }
      }
      if (!expected) { result.reason = 'definition-unverified'; return result; }
      name = expected.name;
    }
    if (!name || name.length > 128 || !skillNamePattern.test(name)) { result.reason = 'definition-unverified'; return result; }
    result.name = name;
    if (!reader?.snapshot) { result.reason = 'snapshot-unavailable'; return result; }
    const snapshot = await read(() => reader.snapshot!());
    if (!snapshot.complete) { result.reason = 'snapshot-incomplete'; return result; }
    const winner = await read(() => reader.get(name!));
    const finalSnapshot = await read(() => reader.snapshot!());
    const finalWinner = await read(() => reader.get(name!));
    if (!finalSnapshot.complete || !isDeepStrictEqual(snapshot, finalSnapshot) || !isDeepStrictEqual(winner, finalWinner)) {
      result.reason = 'observation-changed'; return result;
    }
    if (managed && !isDeepStrictEqual(baseline, await read(managed.baseline))) {
      result.integrity = 'unknown'; result.reason = 'observation-changed'; return result;
    }
    if (!winner) {
      if (snapshot.skills.some(skill => skill.name === name)) { result.reason = 'definition-unverified'; return result; }
      result.discovery = 'not-discovered'; result.reason = 'not-discovered'; return result;
    }
    if (winner.name !== name || !snapshot.skills.some(skill => skill.name === name)) { result.reason = 'observation-changed'; return result; }
    if (managed && expected) {
      if (winner.path !== expected.path || winner.resourceBase?.kind !== 'directory' || winner.resourceBase.path !== managed.directory) {
        result.discovery = 'shadowed'; result.reason = 'same-name-winner'; return result;
      }
      if (!sameDshSkill(winner, expected, managed.directory)) { result.reason = 'definition-changed'; return result; }
    }
    result.discovery = 'winner';
    result.invocation = { userInvocable: winner.invocation.userInvocable, modelInvocable: winner.invocation.modelInvocable };
    if (!managed || result.integrity === 'verified') result.reason = 'observed';
    return result;
  } catch {
    valid(); result.discovery = 'unknown'; result.reason = 'read-failed'; return result;
  }
}

/** Conservative routing-name extraction, not a replacement YAML parser. Ambiguous syntax stays unknown. */
export function installationEntryName(content: string): string | undefined {
  if (content.length > 1024 * 1024 || !content.startsWith('---\n') && !content.startsWith('---\r\n')) return undefined;
  const lines = content.split(/\r?\n/u);
  const end = lines.indexOf('---', 1);
  if (end < 0) return undefined;
  const fields = lines.slice(1, end);
  // Complex mappings/anchors can supply or replace name indirectly; do not infer their semantics.
  if (fields.some(line => /^\s*(?:<<|["']name["'])\s*:/u.test(line) || /[&*]/u.test(line))) return undefined;
  const names = fields.filter(line => /^name\s*:/u.test(line));
  if (names.length !== 1) return undefined;
  const value = names[0]!.slice(names[0]!.indexOf(':') + 1).trim();
  const match = /^(?:([a-z0-9]+(?:-[a-z0-9]+)*)|'([a-z0-9]+(?:-[a-z0-9]+)*)'|"([a-z0-9]+(?:-[a-z0-9]+)*)")(?:\s+#.*)?$/u.exec(value);
  const name = match?.[1] ?? match?.[2] ?? match?.[3];
  return name && name.length <= 128 ? name : undefined;
}

/** Disabled state does not establish discovery exclusion: observe the real current-session winner. */
export async function inspectDisabledSkillAvailability(input: {
  directory: string; fingerprint?: string; signal: AbortSignal; valid(): void;
  baseline(): Promise<RemovalBaseline>; entry(): Promise<string | undefined>; reader?: AvailabilityReader;
}): Promise<SkillAvailability> {
  const result: SkillAvailability = { checkedAt: new Date().toISOString(), association: 'managed-reference',
    installationState: 'disabled', integrity: 'unknown', discovery: 'unknown', reason: 'installation-disabled' };
  const deadline = AbortSignal.timeout(2000);
  const signal = AbortSignal.any([input.signal, deadline]);
  const valid = () => { input.signal.throwIfAborted(); input.valid(); };
  let abort: (() => void) | undefined;
  try {
    return await Promise.race([(async () => {
      const read = async <T>(action: () => Promise<T>): Promise<T> => {
        valid(); signal.throwIfAborted(); const value = await action(); valid(); signal.throwIfAborted(); return value;
      };
      const baseline = await read(input.baseline);
      if (baseline.status === 'blocked') {
        result.integrity = baseline.reason === 'local-changes' ? 'changed' : 'unknown'; return result;
      }
      if (baseline.kind !== 'uninstall' || baseline.target.path !== input.directory || !input.fingerprint
        || !baseline.inventoryItem.targets.some(target => target.installDir === input.directory && target.fingerprint === input.fingerprint
          && target.installationState === 'disabled')) return result;
      result.integrity = 'verified';
      const content = await read(input.entry);
      if (content === undefined) { result.integrity = 'unknown'; result.reason = 'baseline-unverified'; return result; }
      const name = installationEntryName(content);
      if (!name) { result.reason = 'definition-unverified'; return result; }
      result.name = name;
      if (!input.reader?.snapshot) { result.reason = 'snapshot-unavailable'; return result; }
      const snapshot = await read(() => input.reader!.snapshot!(signal));
      if (!snapshot.complete) { result.reason = 'snapshot-incomplete'; return result; }
      const winner = await read(() => input.reader!.get(name, signal));
      const finalSnapshot = await read(() => input.reader!.snapshot!(signal));
      const finalWinner = await read(() => input.reader!.get(name, signal));
      if (!finalSnapshot.complete || !isDeepStrictEqual(snapshot, finalSnapshot) || !isDeepStrictEqual(winner, finalWinner)
        || !isDeepStrictEqual(baseline, await read(input.baseline))) { result.reason = 'observation-changed'; return result; }
      if (!winner) {
        if (snapshot.skills.some(skill => skill.name === name)) { result.reason = 'definition-unverified'; return result; }
        result.discovery = 'not-discovered'; result.reason = 'not-discovered'; return result;
      }
      if (winner.name !== name || !snapshot.skills.some(skill => skill.name === name)) { result.reason = 'observation-changed'; return result; }
      if (winner.path === join(input.directory, 'SKILL.md') && winner.resourceBase?.kind === 'directory'
        && winner.resourceBase.path === input.directory) {
        result.discovery = 'winner'; // A cached selected entry can still be observed after disablement.
      } else { result.discovery = 'shadowed'; result.reason = 'same-name-winner'; }
      // No invocation permission is inferred for a disabled installation or transferred from its fallback.
      return result;
    })(), new Promise<never>((_resolve, reject) => {
      abort = () => reject(new Error('Discovery observation timed out'));
      signal.addEventListener('abort', abort, { once: true });
      if (signal.aborted) abort();
    })]);
  } catch { valid(); return { ...result, discovery: 'unknown', reason: 'read-failed' }; }
  finally { if (abort) signal.removeEventListener('abort', abort); }
}
