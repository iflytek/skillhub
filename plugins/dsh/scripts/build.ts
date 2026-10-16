import { cp, mkdir, mkdtemp, readFile, realpath, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { WorkspaceTypertGenerator } from '@deepseek-ai/dsh-typert-generator';
import ts from 'typescript';
import { buildLocalCore } from './build-core.js';

const root = resolve(import.meta.dir, '..');
const manifest = await Bun.file(join(root, 'package.json')).json();
// The generator canonicalizes rootDir; source paths must use the same spelling
// when the temporary directory is a symlink (for example /var on macOS).
const staging = await realpath(await mkdtemp(join(tmpdir(), 'skillhub-dsh-build-')));
try {
  await buildLocalCore(root);
  const workspace = join(staging, 'packages');
  const plugin = join(workspace, 'plugin');
  const protocol = join(workspace, 'protocol');
  await mkdir(plugin, { recursive: true });
  const protocolRoot = dirname(fileURLToPath(import.meta.resolve('@deepseek-ai/dsh-typert-protocol/package.json')));
  // rc.2's analyzer recognizes decorators only in registered workspace packages.
  // Copy unchanged published declarations; never patch or synthesize the protocol.
  await cp(join(protocolRoot, 'lib/types'), join(protocol, 'declarations'), { recursive: true });
  const protocolManifest = await Bun.file(join(protocolRoot, 'package.json')).json();
  protocolManifest.exports = { '.': { types: './declarations/index.d.ts' }, './types': { types: './declarations/types.d.ts' } };
  await writeFile(join(protocol, 'package.json'), JSON.stringify(protocolManifest));
  const paths: Record<string, string[]> = { '@deepseek-ai/dsh-typert-protocol': [join(protocol, 'declarations/index.d.ts')] };
  const compilerOptions = {
    target: 'ES2024', module: 'NodeNext', moduleResolution: 'NodeNext', strict: true,
    skipLibCheck: true, composite: true, declaration: true, types: ['node'],
    paths,
  };
  const boundaryPackages: string[] = [];
  for (const name of ['@deepseek-ai/dsh-agent', '@deepseek-ai/dsh-session']) {
    const installed = dirname(fileURLToPath(import.meta.resolve(`${name}/package.json`)));
    const destination = join(workspace, name.split('/')[1]!);
    await cp(join(installed, 'lib/types'), join(destination, 'declarations'), { recursive: true });
    const declarationManifest = await Bun.file(join(installed, 'package.json')).json();
    const exports: Record<string, { types: string }> = {};
    for (const [key, entry] of Object.entries(declarationManifest.exports)) {
      if (typeof entry !== 'object' || !entry || !('types' in entry) || typeof entry.types !== 'string') continue;
      const declaration = entry.types.replace('./lib/types/', './declarations/');
      exports[key] = { types: declaration };
      paths[key === '.' ? name : `${name}/${key.slice(2)}`] = [join(destination, declaration)];
    }
    await writeFile(join(destination, 'package.json'), JSON.stringify({ ...declarationManifest, exports }));
    boundaryPackages.push(destination);
  }
  for (const destination of boundaryPackages) {
    await writeFile(join(destination, 'tsconfig.json'), JSON.stringify({ compilerOptions, include: ['declarations/**/*.d.ts'] }));
  }
  await writeFile(join(protocol, 'tsconfig.json'), JSON.stringify({ compilerOptions, include: ['declarations/**/*.d.ts'] }));
  await cp(join(root, 'src/host'), join(plugin, 'src/host'), { recursive: true });
  await writeFile(join(plugin, 'package.json'), JSON.stringify({ ...manifest, exports: { ...manifest.exports, '.': './src/host/index.ts' } }));
  await writeFile(join(plugin, 'tsconfig.json'), JSON.stringify({ compilerOptions, include: ['src/host/index.ts'] }));
  await writeFile(join(staging, 'tsconfig.host.json'), JSON.stringify({ compilerOptions, files: [], references: [plugin, protocol, ...boundaryPackages].map(path => ({ path })) }));
  // Dependencies resolve through the real installed package graph, not DSH source.
  const { symlink } = await import('node:fs/promises');
  await symlink(join(root, 'node_modules'), join(staging, 'node_modules'), process.platform === 'win32' ? 'junction' : 'dir');
  const artifacts = new WorkspaceTypertGenerator(staging).generate([manifest.name], ['host']);
  const artifact = artifacts[0];
  if (artifacts.length !== 1 || !artifact?.remote) throw new Error('Expected one generated Host and Remote contribution');
  await mkdir(join(root, 'lib'), { recursive: true });
  for (const [file, value] of Object.entries({
    'typert.host.js': artifact.js, 'typert.host.d.ts': artifact.dts,
    'typert.remote-client.js': artifact.remote.js, 'typert.remote-client.d.ts': artifact.remote.dts,
  })) await writeFile(join(root, 'lib', file), value);

  const host = await Bun.build({ entrypoints: [join(root, 'src/host/index.ts')], target: 'node', format: 'esm', packages: 'external',
    plugins: [{ name: 'standard-decorators', setup(build) {
      build.onLoad({ filter: /\.ts$/ }, async ({ path }) => ({ contents: ts.transpileModule(await readFile(path, 'utf8'), {
        fileName: path, compilerOptions: { target: ts.ScriptTarget.ES2024, module: ts.ModuleKind.ESNext },
      }).outputText, loader: 'js' }));
    } }],
  });
  if (!host.success) throw new AggregateError(host.logs, 'Host build failed');
  await writeFile(join(root, 'lib/index.js'), await host.outputs[0]!.text());
  const client = await Bun.build({ entrypoints: [join(root, 'src/client/index.tsx')], target: 'browser', format: 'cjs', external: ['react', 'react/jsx-runtime', 'react-dom'], define: { 'process.env.NODE_ENV': JSON.stringify('production') } });
  if (!client.success) throw new AggregateError(client.logs, 'Client build failed');
  await writeFile(join(root, 'lib/client.js'), `window.__ModuleLoader__.load({id:${JSON.stringify(manifest.name)},factory:(require)=>{var module={exports:{}};var exports=module.exports;\n${await client.outputs[0]!.text()}\nreturn module.exports;}});\n`);
  const declarations = Bun.spawn(['bun', 'x', '--no-install', 'tsc', '--project', 'tsconfig.build.json', '--noEmit', 'false', '--declaration', '--emitDeclarationOnly', '--outDir', 'lib/types', '--rootDir', 'src'], { cwd: root, stdout: 'inherit', stderr: 'inherit' });
  if (await declarations.exited !== 0) throw new Error('Declaration build failed');
} finally {
  await rm(staging, { recursive: true, force: true });
}
