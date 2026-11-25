import { createHash, randomUUID } from 'node:crypto';
import { closeSync, constants, fstatSync, lstatSync, mkdirSync, openSync, readFileSync, realpathSync, renameSync, unlinkSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import type { AccountIdentity } from './account-types.js';
import { windowsAccountProtection } from './windows-account-protection.js';

export interface Credential { token: string; identity: AccountIdentity }
// Node 24.2 rmSync can silently retain files under Unicode Windows paths.
function unlinkIfPresent(file: string): void {
  try { unlinkSync(file); }
  catch (error) { if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error; }
}
export class AccountStorageError extends Error {
  constructor(readonly reason: 'SAVE_FAILED' | 'RESTORE_FAILED' | 'CLEAR_FAILED') {
    super('The saved SkillHub account is unavailable.');
    this.name = 'AccountStorageError';
  }
}
export function secureRegistry(source: string) {
  const url = new URL(source);
  return url.protocol === 'https:' || (url.protocol === 'http:' && ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname));
}

/** Profile-local records: DPAPI CurrentUser on Windows, owner-only files on POSIX. */
export class AccountCredentials {
  private readonly memory = new Map<string, Credential | undefined>();
  private readonly removalFailures = new Set<string>();
  constructor(private readonly profileDirectory?: string) {}
  private file(source: string, create = false): string | undefined {
    if (!this.profileDirectory || (process.platform !== 'win32' && !process.getuid)) return;
    const dir = join(this.profileDirectory, 'skillhub-accounts');
    if (create) mkdirSync(dir, { recursive: true, mode: 0o700 });
    const stat = lstatSync(dir);
    if (!stat.isDirectory() || stat.isSymbolicLink()
      || (process.platform !== 'win32' && (stat.uid !== process.getuid!() || (stat.mode & 0o077)))) throw new Error('Invalid account directory.');
    return join(dir, createHash('sha256').update(source).digest('hex') + '.json');
  }
  private entropy(source: string): Buffer {
    const profile = process.platform === 'win32' ? realpathSync.native(this.profileDirectory!) : realpathSync(this.profileDirectory!);
    return createHash('sha256').update('skillhub-account-v1\0').update(profile).update('\0').update(source).digest();
  }
  hasRemovalFailure(source: string): boolean { return this.removalFailures.has(source); }
  get(source: string): Credential | undefined {
    // An explicit logout stays forgotten even when unlink failed.
    if (this.memory.has(source)) return this.memory.get(source);
    try {
      const file = this.file(source);
      if (!file) return;
      if (lstatSync(file).isSymbolicLink()) return;
      const fd = openSync(file, constants.O_RDONLY | constants.O_NOFOLLOW | constants.O_NONBLOCK);
      try {
        const st = fstatSync(fd);
        const windows = process.platform === 'win32';
        if (!st.isFile() || st.size > (windows ? 16384 : 8192)
          || (!windows && (st.uid !== process.getuid?.() || (st.mode & 0o077)))) return;
        let text = readFileSync(fd, 'utf8');
        if (windows) {
          const envelope = JSON.parse(text);
          if (envelope?.version !== 1 || envelope.protection !== 'dpapi-current-user' || typeof envelope.ciphertext !== 'string') return;
          const cipher = Buffer.from(envelope.ciphertext, 'base64');
          if (!cipher.length || cipher.toString('base64') !== envelope.ciphertext) return;
          const plain = windowsAccountProtection('unprotect', cipher, this.entropy(source));
          if (plain.length > 8192) return;
          text = plain.toString('utf8');
        }
        const item = JSON.parse(text);
        if (item?.source === source && typeof item.token === 'string' && /^\S{1,4096}$/u.test(item.token)
          && typeof item.identity?.handle === 'string' && item.identity.handle.length > 0 && typeof item.identity.displayName === 'string') {
          const credential = { token: item.token, identity: { handle: item.identity.handle, displayName: item.identity.displayName } };
          this.memory.set(source, credential);
          return credential;
        }
      } finally { closeSync(fd); }
    } catch (error) {
      if (error instanceof SyntaxError || (error as NodeJS.ErrnoException).code === 'ENOENT') return;
      throw new AccountStorageError('RESTORE_FAILED');
    }
    return undefined;
  }
  set(source: string, credential?: Credential): void {
    if (!credential) this.memory.set(source, undefined);
    try {
      const file = this.file(source, !!credential);
      if (file) {
        if (!credential) unlinkIfPresent(file);
        else {
          const plain = Buffer.from(JSON.stringify({ source, ...credential }));
          if (plain.length > 8192) throw new Error('Account record is too large.');
          const content = process.platform === 'win32'
            ? JSON.stringify({ version: 1, protection: 'dpapi-current-user', ciphertext: windowsAccountProtection('protect', plain, this.entropy(source)).toString('base64') })
            : plain.toString('utf8');
          if (Buffer.byteLength(content) > 16384) throw new Error('Account record is too large.');
          const next = file + '.' + randomUUID();
          try {
            writeFileSync(next, content, { mode: 0o600, flag: 'wx' });
            renameSync(next, file);
          } finally { unlinkIfPresent(next); }
        }
      }
    } catch (error) {
      if (credential || (error as NodeJS.ErrnoException).code !== 'ENOENT') {
        if (!credential) this.removalFailures.add(source);
        throw new AccountStorageError(credential ? 'SAVE_FAILED' : 'CLEAR_FAILED');
      }
    }
    if (credential) this.memory.set(source, credential);
    this.removalFailures.delete(source);
  }
}
