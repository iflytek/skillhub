import { expect, test } from 'bun:test';
import { Context } from '@deepseek-ai/cordis';
import { randomUUID } from 'node:crypto';
import SkillHubService from '@astron-team/skillhub-dsh';
import type { ConversationSelections } from '../src/host/conversation-selections.js';
import type { AskUserQuestionRequest, AskUserQuestionAnswer } from '@deepseek-ai/dsh-user-questions';

async function fixture(answer: (request: AskUserQuestionRequest) => Promise<AskUserQuestionAnswer>) {
  const ctx = new Context();
  const agent = { session: { id: 'owner', header: { cwd: '/project' } } };
  let current = agent;
  const remove = ctx.provide('agents', { get: () => current } as never);
  const removeInvocation = ctx.provide('invocation', { request: { args: { agentId: 'owner' } } } as never);
  const removeQuestions = ctx.provide('userQuestions', { ask: answer } as never);
  const fiber = ctx.plugin(SkillHubService, { registryUrl: 'https://registry.example' }); await fiber;
  const submitted: unknown[] = [], prepared: unknown[] = [];
  const runtime = { getStatus: () => ({ state: 'ready', hostInstanceId: randomUUID() }),
    submitInstall: async (request: unknown, prepare: () => Promise<unknown>) => { submitted.push(request); prepared.push(await prepare()); return undefined; } };
  const host = randomUUID(); runtime.getStatus = () => ({ state: 'ready', hostInstanceId: host });
  Reflect.set(ctx.skillhub, 'localRuntime', runtime);
  Reflect.set(ctx.skillhub, 'getSkillDetail', async () => ({ title: 'Writer', detailToken: 'detail', version: '1.0.0' }));
  Reflect.set(ctx.skillhub, 'installationTargetsFor', async () => ({ targets: [{ scope: 'project', targetToken: 'project-target' }, { scope: 'user', targetToken: 'user-target' }] }));
  Reflect.set(ctx.skillhub, 'prepareInstallation', async (...args: unknown[]) => args);
  const scope = { sessionId: 'owner', cwd: '/project', sourceRevision: ctx.skillhub.getContext().sourceRevision };
  const selections: ConversationSelections = Reflect.get(ctx.skillhub, 'selections');
  const ref = selections.add({ ...scope, callId: 'search' }, { namespace: 'team', slug: 'writer', version: '1.0.0' });
  const context = { sessionId: 'owner', cwd: '/project', callId: 'install' };
  return { ctx, agent, ref, context, submitted, prepared, setAgent: () => { current = { session: { ...agent.session } }; },
    close: async () => { Reflect.set(ctx.skillhub, 'localRuntime', undefined); await fiber.dispose(); removeQuestions(); removeInvocation(); remove(); } };
}

test('native input question waits for an exact human scope, then submits once for repeated calls', async () => {
  let respond!: (value: AskUserQuestionAnswer) => void;
  let seen!: AskUserQuestionRequest;
  let observed!: () => void;
  const questionShown = new Promise<void>(resolve => { observed = resolve; });
  const answer = new Promise<AskUserQuestionAnswer>(resolve => { respond = resolve; });
  const f = await fixture(async request => { seen = request; observed(); return answer; });
  try {
    const signal = new AbortController().signal;
    const first = f.ctx.skillhub.installConversationWithChoice({ selectionRef: f.ref }, f.context, signal);
    await questionShown;
    const second = f.ctx.skillhub.installConversationWithChoice({ selectionRef: f.ref }, f.context, signal);
    expect(seen.agent === (f.agent as unknown)).toBe(true); expect(seen.signal).toBe(signal);
    expect(seen.questions[0]?.detail).toContain('1.0.0');
    expect(seen.questions[0]?.options?.map(x => x.label)).toEqual(['安装到当前项目', '安装到当前用户', '取消安装']);
    expect(f.submitted).toHaveLength(0); expect(f.prepared).toHaveLength(0);
    respond({ answers: [{ id: 'skillhub-install-scope', selected: ['安装到当前项目'] }] });
    await Promise.all([first, second]);
    expect(f.submitted).toHaveLength(1); expect(f.prepared[0]).toEqual(['owner', 'detail', 'project-target', 'conversation', signal]);
  } finally { await f.close(); }
});

test.each([
  { selected: ['取消安装'] }, { selected: [] }, { selected: ['unknown'] },
  { selected: ['安装到当前项目'], custom: '安装到当前用户' }, { selected: ['安装到当前项目', '安装到当前用户'] },
])('cancelled/skipped/custom/ambiguous question answers never install: %j', async choice => {
  const f = await fixture(async () => ({ answers: [{ id: 'skillhub-install-scope', ...choice, selected: [...choice.selected] }] }));
  try {
    expect(await f.ctx.skillhub.installConversationWithChoice({ selectionRef: f.ref }, f.context, new AbortController().signal)).toMatchObject({ status: 'cancelled' });
    expect(f.submitted).toHaveLength(0); expect(f.prepared).toHaveLength(0);
  } finally { await f.close(); }
});

test('missing native question UI preserves the pending confirmation fallback', async () => {
  const f = await fixture(async () => { throw Object.assign(new Error('No answerer'), { code: 'NO_PROVIDER' }); });
  try {
    expect(await f.ctx.skillhub.installConversationWithChoice({ selectionRef: f.ref }, f.context, new AbortController().signal)).toMatchObject({ status: 'awaiting-confirmation' });
    expect(f.submitted).toHaveLength(0);
  } finally { await f.close(); }
});

test('changed live Agent after the human wait rejects the selection without writing', async () => {
  const change = () => f.setAgent();
  const f = await fixture(async () => { change(); return { answers: [{ id: 'skillhub-install-scope', selected: ['安装到当前项目'] }] }; });
  try {
    await expect(f.ctx.skillhub.installConversationWithChoice({ selectionRef: f.ref }, f.context, new AbortController().signal)).rejects.toThrow();
    expect(f.submitted).toHaveLength(0);
  } finally { await f.close(); }
});

test('closing the native question cancels without installing', async () => {
  const f = await fixture(async () => { throw Object.assign(new Error('Closed'), { code: 'ASK_CANCELLED' }); });
  try {
    expect(await f.ctx.skillhub.installConversationWithChoice({ selectionRef: f.ref }, f.context, new AbortController().signal)).toMatchObject({ status: 'cancelled' });
    expect(f.submitted).toHaveLength(0);
  } finally { await f.close(); }
});

test('source changes while choosing a scope reject the old answer', async () => {
  const f = await fixture(async () => { Reflect.set(f.ctx.skillhub, 'sourceRevision', randomUUID()); return { answers: [{ id: 'skillhub-install-scope', selected: ['安装到当前用户'] }] }; });
  try {
    await expect(f.ctx.skillhub.installConversationWithChoice({ selectionRef: f.ref }, f.context, new AbortController().signal)).rejects.toThrow();
    expect(f.submitted).toHaveLength(0);
  } finally { await f.close(); }
});
