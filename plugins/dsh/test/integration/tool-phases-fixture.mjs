import { setTimeout as delay } from 'node:timers/promises';
import { randomUUID } from 'node:crypto';
import assert from 'node:assert/strict';
import { mkdir, readFile, realpath, writeFile } from 'node:fs/promises';
import { join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { tmpdir } from 'node:os';
export const inject = ['sessionController', 'workspaceRegistry', 'tools', 'sessions', 'skillhub', 'clientModules'];
export function apply(ctx, config) {
 const root=resolve(config.root);
 assert(root.startsWith(resolve(tmpdir())+sep));
 assert(fileURLToPath(import.meta.url).startsWith(resolve(tmpdir())+sep), 'Load observer from outside the package');
 assert.equal(process.env.DSH_HOME,join(root,'home'));
 const abort=new AbortController();
 const timer=setTimeout(()=>run(ctx,root,abort.signal).catch(error=>writeFile(join(root,'tool-phases-ready.json'),JSON.stringify({error:error.message}))),1000);
 return ()=>{clearTimeout(timer);abort.abort();};
}
async function run(ctx,root,signal) {
 assert.equal(await realpath(ctx.clientModules.clientPath('@astron-team/skillhub-dsh')),await realpath(join(root,'home/profiles/skillhub-m0/node_modules/@astron-team/skillhub-dsh/lib/client.js')));
 const project=join(root,'conversation-search-'+randomUUID());
 await mkdir(project);
 const workspace=await ctx.workspaceRegistry.create(project);
 const created=await ctx.sessionController.create({workspaceId:workspace.id,agentPreset:'standard'});
 const resolved=await ctx.sessionController.resolveAgent(created.sessionId);
 if ('error' in resolved) throw resolved.error;
 const history = resolved.agent.session;
 const title = `SkillHub tool phases fixture ${history.id.slice(-8)}`;
 await ctx.sessionController.rename({ sessionId: history.id, title });
 const calls = [
   { id: 'search-'+randomUUID(), name: 'astron_skillhub_search', args: {q:'document'} },
   { id: 'install-'+randomUUID(), name: 'astron_skillhub_install', args: {namespace:'demo',slug:'document-helper',scope:'project'} },
 ];
 const searchResult = await ctx.tools.execute({callId:calls[0].id,name:calls[0].name,arguments:calls[0].args,agent:resolved.agent,signal});
 assert.equal(searchResult.isError,false);
 history.append('turn/start', {turn:1}); history.append('step/start', {turn:1,step:1});
 const attemptId = 'fixture-'+randomUUID();
 const emit = frame => resolved.agent.ctx.emit('agent/assistant-stream', {agent:resolved.agent,frame});
 emit({type:'start',attemptId,revision:1,turn:1,step:1});
 calls.forEach((call,index)=>emit({type:'chunk',attemptId,revision:index+2,index,time:Date.now(),
   chunk:{type:'tool-call-delta',index,id:call.id,name:call.name,argumentsDelta:JSON.stringify(call.args)}}));
 await ctx.sessions.flush(history);
 await writeFile(join(root,'tool-phases-ready.json'),JSON.stringify({title,project,modelInvoked:false}));
 const wait = async expected => {
   const deadline=Date.now()+90000;
   while(Date.now()<deadline){
     signal.throwIfAborted();
     const value=await readFile(join(root,'phase-command'),'utf8').catch(error=>{if(error.code==='ENOENT')return '';throw error;});
     if(value===expected)return;
     await delay(100,undefined,{signal});
   }
   throw new Error('Phase command timeout');
 };
 await wait('start');
 const settled = history.append('assistant/message',{turn:1,step:1,
   stream:calls.map((call,index)=>({type:'tool-call-chunks',time0:Date.now(),index,dt:[0],id:call.id,name:call.name,args:[JSON.stringify(call.args)]})),
   message:{id:'fixture-assistant',role:'assistant',source:{kind:'model',provider:'test-fixture',model:'test-fixture'},
     content:calls.map(call=>({type:'tool-call',id:call.id,name:call.name,arguments:JSON.stringify(call.args)}))}}, {surfaceOp:'append'});
 emit({type:'end',attemptId,revision:4,index:2,outcome:{kind:'committed',eventType:'assistant/message',seq:settled.seq}});
 for(const call of calls)history.append('tool/call',{turn:1,step:1,callId:call.id,name:call.name,arguments:JSON.stringify(call.args)});
 await ctx.sessions.flush(history);
 await wait('result');
 const installMeta={schemaVersion:1,kind:'skillhub-install',status:'not-confirmed',toolCallId:calls[1].id,code:'UNCONFIRMED'};
 for(const [index,call] of calls.entries()){
   const meta=index===0?searchResult.meta:installMeta;
   history.append('tool/result',{turn:1,step:1,message:{id:'fixture-result-'+index,role:'tool',source:{kind:'tool',callId:call.id},
     toolCallId:call.id,content:[{type:'text',text:'Fixture body intentionally omits presentation metadata.'}],isError:false},meta},{surfaceOp:'append'});
 }
 history.append('step/end',{turn:1,step:1});history.append('turn/end',{turn:1,reason:{kind:'completed'}});
 await ctx.sessions.flush(history);
 await writeFile(join(root,'tool-phases-settled.json'),JSON.stringify({passed:true,modelInvoked:false,installationExecuted:false}));
}
