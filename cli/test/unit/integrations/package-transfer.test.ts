import { expect, test } from 'bun:test'
import { randomUUID } from 'node:crypto'
import { mkdtemp, realpath, rm, readFile, rename, writeFile, readdir } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { zipSync, unzipSync } from 'fflate'
import { openNativeRuntime } from '../../../src/integrations/native-runtime'
import { previewPackage } from '../../../src/integrations/package-transfer'
import { InventoryStore } from '../../../src/stores/inventory-store'

test('local import roundtrip preserves resources, isolates shared inventory and survives restart', async () => {
  const home = await realpath(await mkdtemp(join(tmpdir(), 'skillhub-transfer-')))
  try {
    const options = {stateDirectory: join(home, 'operations'), inventoryHome: join(home, 'shared')}
    let runtime = await openNativeRuntime(options)
    const files = {'SKILL.md': new TextEncoder().encode('---\nname: demo\ndescription: Test\n---\nHello'), 'asset.bin': new Uint8Array([0, 255, 1]), '.skillhub/examples.json': new TextEncoder().encode('{"example":"案例"}')}
    const archive = zipSync(files)
    const preview = await runtime.previewPackage(archive, async () => ({name: 'demo'}))
    const root = join(home, 'skills')
    const acceptance = {namespace: 'local', slug: 'demo', version: 'local', targetLabel: 'Project', targetPaths: [join(root,'demo')], context: {sourceIdentity: 'local:import', sourceLabel: 'Local', sourceRevision: 'r', hostLabel: 'Host', initiatedFrom: 'plaza' as const}}
    const request = {hostInstanceId: runtime.hostInstanceId, ownerId: 'session', requestKey: randomUUID(), mode: 'new' as const, parameters: {token: randomUUID()}}
    const accepted = (await runtime.acceptInstall(request, async () => acceptance))!
    const result = await runtime.executePackageImport({operationId: accepted.operationId, ownerId: 'session', registry: 'local:import', namespace: 'local', slug: 'demo', version: 'local', versionId: 1, fingerprint: preview.fingerprint, root, scope: 'project', archive, authorize: async () => {}, validateStaged: async () => {}})
    expect(result.outcome).toBe('committed')
    expect((await readdir(options.stateDirectory)).filter(path => path.endsWith('.json'))).not.toContain(`${accepted.operationId}.json`)
    expect((await readdir(join(options.stateDirectory,'activation')).catch(() => [])).filter(path=>path.endsWith('.json'))).not.toContain(`${accepted.operationId}.json`)
    expect(JSON.parse(await readFile(join(options.stateDirectory,'local-packages',`${accepted.operationId}.json`),'utf8')).schemaVersion).toBe(3)
    expect((await new InventoryStore(options.inventoryHome).read()).items).toEqual([])
    runtime = await openNativeRuntime(options)
    const target = {root, scope: 'project' as const}
    expect((await runtime.listAllInstalled([target])).items[0]?.registry).toBe('local:import')
    expect(await runtime.observeInstallation(accepted.operationId, 'session', [root], async () => true)).toBe(true)
    const recovered = await runtime.recover(accepted.operationId, 'session', [join(root, 'demo')], async () => {})
    expect(recovered.outcome).toBe('committed')
    const duplicate = (await runtime.acceptInstall({...request, hostInstanceId: runtime.hostInstanceId, requestKey: randomUUID()}, async () => acceptance))!
    await expect(runtime.executePackageImport({operationId:duplicate.operationId,ownerId:'session',registry:'local:import',namespace:'local',slug:'demo',version:'local',versionId:1,fingerprint:preview.fingerprint,root,scope:'project',archive,authorize:async()=>{},validateStaged:async()=>{}})).rejects.toThrow()
    const selection = {registry:'local:import',namespace:'local',slug:'demo',version:'local'}
    await writeFile(join(root,'demo/SKILL.md'), 'locally edited')
    expect(await runtime.readInstallationEntry(selection,target)).toBe('locally edited')
    const exported = unzipSync((await runtime.exportPackage(selection,target)).archive)
    expect(new TextDecoder().decode(exported['SKILL.md'])).toBe('locally edited')
    expect(exported['asset.bin']).toEqual(files['asset.bin'])
    expect(exported['.skillhub/examples.json']).toEqual(files['.skillhub/examples.json'])
    expect(exported['.skillhub/metadata.json']).toBeUndefined()
    await rename(join(root,'demo/SKILL.md'),join(root,'demo/SKILL.md.disabled'))
    expect(unzipSync((await runtime.exportPackage(selection,target)).archive)['SKILL.md']).toBeDefined()
    expect(await readFile(join(root,'demo/SKILL.md.disabled'),'utf8')).toBe('locally edited')
  } finally {await rm(home,{recursive:true,force:true})}
})

test('preview rejects internal metadata and traversal without invoking validation', async () => {
  for (const path of ['../escape', '.skillhub/metadata.json', '/absolute']) {
    let called = false
    await expect(previewPackage(zipSync({'SKILL.md':new Uint8Array([1]),[path]:new Uint8Array([2])}),async()=>{called=true;return {name:'demo'}})).rejects.toThrow()
    expect(called).toBe(false)
  }
})


