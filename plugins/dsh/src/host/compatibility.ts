import { createRequire } from 'node:module';
import { gte, satisfies, valid } from 'semver';

export const minimumDshVersion = '0.2.0-rc.2';
export const dshRuntimeRange = `>=${minimumDshVersion}`;
/** Regression evidence, deliberately not an admission allowlist. */
export const testedDshVersions = ['0.2.0-rc.2', '0.2.1-rc.2', '0.2.1-rc.5', '0.2.2-rc.1', '0.2.2-rc.3'] as const;
export const supportedDshVersions = testedDshVersions;
export type SupportedDshVersion = string;

/** Retained fixture/development versions; runtime admission checks minimums and the real graph. */
export const hostDependencyBaseline = {
  '@deepseek-ai/cordis': '4.0.4',
  '@deepseek-ai/dsh-fs': minimumDshVersion,
  '@deepseek-ai/dsh-home-paths': minimumDshVersion,
  '@deepseek-ai/dsh-sandbox': minimumDshVersion,
  '@deepseek-ai/dsh-sandbox-policy': minimumDshVersion,
  '@deepseek-ai/dsh-session': minimumDshVersion,
  '@deepseek-ai/dsh-session-query': minimumDshVersion,
  '@deepseek-ai/dsh-skill-filesystem': minimumDshVersion,
  '@deepseek-ai/dsh-tools': minimumDshVersion,
  '@deepseek-ai/dsh-typert-protocol': minimumDshVersion,
} as const;

/** Historical smoke fixtures, not a list of permitted runtime combinations. */
export const cordisFamilies = [
  { '@deepseek-ai/cordis': '4.0.4', '@deepseek-ai/cordis-plugin-loader': '1.0.5',
    '@deepseek-ai/cordis-plugin-include': '1.0.9', '@deepseek-ai/cordis-plugin-timer': '1.1.6' },
  { '@deepseek-ai/cordis': '4.0.5', '@deepseek-ai/cordis-plugin-loader': '1.0.6',
    '@deepseek-ai/cordis-plugin-include': '1.0.10', '@deepseek-ai/cordis-plugin-timer': '1.1.7' },
  { '@deepseek-ai/cordis': '4.1.0', '@deepseek-ai/cordis-plugin-loader': '1.1.0',
    '@deepseek-ai/cordis-plugin-include': '1.1.0', '@deepseek-ai/cordis-plugin-timer': '1.2.0' },
] as const;

/** Accept legal build metadata without accepting normalized v-prefixed spellings. */
export function isSemanticVersion(version: unknown): version is string {
  return typeof version === 'string' && version.trim() === version && /^\d/.test(version) && valid(version) !== null;
}

export function admitsDshVersion(version: unknown): version is string {
  return isSemanticVersion(version) && satisfies(version, dshRuntimeRange, { includePrerelease: true });
}

/** Cordis still needs its existing base API; its patch number is not a Host allowlist. */
export function supportsCordis(release: string, version: string): boolean {
  return admitsDshVersion(release) && isSemanticVersion(version) && gte(version, '4.0.4');
}

const require = createRequire(import.meta.url);
export function assertHostDependencies(readManifest: (name: string) => unknown = name => require(`${name}/package.json`)): string {
  let release = minimumDshVersion;
  for (const name of Object.keys(hostDependencyBaseline)) {
    let manifest: unknown;
    try { manifest = readManifest(name); }
    catch { throw new Error('Host dependency baseline could not be verified; local writes are disabled.'); }
    if (!manifest || typeof manifest !== 'object' || !('name' in manifest) || !('version' in manifest)
      || manifest.name !== name || !isSemanticVersion(manifest.version)
      || (name === '@deepseek-ai/cordis' ? !supportsCordis(minimumDshVersion, manifest.version) : !admitsDshVersion(manifest.version))) {
      throw new Error('Host dependency baseline does not match; local writes are disabled.');
    }
    if (name === '@deepseek-ai/dsh-fs') release = manifest.version;
  }
  return release;
}
