import { randomUUID } from 'node:crypto';
import assert from 'node:assert/strict';
import { mkdir, realpath, readFile, writeFile } from 'node:fs/promises';
import { join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';
import { tmpdir } from 'node:os';
export const inject = ['sessionController', 'workspaceRegistry', 'tools', 'sessions', 'skillhub', 'clientModules', 'typertGateway'];
export function apply(ctx, config) {
 const root=resolve(config.root);
 assert(root.startsWith(resolve(tmpdir())+sep));
 assert(fileURLToPath(import.meta.url).startsWith(resolve(tmpdir())+sep), 'Load observer from outside the package');
 assert.equal(process.env.DSH_HOME,join(root,'home'));
 const abort=new AbortController();
 const timer=setTimeout(()=>run(ctx,root,abort.signal).catch(error=>writeFile(join(root,'upgrade-drift-result.json'),JSON.stringify({error:error.message}))),1000);
 return ()=>{clearTimeout(timer);abort.abort();};
}
async function run(ctx,root,signal) {
 assert.equal(await realpath(ctx.clientModules.clientPath('@astron-team/skillhub-dsh')),await realpath(join(root,'home/profiles/skillhub-m0/node_modules/@astron-team/skillhub-dsh/lib/client.js')));
 const project=join(root,'conversation-install-'+randomUUID());
 await mkdir(project);
 const workspace=await ctx.workspaceRegistry.create(project);
 const created=await ctx.sessionController.create({workspaceId:workspace.id,agentPreset:'standard'});
 const resolved=await ctx.sessionController.resolveAgent(created.sessionId);
 if ('error' in resolved) throw resolved.error;
 const toolCallId='history-'+randomUUID();
 const toolArguments={namespace:process.env.DSH_FIXTURE_NAMESPACE ?? 'demo',slug:'document-helper',scope:'project'};
 const toolResult=await ctx.tools.execute({callId:toolCallId,name:'astron_skillhub_install',arguments:toolArguments,agent:resolved.agent,signal});
 assert.equal(toolResult.isError,false);
 assert.equal(toolResult.value.status,'accepted');
 const deadline=Date.now()+15000;
 let operation;
 while(Date.now()<deadline){
  operation=await ctx.skillhub.getInstallationOperation(created.sessionId,toolResult.value.operationId);
  if(['completed','failed','unknown','recovery-required'].includes(operation?.phase))break;
  await delay(100,undefined,{signal});
 }
 assert.equal(operation?.phase,'completed');

 const source=ctx.skillhub.getContext();
 const invoke=(method,args)=>ctx.typertGateway.invoke({namespace:'skillhub',method,args:{agentId:created.sessionId,...args},signal});
 const installed=await invoke('listInstalled',{sourceRevision:source.sourceRevision});
 const item=installed.items.find(item=>item.namespace===toolArguments.namespace && item.scope==='project');
 assert(item);
 const state=process.env.DSH_FIXTURE_UPDATE_STATE;
 assert(state && resolve(state).startsWith(root+sep));
 await writeFile(state,'newer');
 const plan=await invoke('prepareUpgrade',{sourceRevision:source.sourceRevision,itemKey:item.itemKey});
 assert.equal(plan.status,'ready');
 const runtime=ctx.skillhub.localRuntime;
 const originalSubmit=runtime.submitUpgrade;
 let injected=false;
 runtime.submitUpgrade=function(request,prepare){
  return originalSubmit.call(this,request,async()=>{
   const prepared=await prepare();
   assert.deepEqual(prepared.acceptance.targetPaths,[join(project,'.dsh/skills/document-helper')]);
   const validate=prepared.execution.validateStaged;
   return {...prepared,execution:{...prepared.execution,validateStaged:async directory=>{
    await validate(directory);
    await writeFile(join(project,'.dsh/skills/document-helper/references/note.md'),'Preserved concurrent edit');
    injected=true;
   }}};
  });
 };
 try {
  const receipt=await invoke('executePlan',{input:{planId:plan.planId,requestKey:randomUUID()}});
  assert(receipt);
  const end=Date.now()+15000;
  let outcome;
  while(Date.now()<end){
   outcome=await ctx.skillhub.getInstallationOperation(created.sessionId,receipt.operationId);
   if(['completed','failed','unknown','recovery-required'].includes(outcome.phase))break;
   await delay(100,undefined,{signal});
  }
  assert(injected);
  assert.equal(outcome.phase,'failed');assert.equal(outcome.recovery,'not-needed');
  assert.equal(await readFile(join(project,'.dsh/skills/document-helper/references/note.md'),'utf8'),'Preserved concurrent edit');
  const metadata=JSON.parse(await readFile(join(project,'.dsh/skills/document-helper/.skillhub/metadata.json'),'utf8'));
  assert.equal(metadata.version,'1.2.0');
  await writeFile(join(root,'upgrade-drift-result.json'),JSON.stringify({passed:true,installedClientVerified:true,
   stagedDriftRejected:true,oldVersionPreserved:true,concurrentEditPreserved:true,noRecoveryRequired:true,modelInvoked:false},null,2));
 } finally {runtime.submitUpgrade=originalSubmit;await writeFile(state,'current');}
}
