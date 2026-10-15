import { randomUUID } from 'node:crypto';
import assert from 'node:assert/strict';
import { mkdir, readdir, realpath, readFile, writeFile } from 'node:fs/promises';
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
 const timer=setTimeout(()=>run(ctx,root,abort.signal).catch(error=>writeFile(join(root,'plan-context-result.json'),JSON.stringify({error:error.message}))),1000);
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
 const journalDirectory=join(root,'home/skillhub/operations');
 const records=async()=> (await readdir(journalDirectory)).filter(name=>name.endsWith('.json')).sort();
 const before=await records();
 const foreign=await ctx.sessionController.create({workspaceId:workspace.id,agentPreset:'standard'});
 const foreignAgent=await ctx.sessionController.resolveAgent(foreign.sessionId);
 if ('error' in foreignAgent) throw foreignAgent.error;
 const plans=[{method:'prepareUpgrade',store:ctx.skillhub.upgradePlans},{method:'prepareUninstall',store:ctx.skillhub.removalPlans}];
 const checks=[];
 try {
  await writeFile(state,'newer');
  for(const {method,store} of plans){
   const plan=await invoke(method,{sourceRevision:source.sourceRevision,itemKey:item.itemKey});
   assert.equal(plan.status,'ready');
   await assert.rejects(invoke('executePlan',{agentId:foreign.sessionId,input:{planId:plan.planId,requestKey:randomUUID()}}),
    error=>error.code==='skillhub/context-stale');
   // Change only this confirmation store's clock, not the Host or operation timestamps.
   const originalNow=store.now;
   store.now=()=>Date.parse(plan.expiresAt);
   try {
    await assert.rejects(invoke('executePlan',{input:{planId:plan.planId,requestKey:randomUUID()}}),
     error=>error.code==='skillhub/context-stale');
   } finally {store.now=originalNow;}
   assert.deepEqual(await records(),before);
   checks.push({method,foreignSessionRejected:true,exactExpiryRejected:true,noOperationAccepted:true});
  }
  const plan=await invoke('prepareUpgrade',{sourceRevision:source.sourceRevision,itemKey:item.itemKey});
  assert.equal(plan.status,'ready');
  const request={planId:plan.planId,requestKey:randomUUID()};
  const accepted=await invoke('executePlan',{input:request});assert(accepted);
  const store=ctx.skillhub.upgradePlans;const originalNow=store.now;
  store.now=()=>Date.parse(plan.expiresAt)+1;
  try {
   const repeated=await invoke('executePlan',{input:request});
   assert.equal(repeated.operationId,accepted.operationId);
   await assert.rejects(invoke('executePlan',{input:{...request,requestKey:randomUUID()}}),
    error=>error.code==='skillhub/context-stale');
  } finally {store.now=originalNow;}
  const end=Date.now()+15000;
  let outcome;
  while(Date.now()<end){
   outcome=await ctx.skillhub.getInstallationOperation(created.sessionId,accepted.operationId);
   if(['completed','failed','unknown','recovery-required'].includes(outcome.phase))break;
   await delay(100,undefined,{signal});
  }
  assert.equal(outcome.phase,'completed');
  const after=await records();assert.equal(after.length,before.length+1);
  const requests=(await readFile(process.env.DSH_FIXTURE_REQUEST_LOG,'utf8')).trim().split('\n').map(line=>JSON.parse(line));
  assert.deepEqual(requests.filter(request=>request.path==='/download').map(request=>request.version),['1.2.0','1.3.0']);
  await writeFile(join(root,'plan-context-result.json'),JSON.stringify({passed:true,checks,
   acceptedRetryAfterExpirySameOperation:true,newKeyAfterExpiryRejected:true,upgradeCompleted:true,
   downloadCount:2,clockInjection:'confirmation-store-only',modelInvoked:false},null,2));
 } finally {await writeFile(state,'current');}
}
