import {expect,test} from 'bun:test'
import {mkdtemp,realpath,readFile,readdir,rm} from 'node:fs/promises'
import {tmpdir} from 'node:os'
import {join} from 'node:path'
import {fileURLToPath} from 'node:url'
import {openNativeRuntime} from '../../../src/integrations/native-runtime'
import {InventoryStore} from '../../../src/stores/inventory-store'

test.skipIf(process.platform === 'win32')('killed local import restores private transactions before and after target publication', async()=>{
  const roots: string[]=[]
  try {
    const cases = await Promise.all(['prepared','after-install'].map(async boundary=>{
      const root=await realpath(await mkdtemp(join(tmpdir(),'skillhub-import-kill-')));roots.push(root)
      const child=Bun.spawn([process.execPath,fileURLToPath(new URL('../../fixtures/package-import-crash-child.ts',import.meta.url)),root,boundary],{stdout:'pipe',stderr:'pipe'})
      expect(await child.exited).not.toBe(0)
      expect(await new Response(child.stderr).text()).toBe('')
      const fixture=JSON.parse(await readFile(join(root,'fixture.json'),'utf8')) as {operationId:string;target:string}
      if(boundary==='after-install')expect(await readFile(join(fixture.target,'SKILL.md'),'utf8')).toContain('Local crash fixture')
      return {root,...fixture}
    }))
    // Proper-lockfile target and inventory leases expire after the killed process.
    await Bun.sleep(31_500)
    for(const fixture of cases){
      const options={stateDirectory:join(fixture.root,'operations'),inventoryHome:join(fixture.root,'shared')}
      const runtime=await openNativeRuntime(options)
      expect((await runtime.listPending()).map(item=>item.operationId)).toContain(fixture.operationId)
      expect((await runtime.recover(fixture.operationId,'session',[fixture.target],async()=>{})).outcome).toBe('restored')
      expect(await readdir(join(fixture.root,'skills'))).toEqual([])
      expect((await new InventoryStore(options.inventoryHome).read()).items).toEqual([])
      expect((await new InventoryStore(join(options.stateDirectory,'local-imports')).read()).items).toEqual([])
      expect(await runtime.listPending()).toEqual([])
      expect((await runtime.recover(fixture.operationId,'session',[fixture.target],async()=>{})).outcome).toBe('restored')
    }
  }finally{await Promise.all(roots.map(root=>rm(root,{recursive:true,force:true})))}
},60_000)
