// Real isolated Host proof. No model invocation and no user Profile modifications.
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { mkdir, readFile, writeFile, readdir } from 'node:fs/promises';
import { join, resolve, relative, isAbsolute } from 'node:path';
import { tmpdir } from 'node:os';
import { setTimeout as delay } from 'node:timers/promises';
export const inject = ['sessionController', 'workspaceRegistry', 'skillhub', 'typertGateway'];
export function apply(ctx, config) {
 const root=resolve(config.root);const rel=relative(resolve(tmpdir()),root);assert(rel&&!rel.startsWith('..')&&!isAbsolute(rel));
 assert.equal(process.env.HOME,join(root,'native-install-home'));
 const controller=new AbortController();let stage='setup';const timer=setTimeout(()=>run(ctx,config,controller.signal,v=>{stage=v;}).then(result=>({passed:true,...result}),error=>({passed:false,stage,error:String(error.message)})).then(result=>writeFile(config.output,JSON.stringify(result,null,2))),1000);
 return()=>{clearTimeout(timer);controller.abort();};
}
async function run(ctx,config,signal,checkpoint){
 const root=resolve(config.root),project=join(root,config.projectName??'change-project');await mkdir(join(project,'.git'),{recursive:true});
 const workspace=await ctx.workspaceRegistry.create(project);const session=await ctx.sessionController.create({workspaceId:workspace.id,agentPreset:'standard'});
 const title='升级变化与本地文件撤销验收（真实Host，无模型）';await ctx.sessionController.rename({sessionId:session.sessionId,title});
 const invoke=(method,args={})=>ctx.typertGateway.invoke({peer:ctx.get('connection')?.browserPeer??ctx.get('connection')?.operator,namespace:'skillhub',method,args:{...(method === 'getSkillDetail' ? {} : {agentId:session.sessionId}),...args},signal});
 const revision=()=>ctx.skillhub.getContext().sourceRevision;const list=()=>invoke('listInstalled',{sourceRevision:revision()});
 async function completed(op){assert(op?.operationId);for(let i=0;i<150;i++){const current=await invoke('getOperation',{operationId:op.operationId,hostInstanceId:op.context.hostInstanceId});if(current?.phase==='completed')return current;if(current&&['failed','unknown','recovery-required'].includes(current.phase))throw Error(JSON.stringify(current));await delay(100);}throw Error('Operation timeout');}
 checkpoint('install');
 const detail=await invoke('getSkillDetail',{sourceRevision:revision(),namespace:'change-demo',slug:'document-helper',version:'1.2.0'});const targets=await invoke('listInstallTargets',{detailToken:detail.detailToken});const target=targets.targets.find(t=>t.scope==='project');assert(target);
 await completed(await invoke('startInstall',{input:{detailToken:detail.detailToken,targetToken:target.targetToken,hostInstanceId:targets.hostInstanceId,requestKey:randomUUID(),mode:'new',initiatedFrom:'plaza'}}));
 const directory=join(project,'.dsh/skills/document-helper');const original=await readFile(join(directory,'SKILL.md'),'utf8');
 let item=(await list()).items.find(i=>i.scope==='project');assert(item);
 const metadataPath=join(directory,'.skillhub/metadata.json');const metadata=await readFile(metadataPath,'utf8');
 if(config.previewOnly){await writeFile(join(directory,'SKILL.md'),original+'\n人工验收：本地修改示例。\n'+Array.from({length:90},(_,i)=>'本地说明 '+i).join('\n'));return {realHost:true,modelCalled:false,sessionId:session.sessionId,title,project,directory,manualPreview:true};}

 const inspect=()=>invoke('inspectLocalChanges',{sourceRevision:revision(),itemKey:item.itemKey});
 checkpoint('local-read');await writeFile(join(directory,'SKILL.md'),original+'\n本机定制内容\n');await writeFile(join(directory,'team.md'),'本地新增文件\n');
 let local=await inspect();assert.equal(local.status,'ready');assert.equal(local.changes.length,2);
 const diff=await invoke('readFileChange',{sourceRevision:revision(),itemKey:item.itemKey,snapshotId:local.snapshotId,path:'SKILL.md'});assert.equal(diff.state,'text');assert(diff.lines.some(line=>line.text.includes('本机定制内容')));
 checkpoint('single-reset');let plan=await invoke('prepareFileReset',{sourceRevision:revision(),itemKey:item.itemKey,snapshotId:local.snapshotId,paths:['SKILL.md']});
 await completed(await invoke('executeFileReset',{sourceRevision:revision(),itemKey:item.itemKey,request:{planId:plan.planId,hostInstanceId:plan.hostInstanceId,requestKey:randomUUID()}}));
 assert.equal(await readFile(join(directory,'SKILL.md'),'utf8'),original);assert.equal(await readFile(join(directory,'team.md'),'utf8'),'本地新增文件\n');assert.equal(await readFile(metadataPath,'utf8'),metadata);
 checkpoint('reset-drift');local=await inspect();plan=await invoke('prepareFileReset',{sourceRevision:revision(),itemKey:item.itemKey,snapshotId:local.snapshotId,paths:['team.md']});await writeFile(join(directory,'team.md'),'确认后的新修改\n');
 const drift=await invoke('executeFileReset',{sourceRevision:revision(),itemKey:item.itemKey,request:{planId:plan.planId,hostInstanceId:plan.hostInstanceId,requestKey:randomUUID()}}).then(completed,()=>null).catch(()=>null);assert.equal(drift,null);assert.equal(await readFile(join(directory,'team.md'),'utf8'),'确认后的新修改\n');
 checkpoint('backup-preview');local=await inspect();const before=await readFile(join(directory,'team.md'),'utf8');const preview=await invoke('previewUpgradeChanges',{sourceRevision:revision(),itemKey:item.itemKey,snapshotId:local.snapshotId,version:'1.3.0',backup:true});assert.equal(preview.kind,'version');assert.equal(preview.backupSaved,true);assert.equal(await readFile(join(directory,'team.md'),'utf8'),before);
 const backupRoot=join(root,'home/skillhub/backups');const backups=await readdir(backupRoot);assert(backups.length>0);
 checkpoint('upgrade');plan=await invoke('confirmUpgradeChanges',{sourceRevision:revision(),itemKey:item.itemKey,snapshotId:preview.snapshotId});assert.equal(plan.status,'ready');await completed(await invoke('executePlan',{input:{planId:plan.planId,requestKey:randomUUID()}}));
 item=(await list()).items.find(i=>i.scope==='project');assert.equal(item.version,'1.3.0');assert.equal((await inspect()).changes.length,0);
 // Leave a fresh local edit for the user to inspect and discard manually in the actual UI.
 await writeFile(join(directory,'SKILL.md'),(await readFile(join(directory,'SKILL.md'),'utf8'))+'\n人工验收：本地修改示例。\n');
 return {realHost:true,modelCalled:false,sessionId:session.sessionId,title,project,directory,sourceRevision:revision(),localDiff:true,singleReset:true,metadataUnchanged:true,driftRejected:true,backupBeforeUpgrade:true,upgradedVersion:item.version,backupCount:backups.length,previewLocalChanges:(await inspect()).changes.length};
}