test('preview rejects Unix symlinks and encrypted ZIP flags', async () => {
  for (const symlink of [true, false]) {
    const archive = zipSync({'SKILL.md':new Uint8Array([1])})
    const view = new DataView(archive.buffer, archive.byteOffset, archive.byteLength)
    for (let i = 0; i + 46 <= archive.length; i++) {
      if (view.getUint32(i, true) !== 0x02014b50) continue
      if (symlink) view.setUint32(i + 38, 0xa1ff0000, true)
      else view.setUint16(i + 8, view.getUint16(i + 8, true) | 1, true)
      break
    }
    await expect(previewPackage(archive, async () => ({name:'demo'}))).rejects.toThrow('Unsupported zip entry type')
  }
})

test('local package seams reject market identities and forged metadata source', async () => {
  const {installSkill} = await import('../../../src/services/install-service')
  await expect(installSkill({registry:'https://example.test',namespace:'team',slug:'demo',version:'1',force:false,targets:[],packageBytes:new Uint8Array([1]),metadataSource:'local-import'})).rejects.toThrow('Invalid local package identity')
  await expect(installSkill({registry:'local:import',namespace:'team',slug:'demo',version:'local',force:false,targets:[],packageBytes:new Uint8Array([1]),metadataSource:'local-import'})).rejects.toThrow('Invalid local package identity')
})

test('stale shared inventory claims prevent local import without modifying either source',async()=>{
  const home=await realpath(await mkdtemp(join(tmpdir(),'skillhub-import-stale-')))
  try {
    const options={stateDirectory:join(home,'operations'),inventoryHome:join(home,'shared')}
    const runtime=await openNativeRuntime(options)
    const root=join(home,'skills')
    const shared=new InventoryStore(options.inventoryHome)
    await shared.upsertTarget('https://registry.example','team','demo','1.0.0',{agent:'dsh',rootDir:root,installDir:join(root,'demo'),installedAt:new Date().toISOString()})
    const before=await readFile(shared.path)
    const archive=zipSync({'SKILL.md':new TextEncoder().encode('---\nname: demo\ndescription: demo\n---\nhello')})
    const preview=await runtime.previewPackage(archive,async()=>({name:'demo'}))
    const accepted=(await runtime.acceptInstall({hostInstanceId:runtime.hostInstanceId,ownerId:'session',requestKey:randomUUID(),mode:'new',parameters:{preview:'stale'}},async()=>({namespace:'local',slug:'demo',version:'local',targetLabel:'Project',targetPaths:[join(root,'demo')],context:{sourceIdentity:'local:import',sourceLabel:'Local',sourceRevision:'r',hostLabel:'Host',initiatedFrom:'plaza'}})))!
    await expect(runtime.executePackageImport({operationId:accepted.operationId,ownerId:'session',registry:'local:import',namespace:'local',slug:'demo',version:'local',versionId:1,fingerprint:preview.fingerprint,root,scope:'project',archive,authorize:async()=>{},validateStaged:async()=>{}})).rejects.toThrow('Registry installation record')
    expect(await readFile(shared.path)).toEqual(before)
    await expect(readFile(join(root,'demo/SKILL.md'))).rejects.toMatchObject({code:'ENOENT'})
    expect((await new InventoryStore(join(options.stateDirectory,'local-imports')).read()).items).toEqual([])
  }finally{await rm(home,{recursive:true,force:true})}
})

test('native Registry install rejects a stale private local claim before network access',async()=>{
  const home=await realpath(await mkdtemp(join(tmpdir(),'skillhub-market-stale-')))
  try {
    const options={stateDirectory:join(home,'operations'),inventoryHome:join(home,'shared')}
    const runtime=await openNativeRuntime(options)
    const root=join(home,'skills')
    const local=new InventoryStore(join(options.stateDirectory,'local-imports'))
    await local.upsertTarget('local:import','local','demo','local',{agent:'dsh',rootDir:root,installDir:join(root,'demo'),installedAt:new Date().toISOString()})
    const before=await readFile(local.path)
    const accepted=(await runtime.acceptInstall({hostInstanceId:runtime.hostInstanceId,ownerId:'session',requestKey:randomUUID(),mode:'new',parameters:{preview:'market'}},async()=>({namespace:'team',slug:'demo',version:'1.0.0',targetLabel:'Project',targetPaths:[join(root,'demo')],context:{sourceIdentity:'https://not-contacted.invalid',sourceLabel:'Market',sourceRevision:'r',hostLabel:'Host',initiatedFrom:'plaza'}})))!
    await expect(runtime.executeInstall({operationId:accepted.operationId,ownerId:'session',registry:'https://not-contacted.invalid',namespace:'team',slug:'demo',version:'1.0.0',versionId:1,fingerprint:'sha256:unused',root,scope:'project',authorize:async()=>{},validateStaged:async()=>{}})).rejects.toThrow('local installation record')
    expect(await readFile(local.path)).toEqual(before)
    await expect(readFile(join(root,'demo/SKILL.md'))).rejects.toMatchObject({code:'ENOENT'})
    expect((await new InventoryStore(options.inventoryHome).read()).items).toEqual([])
  }finally{await rm(home,{recursive:true,force:true})}
})
