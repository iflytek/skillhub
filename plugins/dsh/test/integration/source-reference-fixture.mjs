import { randomUUID } from 'node:crypto';
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { join, resolve, sep } from 'node:path';
import { tmpdir } from 'node:os';
export const inject = ['sessionController', 'workspaceRegistry', 'tools', 'sessions', 'skillhub'];
export function apply(ctx, config) {
 const root=resolve(config.root);
 assert(root.startsWith(resolve(tmpdir())+sep));
 assert.equal(process.env.DSH_HOME,join(root,'home'));
 const abort=new AbortController();
 const timer=setTimeout(()=>run(ctx,root,abort.signal).catch(error=>writeFile(join(root,'source-reference-ready.json'),JSON.stringify({error:error.message}))),1000);
 return ()=>{clearTimeout(timer);abort.abort();};
}
async function run(ctx,root,signal) {
 const project=join(root,'source-reference-'+randomUUID());
 await mkdir(project);
 const workspace=await ctx.workspaceRegistry.create(project);
 const created=await ctx.sessionController.create({workspaceId:workspace.id,agentPreset:'standard'});
 const resolved=await ctx.sessionController.resolveAgent(created.sessionId);
 if ('error' in resolved) throw resolved.error;
 const toolSession={scope:resolved.agent};
 const toolCallId='history-'+randomUUID();
 const toolArguments={q:'document'};
 const toolResult=await ctx.tools.execute({callId:toolCallId,name:'astron_skillhub_search',arguments:toolArguments,agent:resolved.agent,signal});
 assert.equal(toolResult.isError,false);
 assert.equal(toolResult.value.items.length,1);
 let searchFixtureTitle;
    const searchHistory = toolSession.scope.session;
    searchFixtureTitle = `SkillHub source reference fixture ${searchHistory.id.slice(-8)}`;
    await ctx.sessionController.rename({ sessionId: searchHistory.id, title: searchFixtureTitle });
    searchHistory.append('turn/start', { turn: 1 });
    searchHistory.append('step/start', { turn: 1, step: 1 });
    searchHistory.append('assistant/message', { turn: 1, step: 1, stream: [
      { type: 'tool-call-chunks', time0: Date.now(), index: 0, dt: [0], id: toolCallId,
        name: 'astron_skillhub_search', args: [JSON.stringify(toolArguments)] },
    ], message: { id: 'skillhub-search-fixture-assistant', role: 'assistant',
      source: { kind: 'model', provider: 'test-fixture', model: 'test-fixture' },
      content: [{ type: 'tool-call', id: toolCallId, name: 'astron_skillhub_search', arguments: JSON.stringify(toolArguments) }],
    } }, { surfaceOp: 'append' });
    searchHistory.append('tool/call', { turn: 1, step: 1, callId: toolCallId,
      name: 'astron_skillhub_search', arguments: JSON.stringify(toolArguments) });
    searchHistory.append('tool/result', { turn: 1, step: 1, message: {
      id: 'skillhub-search-fixture-result', role: 'tool', source: { kind: 'tool', callId: toolCallId },
      toolCallId, content: toolResult.content, isError: false,
    }, meta: toolResult.meta }, { surfaceOp: 'append' });
    searchHistory.append('step/end', { turn: 1, step: 1 });
    searchHistory.append('turn/end', { turn: 1, reason: { kind: 'completed' } });
    await ctx.sessions.flush(searchHistory);

 await writeFile(join(root,'source-reference-ready.json'),JSON.stringify({title:searchFixtureTitle,project,node:process.versions.node,modelInvoked:false}));
}
