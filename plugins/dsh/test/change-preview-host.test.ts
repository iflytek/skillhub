import { expect, test } from 'bun:test';
import { SkillHubService } from '../src/host/index.js';
import { ConfirmationPlans, UpgradePlans } from '../src/host/upgrade-plans.js';
import type { LocalChangesSnapshot } from '../src/host/generated/native-contract.js';
const context = { sessionId: 'session', cwd: '/project', sourceRevision: 'source' };
const local: Extract<LocalChangesSnapshot, {status: 'ready'}> = {
  status: 'ready', kind: 'local', selection: {registry: 'https://registry.example', namespace: 'demo', slug: 'writer', version: '1.0.0'}, target: {scope: 'project', root: '/project/.dsh/skills'},
  installationState: 'enabled', digest: 'original', changes: [{path: 'SKILL.md', kind: 'modified'}, {path: 'added.md', kind: 'added'}],
  baseline: { status: 'ready', inventoryItem: {registry: 'https://registry.example',namespace: 'demo',slug: 'writer',targets: []}, targets: [] },
};
function fixture() {
  const state = { context: {...context}, changed: false, invalid: false, reads: 0 };
  const changes = new ConfirmationPlans<{itemKey: string; local: typeof local}>();
  const reference = changes.issue(context, {itemKey: 'item',local});
  const service = Object.assign(Object.create(SkillHubService.prototype), {
    changes, resets: new ConfirmationPlans(), upgradePlans: new UpgradePlans(),
    selectionScope: () => state.context,
    readInstalledItem: async () => ({ item: {version: '1.0.0'}, agent: {}, valid: () => { if(state.invalid) throw new Error('Context changed'); }, runtime: {
      getStatus: () => ({hostInstanceId: 'host'}), inspectChanges: async () => state.changed ? {...local,digest:'changed'} : local,
      readChangeDiff: async (_snapshot: unknown,path:string) => {state.reads++;return {path,state:'text',lines:[{kind:'added',text:'new'}],truncated:false};},
    }}),
  });
  const signal = new AbortController().signal;
  return {service,state,signal,id: reference.planId};
}
test('file diff accepts only snapshot listed logical paths and matching installation reference', async()=>{
 const f=fixture();
 expect(await f.service.readFileChange('source','item',f.id,'SKILL.md',f.signal)).toMatchObject({path:'SKILL.md',state:'text'});
 await expect(f.service.readFileChange('source','item',f.id,'../../private',f.signal)).rejects.toThrow();
 await expect(f.service.readFileChange('source','other',f.id,'SKILL.md',f.signal)).rejects.toThrow();
 expect(f.state.reads).toBe(1);
});
test('source or session replacement invalidates old file snapshots',async()=>{
 for(const replacement of [{sourceRevision:'new'}, {sessionId:'other'}, {cwd:'/elsewhere'}]){
  const f=fixture();Object.assign(f.state.context,replacement);
  await expect(f.service.readFileChange('source','item',f.id,'SKILL.md',f.signal)).rejects.toThrow();
  expect(f.state.reads).toBe(0);
 }
});
test('late file reads revalidate their context', async()=>{const f=fixture();f.state.invalid=true;await expect(f.service.readFileChange('source','item',f.id,'SKILL.md',f.signal)).rejects.toThrow();});
test('reset confirmation fixes exact paths and refuses duplicates and unknown paths',async()=>{
 const f=fixture();const plan=await f.service.prepareFileReset('source','item',f.id,['added.md'],f.signal);
 expect(plan.paths).toEqual([{path:'added.md',kind:'added'}]);
 for(const paths of [[],['SKILL.md','SKILL.md'],['other.md']]) await expect(f.service.prepareFileReset('source','item',f.id,paths,f.signal)).rejects.toThrow();
});
test('reset preparation rejects local snapshot drift rather than expanding authorization',async()=>{const f=fixture();f.state.changed=true;await expect(f.service.prepareFileReset('source','item',f.id,['SKILL.md'],f.signal)).rejects.toThrow();});
