import { randomUUID } from 'node:crypto';
import assert from 'node:assert/strict';
import { mkdir, readdir, realpath, readFile, writeFile } from 'node:fs/promises';
import { join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';
import { tmpdir } from 'node:os';
export const inject = ['loader', 'sessionController', 'workspaceRegistry', 'tools', 'sessions', 'skillhub', 'clientModules', 'typertGateway'];
export function apply(ctx, config) {
 const root=resolve(config.root);
 assert(root.startsWith(resolve(tmpdir())+sep));
 assert(fileURLToPath(import.meta.url).startsWith(resolve(tmpdir())+sep), 'Load observer from outside the package');
 assert.equal(process.env.DSH_HOME,join(root,'home'));
 const abort=new AbortController();
 const timer=setTimeout(()=>run(ctx,root,abort.signal).catch(error=>writeFile(join(root,'plan-source-result.json'),JSON.stringify({error:error.message}))),1000);
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
 const records=async()=> (await readdir(join(root,'home/skillhub/operations'))).filter(name=>name.endsWith('.json')).sort();
 const before=await records();
 const otherProject=join(root,'other-project-'+randomUUID());await mkdir(otherProject);
 await assert.rejects(ctx.sessionController.create({sessionId:created.sessionId,cwd:otherProject,agentPreset:'standard'}),
  error=>error.code==='session/conflict');
 assert.equal((await ctx.sessionController.resolveAgent(created.sessionId)).agent.session.header.cwd,project);
 const entries=[...ctx.loader.entries()].filter(entry=>entry.options.name==='@astron-team/skillhub-dsh');
 assert.equal(entries.length,1);const entry=entries[0];
 const originalConfig=structuredClone(entry.options.config);
 const plans=[];
 try {
  await writeFile(state,'newer');
  for(const method of ['prepareUpgrade','prepareUninstall']){
   const plan=await invoke(method,{sourceRevision:source.sourceRevision,itemKey:item.itemKey});
   assert.equal(plan.status,'ready');plans.push(plan);
  }
  await entry.update({config:{...originalConfig,registryUrl:'http://127.0.0.1:43220'}});
  const switched=ctx.skillhub.getContext();
  assert.equal(switched.registryUrl,'http://127.0.0.1:43220');
  assert.notEqual(switched.sourceRevision,source.sourceRevision);
  for(const plan of plans) await assert.rejects(invoke('executePlan',{input:{planId:plan.planId,requestKey:randomUUID()}}),
   error=>error.code==='skillhub/context-stale');
  assert.deepEqual(await records(),before);
  await entry.update({config:originalConfig});
  const restored=ctx.skillhub.getContext();
  assert.equal(restored.registryUrl,source.registryUrl);
  assert.notEqual(restored.sourceRevision,source.sourceRevision);
  for(const plan of plans) await assert.rejects(invoke('executePlan',{input:{planId:plan.planId,requestKey:randomUUID()}}),
   error=>error.code==='skillhub/context-stale');
  assert.deepEqual(await records(),before);
  const metadata=JSON.parse(await readFile(join(project,'.dsh/skills/document-helper/.skillhub/metadata.json'),'utf8'));
  assert.equal(metadata.version,'1.2.0');
  const requests=(await readFile(process.env.DSH_FIXTURE_REQUEST_LOG,'utf8')).trim().split('\n').map(line=>JSON.parse(line));
  assert.equal(requests.filter(request=>request.path==='/download').length,1);
  await writeFile(join(root,'plan-source-result.json'),JSON.stringify({passed:true,sourceSwitchRejectedBothPlans:true,
   sourceRestoreDoesNotRevivePlans:true,noOperationAccepted:true,onlyInitialDownload:true,
   sameSessionProjectChangeRejectedByHost:true,originalProjectPreserved:true,
   modelInvoked:false},null,2));
 } finally {await entry.update({config:originalConfig});await writeFile(state,'current');}
}
