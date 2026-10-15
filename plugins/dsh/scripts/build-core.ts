import { cp, mkdir, writeFile } from 'node:fs/promises';
import { join, resolve } from 'node:path';

/** Private runtime artifact; bundle the CLI's own dependency graph, including its Zod major. */
export async function buildLocalCore(root: string): Promise<void> {
  const cli = resolve(root, '../../cli');
  const generated = Bun.spawn(['bun', 'run', 'scripts/generate-pkg-info.ts'], { cwd: cli, stdout: 'inherit', stderr: 'inherit' });
  if (await generated.exited !== 0) throw new Error('Shared core package metadata generation failed');
  const loaded: string[] = [];
  const result = await Bun.build({
    entrypoints: [join(cli, 'src/integrations/local-skills.ts')], target: 'node', format: 'esm',
    plugins: [{ name: 'core-import-boundary', setup(build) {
      build.onLoad({ filter: /\.[cm]?[jt]s$/ }, args => { loaded.push(args.path.replaceAll('\\', '/')); return undefined; });
    } }],
  });
  if (!result.success) throw new AggregateError(result.logs, 'Shared installation core build failed');
  if (loaded.some(path => /\/commands\/|\/cli\/src\/index\.ts$|\/credentials-store\.[jt]s$|\/node_modules\/(?:[^/]+\/node_modules\/)?(?:cac|prompts)\//.test(path))) {
    throw new Error('Native core includes a CLI command, credential reader, or interactive dependency');
  }
  await mkdir(join(root, 'lib'), { recursive: true });
  await writeFile(join(root, 'lib/local-core.js'), await result.outputs[0]!.text());
  const declarations = join(root, 'src/host/generated');
  await mkdir(declarations, { recursive: true });
  await cp(join(cli, 'src/integrations/native-contract.d.ts'), join(declarations, 'native-contract.d.ts'));
  await mkdir(join(root, 'lib/types/host/generated'), { recursive: true });
  await cp(join(declarations, 'native-contract.d.ts'), join(root, 'lib/types/host/generated/native-contract.d.ts'));
}
