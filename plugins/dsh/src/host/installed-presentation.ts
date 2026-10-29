import { constants } from 'node:fs';
import { lstat, open, realpath } from 'node:fs/promises';
import { isAbsolute, join, resolve } from 'node:path';
import { parseDocument } from 'yaml';
import type { InstalledProjectionItem, InstalledProjectionTarget } from './generated/native-contract.js';

export type InstalledPresentation = { displayName?: string; summary?: string };
export const MAX_PRESENTATION_BYTES = 64 * 1024;

function text(value: unknown, limit: number): string | undefined {
  if (typeof value !== 'string') return undefined;
  const normalized = Array.from(value, character => character.charCodeAt(0) < 32 || character.charCodeAt(0) === 127 ? ' ' : character).join('').replace(/\s+/gu, ' ').trim();
  return normalized ? normalized.slice(0, limit) : undefined;
}

/** Presentation only: never return the instruction body or treat these fields as identity. */
export function parseInstalledPresentation(prefix: Uint8Array): InstalledPresentation {
  if (prefix.byteLength > MAX_PRESENTATION_BYTES) return {};
  const content = new TextDecoder('utf-8', { fatal: true });
  try {
    const lines = content.decode(prefix, { stream: true }).replace(/^\uFEFF/u, '').split(/\r?\n/u);
    if (lines[0] !== '---') return {};
    const closing = lines.findIndex((line, index) => index > 0 && line === '---');
    if (closing < 0) return {};
    const document = parseDocument(lines.slice(1, closing).join('\n'), { uniqueKeys: true });
    if (document.errors.length || document.warnings.length) return {};
    const data: unknown = document.toJS({ maxAliasCount: 0 });
    if (!data || typeof data !== 'object' || Array.isArray(data)) return {};
    const fields = data as Record<string, unknown>;
    const displayName = text(fields.name, 128);
    const summary = text(fields.description, 1024);
    return { ...(displayName ? { displayName } : {}), ...(summary ? { summary } : {}) };
  } catch { return {}; }
}

async function canonicalDirectory(path: string): Promise<boolean> {
  const info = await lstat(path);
  return info.isDirectory() && !info.isSymbolicLink() && await realpath(path) === path;
}

async function readPresentation(item: InstalledProjectionItem, root: string, signal: AbortSignal): Promise<InstalledPresentation> {
  signal.throwIfAborted();
  if (item.state !== 'present' || !/^[a-zA-Z0-9][a-zA-Z0-9._-]*$/.test(item.slug)
    || join(root, item.slug) !== item.targetPath) return {};
  try {
    if (!await canonicalDirectory(item.targetPath)) return {};
    const path = join(item.targetPath, item.installationState === 'disabled' ? 'SKILL.md.disabled' : 'SKILL.md');
    const before = await lstat(path, { bigint: true });
    if (!before.isFile() || before.isSymbolicLink()) return {};
    const file = await open(path, constants.O_RDONLY | (constants.O_NOFOLLOW ?? 0) | (constants.O_NONBLOCK ?? 0));
    try {
      const opened = await file.stat({ bigint: true });
      if (!opened.isFile() || opened.dev !== before.dev || opened.ino !== before.ino) return {};
      const bytes = new Uint8Array(MAX_PRESENTATION_BYTES);
      let size = 0;
      while (size < bytes.length) {
        signal.throwIfAborted();
        const { bytesRead } = await file.read(bytes, size, bytes.length - size, size);
        if (!bytesRead) break;
        size += bytesRead;
      }
      const after = await file.stat({ bigint: true });
      const current = await lstat(path, { bigint: true });
      if (after.dev !== opened.dev || after.ino !== opened.ino || after.size !== opened.size
        || after.mtimeNs !== opened.mtimeNs || after.ctimeNs !== opened.ctimeNs
        || current.dev !== opened.dev || current.ino !== opened.ino || !current.isFile()
        || !await canonicalDirectory(root) || !await canonicalDirectory(item.targetPath)) return {};
      return parseInstalledPresentation(bytes.subarray(0, size));
    } finally { await file.close(); }
  } catch { signal.throwIfAborted(); return {}; }
}

/** One bounded local read per valid target; no inventory traversal, discovery, or network call. */
export async function readInstalledPresentations(items: readonly InstalledProjectionItem[], roots: readonly InstalledProjectionTarget[], signal: AbortSignal): Promise<InstalledPresentation[]> {
  const approved = new Map<string, string>();
  for (const root of roots) {
    signal.throwIfAborted();
    if (!isAbsolute(root.root) || resolve(root.root) !== root.root) continue;
    try { if (await canonicalDirectory(root.root)) approved.set(root.scope, root.root); } catch { signal.throwIfAborted(); }
  }
  const result: InstalledPresentation[] = [];
  for (const item of items) {
    signal.throwIfAborted();
    const root = approved.get(item.scope);
    result.push(root ? await readPresentation(item, root, signal) : {});
  }
  signal.throwIfAborted();
  return result;
}
