import { randomUUID } from 'node:crypto';
import assert from 'node:assert/strict';
import { mkdir, realpath, readFile, writeFile, rename, symlink, unlink, chmod, stat } from 'node:fs/promises';
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
 const timer=setTimeout(()=>run(ctx,root,abort.signal).catch(error=>writeFile(join(root,'installed-state-result.json'),JSON.stringify({error:error.message}))),1000);
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

 const userResult=await ctx.tools.execute({callId:randomUUID(),name:'astron_skillhub_install',
  arguments:{...toolArguments,scope:'user'},agent:resolved.agent,signal});
 assert.equal(userResult.isError,false);assert.equal(userResult.value.status,'accepted');
 const end=Date.now()+15000;
 while(Date.now()<end){
  operation=await ctx.skillhub.getInstallationOperation(created.sessionId,userResult.value.operationId);
  if(['completed','failed','unknown','recovery-required'].includes(operation?.phase))break;
  await delay(100,undefined,{signal});
 }
 assert.equal(operation.phase,'completed');
 const source=ctx.skillhub.getContext();
 const invoke=(method,args)=>ctx.typertGateway.invoke({namespace:'skillhub',method,args:{agentId:created.sessionId,...args},signal});
 const paths={project:join(project,'.dsh/skills/document-helper'),user:join(root,'home/skills/document-helper')};
 const results=[];
 const permissionsCovered=process.platform!=='win32' && typeof process.getuid==='function' && process.getuid()!==0;
 for(const scope of ['project','user']){
  const target=paths[scope];const metadataPath=join(target,'.skillhub/metadata.json');
  const metadata=await readFile(metadataPath,'utf8');const skill=await readFile(join(target,'SKILL.md'),'utf8');
  for(const mode of ['missing','symlink','metadata-invalid','metadata-mismatch','skill-missing','content-modified',...(permissionsCovered?['permission-denied']:[])]){
   const backup=join(root,'state-backup-'+randomUUID());
   let moved=false;let linked=false;let skillMoved=false;
   const originalMode=(await stat(target)).mode & 0o777;
   try {
    if(mode==='missing'||mode==='symlink'){
     await rename(target,backup);moved=true;
     if(mode==='symlink'){await symlink(backup,target,'dir');linked=true;}
    } else if(mode==='metadata-invalid') await writeFile(metadataPath,'{broken');
    else if(mode==='metadata-mismatch') await writeFile(metadataPath,JSON.stringify({...JSON.parse(metadata),version:'9.0.0'}));
    else if(mode==='skill-missing'){await rename(join(target,'SKILL.md'),backup);skillMoved=true;}
    else if(mode==='permission-denied') await chmod(target,0);
    else await writeFile(join(target,'SKILL.md'),skill+'\nLocal edit');
    const list=await invoke('listInstalled',{sourceRevision:source.sourceRevision});
    const items=list.items.filter(item=>item.namespace===toolArguments.namespace);
    assert.equal(items.length,2);
    const selected=items.find(item=>item.scope===scope);assert(selected);
    assert.equal(items.find(item=>item.scope!==scope).state,'present');
    const expected={missing:'missing',symlink:'mismatch','metadata-invalid':'metadata-invalid',
     'metadata-mismatch':'mismatch','skill-missing':'unreadable','content-modified':'present','permission-denied':'unreadable'}[mode];
    assert.equal(selected.state,expected);
    if(!['missing','content-modified'].includes(mode)) assert.equal(list.complete,false);
    const upgrade=await invoke('prepareUpgrade',{sourceRevision:source.sourceRevision,itemKey:selected.itemKey});
    assert.equal(upgrade.status,'blocked');
    const removal=await invoke('prepareUninstall',{sourceRevision:source.sourceRevision,itemKey:selected.itemKey});
    assert.equal(removal.status,mode==='missing'?'ready':'blocked');
    if(mode==='missing') assert.equal(removal.kind,'remove-stale-record');
    results.push({scope,mode,state:selected.state,upgrade:upgrade.status,removal:removal.status});
   } finally {
    if(mode==='permission-denied') await chmod(target,originalMode);
    if(linked) await unlink(target);
    if(moved) await rename(backup,target);
    if(skillMoved) await rename(backup,join(target,'SKILL.md'));
    if(!moved){await writeFile(metadataPath,metadata);await writeFile(join(target,'SKILL.md'),skill);}
   }
  }
 }
 const final=await invoke('listInstalled',{sourceRevision:source.sourceRevision});
 assert(final.items.filter(item=>item.namespace===toolArguments.namespace).every(item=>item.state==='present'));
 await writeFile(join(root,'installed-state-result.json'),JSON.stringify({passed:true,results,bothScopesRestored:true,
  modelInvoked:false,contentModificationRequiresPreflight:true,permissionsCovered},null,2));
}
