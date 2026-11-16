import { readFileSync, realpathSync } from 'node:fs';
import { dirname, isAbsolute, join, relative } from 'node:path';
import { pathToFileURL } from 'node:url';
import { gte, prerelease, satisfies } from 'semver';
import { admitsDshVersion, assertHostDependencies, hostDependencyBaseline, isSemanticVersion } from './compatibility.js';

export const nodeRuntimeRange = '>=24.2.0';

export function assertNodeRuntime(version = process.versions.node, release?: string): void {
  const minimum = release && admitsDshVersion(release) && gte(release, '0.2.2-0') ? '24.9.0' : '24.2.0';
  if (!isSemanticVersion(version) || prerelease(version) !== null || !gte(version, minimum)) {
    throw new Error('Node runtime baseline does not match; local writes are disabled.');
  }
}

export const runtimePackageBaseline = {
  '@deepseek-ai/dsh': '0.2.0-rc.2',
  '@deepseek-ai/dsh-app-boot': '0.2.0-rc.2',
  ...hostDependencyBaseline,
  '@deepseek-ai/cordis-plugin-loader': '1.0.5',
  '@deepseek-ai/cordis-plugin-include': '1.0.9',
  '@deepseek-ai/cordis-plugin-timer': '1.1.6',
};

export const clientPackageBaseline = [
  '@deepseek-ai/dsh-api-gateway',
  '@deepseek-ai/dsh-client-ui-layout',
  '@deepseek-ai/dsh-client-ui-sidebar',
  '@deepseek-ai/dsh-client-ui-settings',
  '@deepseek-ai/dsh-client-ui-plugin-manager',
  '@deepseek-ai/dsh-client-ui-tool',
  '@deepseek-ai/dsh-api-session-controller',
  '@deepseek-ai/dsh-client-ui-session',
  '@deepseek-ai/dsh-client-ui-conversation',
  '@deepseek-ai/dsh-api-workspace-controller',
  '@deepseek-ai/dsh-client-ui-workspace',
] as const;

type PackageRecord = { name: string; version?: string; manifestPath?: string };
interface RuntimeServices {
  packageOf(name: string, parentURL: string): PackageRecord | undefined;
  clientPath(name: string): string | undefined;
  hasAgentLookup(): boolean;
}

function clientManifest(file: string, name: string): unknown {
  let directory = dirname(file);
  while (directory) {
    try {
      const manifest = JSON.parse(readFileSync(join(directory, 'package.json'), 'utf8'));
      if (manifest.name === name) return manifest;
    } catch (error) {
      if (!(error && typeof error === 'object' && 'code' in error && error.code === 'ENOENT')) throw error;
    }
    const parent = dirname(directory);
    if (parent === directory) break;
    directory = parent;
  }
  throw new Error('Client owning manifest unavailable');
}

function manifestIdentity(value: unknown, name: string): value is { name: string; version: string; dependencies?: Record<string, string>; peerDependencies?: Record<string, string> } {
  return !!value && typeof value === 'object' && 'name' in value && value.name === name
    && 'version' in value && isSemanticVersion(value.version);
}

/** Read actual resolution and current manifests for every mutation; tested versions are not a gate. */
export function assertRuntimeCompatibility(
  services: RuntimeServices,
  parentURL: string,
  readClientManifest: (file: string, name: string) => unknown = clientManifest,
  readHostManifest: (file: string) => unknown = file => JSON.parse(readFileSync(file, 'utf8')),
): string {
  try {
    assertNodeRuntime();
    if (!services.hasAgentLookup()) throw new Error('Agent lookup capability unavailable');
    const manifests = new Map<string, ReturnType<typeof readHostManifest>>();
    const readPackage = (name: string, parent: string) => {
      const actual = services.packageOf(name, parent);
      if (actual?.name !== name || !actual.manifestPath || typeof actual.version !== 'string') throw new Error('Runtime package mismatch');
      // packageOf may cache metadata until its resolution generation changes.
      const current = readHostManifest(actual.manifestPath);
      if (!manifestIdentity(current, name) || current.version !== actual.version) throw new Error('Runtime manifest mismatch');
      manifests.set(name, current);
      return actual as PackageRecord & { version: string; manifestPath: string };
    };
    const packages = new Map(Object.keys(runtimePackageBaseline).map(name => [name, readPackage(name, parentURL)]));
    const release = packages.get('@deepseek-ai/dsh')!.version;
    if (!admitsDshVersion(release)) throw new Error('Host is below minimum version');
    assertNodeRuntime(process.versions.node, release);
    assertHostDependencies(name => packages.get(name));

    // Compare against the Host's resolution, not a stale/nested plugin peer copy.
    const anchors = ['@deepseek-ai/dsh', '@deepseek-ai/dsh-app-boot'].map(name => pathToFileURL(packages.get(name)!.manifestPath).href);
    for (const anchor of anchors) {
      for (const [name, expected] of packages) {
        const actual = readPackage(name, anchor);
        if (actual.version !== expected.version || actual.manifestPath !== expected.manifestPath) throw new Error('Host dependency graph mismatch');
      }
    }
    for (const name of clientPackageBaseline) {
      const file = services.clientPath(name);
      if (!file) throw new Error('Client module unavailable');
      const owner = readPackage(name, anchors[0]!);
      const other = readPackage(name, anchors[1]!);
      const path = relative(dirname(owner.manifestPath), file);
      if (readClientManifest === clientManifest) {
        const canonical = relative(realpathSync(dirname(owner.manifestPath)), realpathSync(file));
        if (isAbsolute(canonical) || canonical === '..' || canonical.startsWith(`..${process.platform === 'win32' ? '\\' : '/'}`)) throw new Error('Client entry escapes owning package');
      }
      if (other.version !== owner.version || other.manifestPath !== owner.manifestPath
        || isAbsolute(path) || path === '..' || path.startsWith(`..${process.platform === 'win32' ? '\\' : '/'}`)) throw new Error('Client owning package mismatch');
      const manifest = readClientManifest(file, name);
      if (!manifestIdentity(manifest, name) || manifest.version !== owner.version) throw new Error('Client package mismatch');
      packages.set(name, owner);
    }
    // Legitimate module versions may differ from the root release. Validate what the Host declares.
    for (const [name, value] of manifests) {
      if (!manifestIdentity(value, name)) throw new Error('Invalid Host manifest');
      for (const dependencies of [value.dependencies, value.peerDependencies]) {
        if (dependencies === undefined) continue;
        if (!dependencies || typeof dependencies !== 'object' || Array.isArray(dependencies)) throw new Error('Invalid Host dependencies');
        for (const [dependency, range] of Object.entries(dependencies)) {
          const actual = packages.get(dependency);
          if (!actual) continue;
          if (typeof range !== 'string' || !satisfies(actual.version, range, { includePrerelease: true })) throw new Error('Host declared dependency mismatch');
        }
      }
    }
    return release;
  } catch {
    throw new Error('Host runtime baseline could not be verified; local writes are disabled.');
  }
}
