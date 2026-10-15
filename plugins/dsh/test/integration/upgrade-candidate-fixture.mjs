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
 const timer=setTimeout(()=>run(ctx,root,abort.signal).catch(error=>writeFile(join(root,'upgrade-candidate-result.json'),JSON.stringify({error:error.message}))),1000);
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
 const downloadState=process.env.DSH_FIXTURE_DOWNLOAD_STATE;
 assert(downloadState && resolve(downloadState).startsWith(root+sep));
 const outcomes=[];
 try {
  for (const mode of ['resolve-fingerprint','resolve-version','tampered','missing','normal']) {
   await writeFile(state,'newer');
   const plan=await invoke('prepareUpgrade',{sourceRevision:source.sourceRevision,itemKey:item.itemKey});
   assert.equal(plan.status,'ready');assert.equal(plan.candidateVersion,'1.3.0');
   // The latest release changes after confirmation; the accepted plan must remain exact.
   await writeFile(state,'older');await writeFile(downloadState,mode);
   const receipt=await invoke('executePlan',{input:{planId:plan.planId,requestKey:randomUUID()}});
   assert(receipt);
   const end=Date.now()+15000;
   let outcome;
   while(Date.now()<end){
    outcome=await ctx.skillhub.getInstallationOperation(created.sessionId,receipt.operationId);
    if(['completed','failed','unknown','recovery-required'].includes(outcome.phase))break;
    await delay(100,undefined,{signal});
   }
   assert.equal(outcome.phase,mode==='normal'?'completed':'failed');
   assert.equal(outcome.recovery,'not-needed');
   const metadata=JSON.parse(await readFile(join(project,'.dsh/skills/document-helper/.skillhub/metadata.json'),'utf8'));
   assert.equal(metadata.version,mode==='normal'?'1.3.0':'1.2.0');
   assert.equal(await readFile(join(project,'.dsh/skills/document-helper/references/note.md'),'utf8'),
    mode==='normal'?'Native upgraded resource 1.3.0':'Native installation resource');
   outcomes.push({mode,phase:outcome.phase,version:metadata.version});
  }
  const requests=(await readFile(process.env.DSH_FIXTURE_REQUEST_LOG,'utf8')).trim().split('\n').map(line=>JSON.parse(line));
  const downloads=requests.filter(request=>request.path==='/download');
  assert.deepEqual(downloads.map(request=>request.version),['1.2.0','1.3.0','1.3.0','1.3.0']);
  await writeFile(join(root,'upgrade-candidate-result.json'),JSON.stringify({passed:true,installedClientVerified:true,
   outcomes,downloadVersions:downloads.map(request=>request.version),modelInvoked:false},null,2));
 } finally {await writeFile(state,'current');await writeFile(downloadState,'normal');}
}
