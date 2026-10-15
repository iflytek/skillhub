import { randomUUID } from 'node:crypto';
import assert from 'node:assert/strict';
import { mkdir, realpath, writeFile } from 'node:fs/promises';
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
 const timer=setTimeout(()=>run(ctx,root,abort.signal).catch(error=>writeFile(join(root,'unknown-history-ready.json'),JSON.stringify({error:error.message}))),1000);
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
 const toolSession={scope:resolved.agent};
 const toolCallId='history-'+randomUUID();
 const toolArguments={q:'document'};
 const toolResult=await ctx.tools.execute({callId:toolCallId,name:'astron_skillhub_search',arguments:toolArguments,agent:resolved.agent,signal});
 assert.equal(toolResult.isError,false);
 assert.equal(toolResult.value.kind,'skillhub-search');
 assert(toolResult.value.items.some(item=>item.slug==='document-helper'));
 const installHistory = process.env.DSH_HISTORY_KIND === 'install';
 const historyTool = installHistory ? 'astron_skillhub_install' : 'astron_skillhub_search';
 const metadata = installHistory ? {
   schemaVersion: 1, kind: 'skillhub-install', status: 'accepted', toolCallId,
   operationId: randomUUID(), hostInstanceId: randomUUID(), sourceRevision: randomUUID(),
   namespace: 'demo', slug: 'document-helper', version: '1.2.0', targetLabel: '当前项目',
   hostLabel: 'Fixture Host', sourceLabel: 'http://127.0.0.1:43219', createdAt: new Date().toISOString(),
 } : toolResult.meta;
 const content = installHistory ? [{ type: 'text', text: JSON.stringify(metadata) }] : toolResult.content;
 let searchFixtureTitle;
    const searchHistory = toolSession.scope.session;
    searchFixtureTitle = `SkillHub unknown history fixture ${searchHistory.id.slice(-8)}`;
    await ctx.sessionController.rename({ sessionId: searchHistory.id, title: searchFixtureTitle });
    searchHistory.append('turn/start', { turn: 1 });
    searchHistory.append('step/start', { turn: 1, step: 1 });
    searchHistory.append('assistant/message', { turn: 1, step: 1, stream: [
      { type: 'tool-call-chunks', time0: Date.now(), index: 0, dt: [0], id: toolCallId,
        name: historyTool, args: [JSON.stringify(toolArguments)] },
    ], message: { id: 'skillhub-search-fixture-assistant', role: 'assistant',
      source: { kind: 'model', provider: 'test-fixture', model: 'test-fixture' },
      content: [{ type: 'tool-call', id: toolCallId, name: historyTool, arguments: JSON.stringify(toolArguments) }],
    } }, { surfaceOp: 'append' });
    searchHistory.append('tool/call', { turn: 1, step: 1, callId: toolCallId,
      name: historyTool, arguments: JSON.stringify(toolArguments) });
    searchHistory.append('tool/result', { turn: 1, step: 1, message: {
      id: 'skillhub-search-fixture-result', role: 'tool', source: { kind: 'tool', callId: toolCallId },
      toolCallId, content, isError: false,
    }, meta: { ...metadata, schemaVersion: 999 } }, { surfaceOp: 'append' });
    searchHistory.append('step/end', { turn: 1, step: 1 });
    searchHistory.append('turn/end', { turn: 1, reason: { kind: 'completed' } });
    await ctx.sessions.flush(searchHistory);

 await writeFile(join(root,'unknown-history-ready.json'),JSON.stringify({title:searchFixtureTitle,project,node:process.versions.node,installedClientVerified:true,syntheticInstallReceipt:installHistory,modelInvoked:false}));
}
