import { expect, test } from 'bun:test';
import { mkdir, mkdtemp, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { hostDependencyBaseline } from '../src/host/compatibility.js';

test('Node package resolution rejects missing, corrupt, old and inaccessible manifests', async () => {
  const root = await mkdtemp(join(tmpdir(), 'skillhub-dsh-compatibility-'));
  try {
    const built = await Bun.build({ entrypoints: [fileURLToPath(new URL('../src/host/compatibility.ts', import.meta.url))], target: 'node', format: 'esm' });
    expect(built.success).toBe(true);
    await writeFile(join(root, 'compatibility.mjs'), await built.outputs[0]!.text());
    await writeFile(join(root, 'probe.mjs'), `
import assert from 'node:assert/strict';
import { assertHostDependencies } from './compatibility.mjs';
if (process.argv[2] === 'matched') assertHostDependencies();
else assert.throws(() => assertHostDependencies(), error => error.message === process.argv[2] && !error.message.includes(process.cwd()));
console.log('verified');
`);
    const path = (name: string) => join(root, 'node_modules', name, 'package.json');
    for (const [name, version] of Object.entries(hostDependencyBaseline)) { await mkdir(dirname(path(name)), { recursive: true }); await writeFile(path(name), JSON.stringify({ name, version })); }
    const probe = async (expected: string) => {
      const child = Bun.spawn(['node', 'probe.mjs', expected], { cwd: root, stdout: 'pipe', stderr: 'pipe', env: { ...process.env, NODE_PATH: '' } });
      const [stdout, stderr, code] = await Promise.all([new Response(child.stdout).text(), new Response(child.stderr).text(), child.exited]);
      expect(code, stderr).toBe(0); expect(stdout).toBe('verified\n');
    };
    await probe('matched');
    for (const [name, version] of Object.entries(hostDependencyBaseline)) {
      await writeFile(path(name), JSON.stringify({ name, version: '0.0.1' })); await probe('Host dependency baseline does not match; local writes are disabled.');
      await writeFile(path(name), '{broken'); await probe('Host dependency baseline could not be verified; local writes are disabled.');
      await rm(path(name)); await probe('Host dependency baseline could not be verified; local writes are disabled.');
      await writeFile(path(name), JSON.stringify({ name, version }));
    }
    for (const [name] of Object.entries(hostDependencyBaseline)) await writeFile(path(name), JSON.stringify({ name, version: name === '@deepseek-ai/cordis' ? '4.2.0' : '0.2.2-rc.5' }));
    await probe('matched');
  } finally { await rm(root, { recursive: true, force: true }); }
}, 30_000);

test('real Node admits future coherent dependency graphs and rejects ownership mismatches', async () => {
  const root = await mkdtemp(join(tmpdir(), 'skillhub-runtime-compatibility-'));
  try {
    const built = await Bun.build({ entrypoints: [fileURLToPath(new URL('../src/host/runtime-compatibility.ts', import.meta.url))], target: 'node', format: 'esm' });
    expect(built.success).toBe(true);
    await writeFile(join(root, 'runtime.mjs'), await built.outputs[0]!.text());
    await writeFile(join(root, 'probe.mjs'), `
import assert from 'node:assert/strict';
import { assertRuntimeCompatibility, runtimePackageBaseline, clientPackageBaseline } from './runtime.mjs';
for (const release of ['0.2.2-rc.3','0.2.2-rc.4','0.2.2-rc.5','0.3.0','0.2.2-rc.3+desktop.1']) {
 const packages=new Map(Object.entries(runtimePackageBaseline).map(([name,version])=>[name,{name,version:name.startsWith('@deepseek-ai/dsh')?release:version,manifestPath:'/fixture/'+name+'/package.json'}]));
 for(const name of clientPackageBaseline)packages.set(name,{name,version:release,manifestPath:'/fixture/'+name+'/package.json'});
 packages.get('@deepseek-ai/dsh').dependencies=Object.fromEntries([...packages].filter(([name])=>name!=='@deepseek-ai/dsh').map(([name,v])=>[name,v.version]));
 const check=()=>assertRuntimeCompatibility({packageOf:name=>packages.get(name),clientPath:name=>'/fixture/'+name+'/lib/client.js',hasAgentLookup:()=>true},'file:///plugin/index.js',(_file,name)=>packages.get(name),path=>[...packages.values()].find(v=>v.manifestPath===path));
 const [major,minor]=process.versions.node.split('.').map(Number);
 if(major<24 || major===24&&minor<9){assert.throws(check,/local writes are disabled/);continue;}
 assert.equal(check(),release);
 packages.get('@deepseek-ai/dsh-fs').version='0.2.1-rc.5';assert.throws(check,/local writes are disabled/);
}
console.log('verified on Node '+process.versions.node);
`);
    const child = Bun.spawn(['node', 'probe.mjs'], { cwd: root, stdout: 'pipe', stderr: 'pipe' });
    const [stdout, stderr, code] = await Promise.all([new Response(child.stdout).text(), new Response(child.stderr).text(), child.exited]);
    expect(code, stderr).toBe(0); expect(stdout).toContain('verified on Node ');
  } finally { await rm(root, { recursive: true, force: true }); }
});
