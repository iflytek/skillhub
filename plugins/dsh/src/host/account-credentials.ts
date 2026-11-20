import { createHash, randomUUID } from 'node:crypto';
import { closeSync, constants, fstatSync, lstatSync, mkdirSync, openSync, readFileSync, renameSync, rmSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import type { AccountIdentity } from './account-types.js';

export interface Credential { token: string; identity: AccountIdentity }
export function secureRegistry(source: string) {
  const url = new URL(source);
  return url.protocol === 'https:' || (url.protocol === 'http:' && ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname));
}

/** Keep each source separate. Platforms without enforceable POSIX permissions use process memory. */
export class AccountCredentials {
  private readonly memory = new Map<string, Credential>();
  constructor(private readonly profileDirectory?: string) {}
  private file(source: string, create = false): string | undefined {
    if (!this.profileDirectory || process.platform === 'win32' || !process.getuid) return;
    const dir = join(this.profileDirectory, 'skillhub-accounts');
    if (create) mkdirSync(dir, { recursive: true, mode: 0o700 });
    const stat = lstatSync(dir);
    if (!stat.isDirectory() || stat.uid !== process.getuid() || (stat.mode & 0o077)) return;
    return join(dir, createHash('sha256').update(source).digest('hex') + '.json');
  }
  get(source: string): Credential | undefined {
    const memory = this.memory.get(source);
    if (memory) return memory;
    try {
      const file = this.file(source);
      if (!file) return;
      const fd = openSync(file, constants.O_RDONLY | constants.O_NOFOLLOW | constants.O_NONBLOCK);
      try {
        const st = fstatSync(fd);
        if (!st.isFile() || st.size > 8192 || st.uid !== process.getuid?.() || (st.mode & 0o077)) return;
        const item = JSON.parse(readFileSync(fd, 'utf8'));
        if (item.source === source && typeof item.token === 'string' && typeof item.identity?.handle === 'string'
          && typeof item.identity.displayName === 'string') return { token: item.token, identity: item.identity };
      } finally { closeSync(fd); }
    } catch { /* A missing or unreadable private file is disconnected. */ }
    return undefined;
  }
  set(source: string, credential?: Credential): void {
    try {
      const file = this.file(source, !!credential);
      if (file) {
        if (!credential) rmSync(file, { force: true });
        else {
          const next = file + '.' + randomUUID();
          try {
            writeFileSync(next, JSON.stringify({ source, ...credential }), { mode: 0o600, flag: 'wx' });
            renameSync(next, file);
          } finally { rmSync(next, { force: true }); }
        }
      }
    } catch (error) {
      const missing = (error as NodeJS.ErrnoException).code === 'ENOENT';
      if (!credential && !missing) throw new Error('Unable to clear the saved SkillHub account.');
      if (credential && this.get(source)) throw new Error('Unable to replace the saved SkillHub account.');
    }
    if (credential) this.memory.set(source, credential);
    else this.memory.delete(source);
  }
}
