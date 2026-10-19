import { createRequire } from 'node:module';

/** Exact Host dependency baseline; a match is necessary, not full product acceptance. */
export const hostDependencyBaseline = {
  '@deepseek-ai/cordis': '4.0.4',
  '@deepseek-ai/dsh-fs': '0.2.0-rc.2',
  '@deepseek-ai/dsh-home-paths': '0.2.0-rc.2',
  '@deepseek-ai/dsh-sandbox': '0.2.0-rc.2',
  '@deepseek-ai/dsh-sandbox-policy': '0.2.0-rc.2',
  '@deepseek-ai/dsh-session': '0.2.0-rc.2',
  '@deepseek-ai/dsh-session-query': '0.2.0-rc.2',
  '@deepseek-ai/dsh-skill-filesystem': '0.2.0-rc.2',
  '@deepseek-ai/dsh-tools': '0.2.0-rc.2',
  '@deepseek-ai/dsh-typert-protocol': '0.2.0-rc.2',
} as const;

/** Only combinations exercised together by the native Host smoke are accepted. */
export const cordisFamilies = [
  { '@deepseek-ai/cordis': '4.0.4', '@deepseek-ai/cordis-plugin-loader': '1.0.5',
    '@deepseek-ai/cordis-plugin-include': '1.0.9', '@deepseek-ai/cordis-plugin-timer': '1.1.6' },
  { '@deepseek-ai/cordis': '4.0.5', '@deepseek-ai/cordis-plugin-loader': '1.0.6',
    '@deepseek-ai/cordis-plugin-include': '1.0.10', '@deepseek-ai/cordis-plugin-timer': '1.1.7' },
] as const;

const require = createRequire(import.meta.url);
export function assertHostDependencies(readManifest: (name: string) => unknown = name => require(`${name}/package.json`)): void {
  for (const [name, version] of Object.entries(hostDependencyBaseline)) {
    let manifest: unknown;
    try { manifest = readManifest(name); }
    catch { throw new Error('Host dependency baseline could not be verified; local writes are disabled.'); }
    if (!manifest || typeof manifest !== 'object' || !('name' in manifest) || !('version' in manifest)
      || manifest.name !== name || (name === '@deepseek-ai/cordis'
        ? !cordisFamilies.some(family => family['@deepseek-ai/cordis'] === manifest.version)
        : manifest.version !== version)) {
      throw new Error('Host dependency baseline does not match; local writes are disabled.');
    }
  }
}
