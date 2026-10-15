// Host observer: install through the plugin and verify same-session native discovery. No model invocation.
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { mkdir, readFile, realpath, writeFile } from 'node:fs/promises';
import { join, resolve, sep } from 'node:path';
import { tmpdir } from 'node:os';
import { fileURLToPath } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';
export const inject=['sessionController','workspaceRegistry','skills','typertGateway','skillhub','tools','clientModules'];
export function apply(ctx,config){
 const root=resolve(config.root);
 assert(root.startsWith(resolve(tmpdir())+sep), 'Use an isolated temporary root');
 assert(fileURLToPath(import.meta.url).startsWith(resolve(tmpdir())+sep), 'Copy observer outside the plugin package before loading');
 assert.equal(process.env.DSH_HOME,join(root,'home'));
 const abort=new AbortController();
 const timer=setTimeout(()=>run(ctx,root,abort.signal).then(result=>writeFile(join(root,'installed-discovery-result.json'),JSON.stringify({passed:true,...result},null,2)),error=>writeFile(join(root,'installed-discovery-result.json'),JSON.stringify({passed:false,error:error.message},null,2))),1000);
 return ()=>{clearTimeout(timer);abort.abort();};
}
async function run(ctx,root,signal){
 const client=await realpath(ctx.clientModules.clientPath('@astron-team/skillhub-dsh'));
 assert.equal(client,await realpath(join(root,'home/profiles/skillhub-m0/node_modules/@astron-team/skillhub-dsh/lib/client.js')));
 const project=join(root,'discovery-'+randomUUID());await mkdir(project);
 const workspace=await ctx.workspaceRegistry.create(project);
 const created=await ctx.sessionController.create({workspaceId:workspace.id,agentPreset:'standard'});
 const resolved=await ctx.sessionController.resolveAgent(created.sessionId);if('error' in resolved)throw resolved.error;
 const options={scope:resolved.agent,cwd:project,signal};
 const initial=await ctx.skills.snapshot(options);assert(initial.complete);assert(!initial.skills.some(s=>s.name==='document-helper'));
 const found=await ctx.tools.execute({callId:randomUUID(),name:'astron_skillhub_search',arguments:{q:'document'},agent:resolved.agent,signal});assert.equal(found.isError,false);
 const invoke=(method,args)=>ctx.typertGateway.invoke({namespace:'skillhub',method,args:{agentId:created.sessionId,...args},signal});
 const detail=await invoke('openConversationDetail',{selectionRef:found.value.items[0].selectionRef});
 const targets=await invoke('listInstallTargets',{detailToken:detail.detailToken});
 const target=targets.targets.find(t=>t.scope==='project');assert(target);
 const receipt=await invoke('startInstall',{input:{detailToken:detail.detailToken,targetToken:target.targetToken,requestKey:randomUUID(),hostInstanceId:targets.hostInstanceId,mode:'new',initiatedFrom:'plaza'}});
 let op;const end=Date.now()+15000;
 while(Date.now()<end){op=await invoke('getOperation',{operationId:receipt.operationId,hostInstanceId:targets.hostInstanceId});if(op.phase==='completed'||op.phase==='failed')break;await delay(100,undefined,{signal});}
 assert.equal(op.phase,'completed');
 const confirmDeadline=Date.now()+10000;
 while(op.discovery!=='confirmed' && Date.now()<confirmDeadline){
  await delay(100,undefined,{signal});
  op=await invoke('getOperation',{operationId:receipt.operationId,hostInstanceId:targets.hostInstanceId});
 }
 assert.equal(op.discovery,'confirmed');
 let skill;const deadline=Date.now()+15000;
 while(Date.now()<deadline){skill=await ctx.skills.get('document-helper',options);if(skill)break;await delay(100,undefined,{signal});}
 assert(skill?.content.includes('references/note.md'));
 assert.equal(skill.resourceBase.kind,'directory');assert.equal(await realpath(skill.resourceBase.path),await realpath(join(project,'.dsh/skills/document-helper')));
 assert.equal(await readFile(join(skill.resourceBase.path,'references/note.md'),'utf8'),'Native installation resource');
 const final=await ctx.skills.snapshot(options);assert(final.complete);assert(final.skills.some(s=>s.name==='document-helper'));
 await writeFile(join(project,'.dsh/skills/document-helper/references/note.md'),'Changed resource');
 const changed=await invoke('getOperation',{operationId:receipt.operationId,hostInstanceId:targets.hostInstanceId});
 assert.equal(changed.discovery,'unconfirmed');assert.equal(changed.files,'installed');
 return {changedResourceRevokesConfirmation:true,installedClientVerified:true,initiallyAbsent:true,installationCompleted:true,sameSessionDiscovery:true,resourceBaseVerified:true,resourceRead:true,operationDiscovery:op.discovery,modelInvoked:false};
}
