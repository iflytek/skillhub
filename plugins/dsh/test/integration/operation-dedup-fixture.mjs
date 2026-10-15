import { randomUUID } from 'node:crypto';
import assert from 'node:assert/strict';
import { mkdir, realpath, writeFile } from 'node:fs/promises';
import { join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';
import { tmpdir } from 'node:os';
export const inject = ['sessionController', 'workspaceRegistry', 'tools', 'sessions', 'skillhub', 'clientModules'];
export function apply(ctx, config) {
 const root=resolve(config.root);
 assert(root.startsWith(resolve(tmpdir())+sep));
 assert(fileURLToPath(import.meta.url).startsWith(resolve(tmpdir())+sep), 'Load observer from outside the package');
 assert.equal(process.env.DSH_HOME,join(root,'home'));
 const abort=new AbortController();
 const timer=setTimeout(()=>run(ctx,root,abort.signal).catch(error=>writeFile(join(root,'operation-dedup-session.json'),JSON.stringify({error:error.message}))),1000);
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
 const toolSession={scope:resolved.agent};
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
 let searchFixtureTitle;
    const searchHistory = toolSession.scope.session;
    searchFixtureTitle = `SkillHub conversation install fixture ${searchHistory.id.slice(-8)}`;
    await ctx.sessionController.rename({ sessionId: searchHistory.id, title: searchFixtureTitle });
    // Synthetic history renders the same real receipt twice; only one install tool was executed.
    for (const turn of [1, 2]) {
      const callId = toolCallId + '-' + turn;
    searchHistory.append('turn/start', { turn: turn });
    searchHistory.append('step/start', { turn: turn, step: 1 });
    searchHistory.append('assistant/message', { turn: turn, step: 1, stream: [
      { type: 'tool-call-chunks', time0: Date.now(), index: 0, dt: [0], id: callId,
        name: 'astron_skillhub_install', args: [JSON.stringify(toolArguments)] },
    ], message: { id: 'skillhub-assistant-'+turn, role: 'assistant',
      source: { kind: 'model', provider: 'test-fixture', model: 'test-fixture' },
      content: [{ type: 'tool-call', id: callId, name: 'astron_skillhub_install', arguments: JSON.stringify(toolArguments) }],
    } }, { surfaceOp: 'append' });
    searchHistory.append('tool/call', { turn: turn, step: 1, callId: callId,
      name: 'astron_skillhub_install', arguments: JSON.stringify(toolArguments) });
    searchHistory.append('tool/result', { turn: turn, step: 1, message: {
      id: 'skillhub-result-'+turn, role: 'tool', source: { kind: 'tool', callId: callId },
      toolCallId: callId, content: toolResult.content, isError: false,
    }, meta: toolResult.meta }, { surfaceOp: 'append' });
    searchHistory.append('step/end', { turn: turn, step: 1 });
    searchHistory.append('turn/end', { turn: turn, reason: { kind: 'completed' } });
    }
    await ctx.sessions.flush(searchHistory);

 await writeFile(join(root,'operation-dedup-session.json'),JSON.stringify({title:searchFixtureTitle,project,node:process.versions.node,installedClientVerified:true,modelInvoked:false}));
}
