import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { assertHostDependencies, cordisFamilies, hostDependencyBaseline } from './compatibility.js';

/** Candidate runtime range, validated at its lower bound and current Node 24 release. */
export const nodeRuntimeRange = '>=24.2.0 <25';

export function assertNodeRuntime(version = process.versions.node): void {
  const match = /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/.exec(version);
  if (!match || Number(match[1]) !== 24 || Number(match[2]) < 2) {
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
] as const;

interface RuntimeServices {
  packageOf(name: string, parentURL: string): { name: string; version?: string; manifestPath?: string } | undefined;
  clientPath(name: string): string | undefined;
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

/** Read the current Host resolution and Client graph on each new write; never cache acceptance. */
export function assertRuntimeCompatibility(
  services: RuntimeServices,
  parentURL: string,
  readClientManifest: (file: string, name: string) => unknown = clientManifest,
  readHostManifest: (file: string) => unknown = file => JSON.parse(readFileSync(file, 'utf8')),
): void {
  try {
    assertNodeRuntime();
    const readPackage = (name: string, parent: string) => {
      const actual = services.packageOf(name, parent);
      if (actual?.name !== name || !actual.manifestPath) throw new Error('Runtime package mismatch');
      // packageOf caches metadata until a resolution generation is replaced.
      const current = readHostManifest(actual.manifestPath);
      if (!current || typeof current !== 'object' || !('name' in current) || !('version' in current)
        || current.name !== name || current.version !== actual.version) throw new Error('Runtime manifest mismatch');
      return actual;
    };
    const packages = new Map(Object.keys(runtimePackageBaseline).map(name => [name, readPackage(name, parentURL)]));
    const family = cordisFamilies.find(candidate => Object.entries(candidate).every(([name, version]) => packages.get(name)?.version === version));
    if (!family) throw new Error('Runtime Cordis family mismatch');
    for (const [name, version] of Object.entries({ ...runtimePackageBaseline, ...family })) {
      if (packages.get(name)?.version !== version) throw new Error('Runtime package mismatch');
    }
    // Check the Host's own dependency lookup, not just the plugin's peer copies.
    // A nested peer can otherwise hide an unsupported Host dependency graph.
    for (const anchor of ['@deepseek-ai/dsh', '@deepseek-ai/dsh-app-boot']) {
      const anchorURL = pathToFileURL(packages.get(anchor)!.manifestPath!).href;
      for (const [name, expected] of packages) {
        if (readPackage(name, anchorURL).version !== expected.version) throw new Error('Host dependency graph mismatch');
      }
    }
    assertHostDependencies(name => packages.get(name));
    for (const name of clientPackageBaseline) {
      const file = services.clientPath(name);
      if (!file) throw new Error('Client module unavailable');
      const manifest = readClientManifest(file, name);
      if (!manifest || typeof manifest !== 'object' || !('name' in manifest) || !('version' in manifest)
        || manifest.name !== name || manifest.version !== '0.2.0-rc.2') throw new Error('Client package mismatch');
    }
  } catch {
    throw new Error('Host runtime baseline could not be verified; local writes are disabled.');
  }
}
