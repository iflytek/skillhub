import { expect, test } from 'bun:test';
import type { InputState, TokenSpan } from '@deepseek-ai/dsh-client-ui-conversation/client';
import type { SkillUsePreparation, InstalledSkillList, SkillExamples } from '../src/host/types.js';
import { SkillUseButtonRequests, SkillUseController, exactInstalledSkill, selectExampleLocale, exampleTask, skillTaskInsertion, type SkillUseEnvironment } from '../src/client/use-skill.js';

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((done, fail) => { resolve = done; reject = fail; });
  return { promise, resolve, reject };
}
function fixture(environment: Partial<SkillUseEnvironment> = {}) {
  let state: InputState = { draft: '原草稿 与引用', draftRev: 3, phase: 'plain', attachmentIds: [], occurrences: [], queue: [] };
  const original = state;
  let current = true, revision = 'source', inserted = 0, revealed = 0, focused = 0, released = 0;
  let result: SkillUsePreparation = { status: 'ready', name: 'actual-name', version: '1.2.0', scope: 'project', examples: { state: 'none' } };
  let prepare: SkillUseEnvironment['prepare'] = async () => ({ ok: true, value: result });
  const spans: TokenSpan[] = [];
  let selection = { start: 0, end: 3 };
  const actions = {
    captureInsertion: () => ({ ...selection, draftRev: state.draftRev }),
    insertText: (text: string, span: TokenSpan) => {
      spans.push(span);
      if (span.draftRev !== state.draftRev) return false;
      state = { ...state, draft: state.draft.slice(0, span.end) + text + state.draft.slice(span.end), draftRev: state.draftRev + 1 }; inserted++; return true;
    },
  };
  const controller = new SkillUseController({
    capture: () => ({ actions, input: { state: { getSnapshot: () => state }, focus: () => { focused++; } }, assertCurrent: () => { if (!current) throw new Error('stale context'); }, dispose: () => { released++; } }),
    revision: async () => revision,
    prepare: (...args) => prepare(...args), reveal: () => { revealed++; }, ...environment,
  });
  const unbind = controller.bind('session', actions);
  return { controller, unbind, actions, original, spans, use: () => controller.services('session').useSkill('source', 'item', new AbortController().signal),
    released: () => released, counts: () => ({ inserted, revealed, focused }), state: () => state,
    position: (start: number, end = start) => { selection = { start, end }; },
    content: (value: Partial<InputState>) => { state = { ...state, ...value, draftRev: state.draftRev + 1 }; },
    edit: (draft = '用户的新草稿') => { state = { ...state, draft, draftRev: state.draftRev + 1 }; },
    invalidate: () => { current = false; }, source: (value: string) => { revision = value; }, phase: (value: InputState['phase']) => { state = { ...state, phase: value }; },
    prepare: (value: SkillUseEnvironment['prepare']) => { prepare = value; }, blocked: (reason: Extract<SkillUsePreparation, { status: 'blocked' }>['reason']) => { result = { status: 'blocked', reason }; },
  };
}
test('native insertion collapses selection, preserves existing content and attachment/reference objects, never sends', async () => {
  const f = fixture(); await f.use();
  expect(f.spans).toEqual([{ start: 3, end: 3, draftRev: 3 }]);
  expect(f.state().draft).toContain('请使用 /actual-name');
  expect(f.state().draft.replace(/\n请使用.*\n/u, '')).toBe(f.original.draft);
  expect(f.state().attachmentIds).toBe(f.original.attachmentIds); expect(f.state().occurrences).toBe(f.original.occurrences);
  expect(f.counts()).toEqual({ inserted: 1, revealed: 1, focused: 0 });
  f.controller.focus('another'); expect(f.counts().focused).toBe(0);
  f.controller.focus('session'); expect(f.counts().focused).toBe(1);
  await f.use(); expect(f.counts().inserted).toBe(1); f.controller.dispose();
});
test('edited draft fails CAS without replacing text or navigating', async () => {
  const f = fixture(); const gate = deferred<void>(); f.prepare(async () => { await gate.promise; return { ok: true, value: { status: 'ready', name: 'actual-name', version: '1', scope: 'user', examples: { state: 'none' } } }; });
  const use = f.use(); await Promise.resolve(); await Promise.resolve(); f.edit(); gate.resolve();
  await expect(use).rejects.toThrow('草稿已被编辑'); expect(f.state().draft).toBe('用户的新草稿'); expect(f.counts().revealed).toBe(0);
});
for (const reason of ['installation-unverified', 'operation-pending', 'shared-installation', 'files-changed', 'skill-unavailable', 'skill-shadowed', 'user-invocation-disabled', 'unsupported-name', 'host-unavailable'] as const) {
  test(`blocked ${reason} leaves native draft intact`, async () => { const f = fixture(); f.blocked(reason); await expect(f.use()).rejects.toThrow(); expect(f.state()).toBe(f.original); expect(f.counts().inserted).toBe(0); });
}
for (const phase of ['claimed', 'adjudicating', 'submitting'] as const) {
  test(`busy ${phase} refuses before remote verification`, async () => { const f = fixture(); f.phase(phase); f.prepare(() => { throw new Error('must not call'); }); await expect(f.use()).rejects.toThrow('命令'); expect(f.counts().inserted).toBe(0); });
}
for (const change of ['session/cwd/binding', 'source-ABA', 'unmount', 'dispose'] as const) {
  test(`${change} while preparing never writes a draft`, async () => {
    const f = fixture(); const gate = deferred<void>();
    f.prepare(async () => { await gate.promise; return { ok: true, value: { status: 'ready', name: 'actual-name', version: '1', scope: 'project', examples: { state: 'none' } } }; });
    const use = f.use(); await Promise.resolve(); await Promise.resolve();
    if (change === 'session/cwd/binding') f.invalidate(); if (change === 'source-ABA') f.source('new-revision-even-if-same-url');
    if (change === 'unmount') f.unbind(); if (change === 'dispose') f.controller.dispose(); gate.resolve();
    await expect(use).rejects.toThrow(); expect(f.state()).toBe(f.original); expect(f.counts().inserted).toBe(0);
  });
}
test('success receipt inventory read captures selection before awaiting, so concurrent edits are preserved', async () => {
  const f = fixture(); const gate = deferred<string>();
  const use = f.controller.services('session').useSkill('source', undefined, new AbortController().signal, () => gate.promise);
  await Promise.resolve(); f.edit(); gate.resolve('fresh-item'); await expect(use).rejects.toThrow('草稿已被编辑'); expect(f.state().draft).toBe('用户的新草稿');
});
test('cancelled request and unbound composer never insert', async () => {
  const f = fixture(); const signal = AbortSignal.abort(); await expect(f.controller.services('session').useSkill('source', 'item', signal)).rejects.toThrow();
  f.unbind(); await expect(f.use()).rejects.toThrow('打开当前会话'); expect(f.counts().inserted).toBe(0);
});
test('receipt resolves exact version and scope, never a later or historical installation', () => {
  const item = { itemKey: 'fresh', namespace: 'global', slug: 'writer', scope: 'user', version: '2', state: 'present' } as const;
  const inventory: InstalledSkillList = { items: [item, { ...item, scope: 'project', version: '1', itemKey: 'project' }], complete: true, issues: [], unavailable: [] };
  expect(exactInstalledSkill(inventory, item)?.itemKey).toBe('fresh');
  expect(exactInstalledSkill(inventory, { ...item, version: '1' })).toBeUndefined();
  expect(exactInstalledSkill({ ...inventory, complete: false }, item)).toBeUndefined();
  expect(exactInstalledSkill({ ...inventory, unavailable: ['user'] }, item)).toBeUndefined();
});

test('native capture exceptions release context subscriptions and never edit', async () => {
  const f = fixture(); f.actions.captureInsertion = () => { throw new Error('native editor unavailable'); };
  await expect(f.use()).rejects.toThrow('native editor unavailable'); expect(f.released()).toBe(1); expect(f.counts().inserted).toBe(0);
});

test('deduplication still refuses a concurrently edited draft even when it now includes the skill token', async () => {
  const f = fixture(); const gate = deferred<void>();
  f.prepare(async () => { await gate.promise; return { ok: true, value: { status: 'ready', name: 'actual-name', version: '1', scope: 'project', examples: { state: 'none' } } }; });
  const use = f.use(); await Promise.resolve(); await Promise.resolve();
  f.edit('用户的新草稿 /actual-name'); gate.resolve();
  await expect(use).rejects.toThrow('草稿已被编辑'); expect(f.state().draft).toBe('用户的新草稿 /actual-name'); expect(f.counts().revealed).toBe(0);
});
test('changing an entry target resets busy state and stale completion cannot overwrite the new request', async () => {
  const requests = new SkillUseButtonRequests(); const old = deferred<void>(), fresh = deferred<void>();
  let oldSignal: AbortSignal | undefined; let used = 0;
  const first = requests.run(signal => { oldSignal = signal; return old.promise; }, () => { used++; });
  expect(requests.getSnapshot().busy).toBe(true);
  requests.reset(); expect(oldSignal?.aborted).toBe(true); expect(requests.getSnapshot()).toEqual({ busy: false, error: '' });
  const second = requests.run(() => fresh.promise, () => { used++; });
  old.resolve(); await first; expect(used).toBe(0); expect(requests.getSnapshot().busy).toBe(true);
  fresh.resolve(); await second; expect(used).toBe(1); expect(requests.getSnapshot()).toEqual({ busy: false, error: '' });
});
test('stale request rejection cannot expose errors or disable a replacement entry', async () => {
  const requests = new SkillUseButtonRequests(); const old = deferred<void>();
  const first = requests.run(() => old.promise); requests.reset();
  old.reject(new Error('old target error')); await first; expect(requests.getSnapshot()).toEqual({ busy: false, error: '' });
  await requests.run(async () => { throw new Error('current error'); }); expect(requests.getSnapshot().error).toBe('current error');
  requests.reset(); expect(requests.getSnapshot()).toEqual({ busy: false, error: '' });
});

const caseDocument: Extract<SkillExamples, { state: 'ready' }> = {
  state: 'ready', digest: 'digest', defaultLocale: 'zh-CN', items: {
    'zh-CN': [{ id: 'summary', title: '整理会议', prompt: '使用 {{skill}} 整理；再用 {{skill}} 检查 {{other}}。' }],
    en: [{ id: 'summary', title: 'Summary', prompt: 'Summarize notes.' }],
    'zh-Hant': [],
  },
};
const caseReady = (examples: SkillExamples = caseDocument): SkillUsePreparation => ({ status: 'ready', name: 'actual-name', scope: 'project', version: '1.2.0', examples });
test('locale exact/parent/default selection respects explicit empty and authored key case', () => {
  expect(selectExampleLocale(caseDocument, 'EN')).toBe('en');
  expect(selectExampleLocale(caseDocument, 'zh-Hant-TW')).toBe('zh-Hant');
  expect(selectExampleLocale(caseDocument, 'ja')).toBe('zh-CN');
  expect(selectExampleLocale({ ...caseDocument, defaultLocale: 'zh-cn' })).toBe('zh-CN');
  expect(selectExampleLocale(caseDocument, 'zh-TW')).toBe('zh-CN');
});
test('only exact placeholders expand, all occurrences use native name, no expression evaluation', () => {
  expect(exampleTask('{{skill}} {{other}} {{skill}}', 'native-name', '')).toBe('/native-name {{other}} /native-name');
  expect(exampleTask('整理材料', 'native-name', '')).toBe('请使用 /native-name 完成本次任务。\n整理材料');
  expect(exampleTask('整理材料', 'native-name', '已选 /native-name')).toBe('整理材料');
});
test('case panel waits without editing then inserts selected fresh prompt preserving draft objects', async () => {
  const f = fixture(); let preparations = 0;
  f.prepare(async () => { preparations++; return { ok: true, value: caseReady() }; });
  const selected = deferred<{ locale: string; id: string }>(); let shown = false;
  const use = f.controller.services('session').useSkill('source', 'item', new AbortController().signal, undefined,
    async (items, locale) => { shown = true; expect(items[0]?.title).toBe('整理会议'); expect(locale).toBe('zh-CN'); return selected.promise; });
  await Bun.sleep(0); expect(shown).toBe(true); expect(f.counts().inserted).toBe(0); expect(f.spans).toEqual([]);
  f.edit('用户在选择时补充材料 /actual-name');
  selected.resolve({ locale: 'zh-CN', id: 'summary' }); await use;
  expect(preparations).toBe(2); expect(f.state().draft).toContain('使用 /actual-name 整理；再用 /actual-name 检查 {{other}}。');
  expect(f.state().attachmentIds).toBe(f.original.attachmentIds); expect(f.counts().inserted).toBe(1);
});
for (const change of ['digest', 'version', 'scope', 'name', 'id'] as const) {
  test(`case ${change} change at final preparation prevents insertion`, async () => {
    const f = fixture(); let calls = 0;
    f.prepare(async () => {
      const result = caseReady(); if (++calls === 2 && result.status === 'ready') {
        if (change === 'digest') result.examples = { ...caseDocument, digest: 'new' };
        if (change === 'id') result.examples = { ...caseDocument, items: { 'zh-CN': [] } };
        if (change === 'version') result.version = '2'; if (change === 'scope') result.scope = 'user'; if (change === 'name') result.name = 'other';
      }
      return { ok: true, value: result };
    });
    await expect(f.controller.services('session').useSkill('source', 'item', new AbortController().signal, undefined,
      async () => ({ locale: 'zh-CN', id: 'summary' }))).rejects.toThrow();
    expect(f.state()).toBe(f.original); expect(f.counts().revealed).toBe(0);
  });
}
test('final case preparation CAS rejects edits after confirmation', async () => {
  const f = fixture(); const gate = deferred<void>(); let calls = 0;
  f.prepare(async () => { if (++calls === 2) await gate.promise; return { ok: true, value: caseReady() }; });
  const use = f.controller.services('session').useSkill('source', 'item', new AbortController().signal, undefined, async () => ({ locale: 'zh-CN', id: 'summary' }));
  await Bun.sleep(0); f.edit('final user edit'); gate.resolve(); await expect(use).rejects.toThrow('草稿已被编辑'); expect(f.state().draft).toBe('final user edit');
});
test('cancelled selection never edits, and custom selection is reverified without author prompt', async () => {
  const f = fixture(); f.prepare(async () => ({ ok: true, value: caseReady() }));
  await expect(f.controller.services('session').useSkill('source', 'item', new AbortController().signal, undefined, async () => { throw new Error('cancel'); })).rejects.toThrow('cancel');
  expect(f.counts().inserted).toBe(0);
  await f.controller.services('session').useSkill('source', 'item', new AbortController().signal, undefined, async () => ({ custom: true }));
  expect(f.state().draft).toContain('请补充任务目标和输入材料'); expect(f.state().draft).not.toContain('检查 {{other}}');
});

test('explicit empty locale skips case selector, unavailable is distinct and custom remains verified', async () => {
  const f = fixture({ locale: () => 'zh-Hant-TW' }); f.prepare(async () => ({ ok: true, value: caseReady() }));
  await f.controller.services('session').useSkill('source', 'item', new AbortController().signal, undefined, async () => { throw new Error('must not show empty'); });
  expect(f.counts().inserted).toBe(1);
  const g = fixture(); let reads = 0; g.prepare(async () => { reads++; return { ok: true, value: caseReady({ state: 'unavailable', retryable: true }) }; });
  await g.controller.services('session').useSkill('source', 'item', new AbortController().signal, undefined, async (items, locale) => { expect(items).toEqual([]); expect(locale).toBe(''); return { custom: true }; });
  expect(reads).toBe(2); expect(g.counts().inserted).toBe(1);
});
test('Host locale change cancels pending case choice even when language switches back', async () => {
  let language = 'zh-CN'; let changed: (() => void) | undefined; let stopped = false;
  const f = fixture({ locale: () => language, subscribeLocale: callback => { changed = callback; return () => { stopped = true; }; } });
  f.prepare(async () => ({ ok: true, value: caseReady() }));
  const use = f.controller.services('session').useSkill('source', 'item', new AbortController().signal, undefined, async (_items, _locale, signal) => {
    language = 'en'; changed?.(); language = 'zh-CN'; expect(signal.aborted).toBe(true); return { custom: true };
  });
  await expect(use).rejects.toThrow(); expect(stopped).toBe(true); expect(f.counts().inserted).toBe(0);
});

test('resident inline selection survives entry abort; confirmation preserves new composer edits', async () => {
  const f = fixture(); f.prepare(async () => ({ ok: true, value: caseReady() }));
  const origin = new AbortController();
  const use = f.controller.services('session').useSkill('source', 'item', origin.signal);
  await Bun.sleep(0);
  const panel = f.controller.casePanel(); expect(panel?.name).toBe('actual-name'); expect(f.counts().inserted).toBe(0);
  origin.abort(); f.edit('selection time edit');
  panel!.finish({ locale: 'zh-CN', id: 'summary' }); await use;
  expect(f.state().draft.replace(/\n使用.*\n/u, '')).toBe('selection time edit'); expect(f.state().draft).toContain('/actual-name');
  expect(f.controller.casePanel()).toBeUndefined();
});
test('closing resident cases leaves draft unchanged and releases subscriptions', async () => {
  const f = fixture(); f.prepare(async () => ({ ok: true, value: caseReady() }));
  const use = f.use(); await Bun.sleep(0); f.controller.casePanel()!.finish();
  await expect(use).rejects.toThrow('已取消'); expect(f.state()).toBe(f.original); expect(f.released()).toBe(1);
});
test('context release cancels pending resident case promise without inserting', async () => {
  let changed: (() => void) | undefined;
  const f = fixture({ subscribeContext: listener => { changed = listener; return () => { changed = undefined; }; } });
  f.prepare(async () => ({ ok: true, value: caseReady() }));
  const use = f.use(); await Bun.sleep(0); f.invalidate(); changed!();
  await expect(use).rejects.toThrow(); expect(f.controller.casePanel()).toBeUndefined(); expect(f.counts().inserted).toBe(0); expect(changed).toBeUndefined();
});
test('duplicate concurrent conversation use cannot replace another pending case chooser', async () => {
  const f = fixture(); f.prepare(async () => ({ ok: true, value: caseReady() }));
  const use = f.use(); await Bun.sleep(0); const panel = f.controller.casePanel();
  await expect(f.use()).rejects.toThrow('当前技能'); expect(f.controller.casePanel()).toBe(panel);
  panel!.finish({ custom: true }); await use;
});
test('catalog handoff deduplicates creation, binds target only and ignores old entry unmount after navigation', async () => {
  const created = deferred<{ sessionId: string; itemKey: string }>(); const origin = new AbortController();
  let creations = 0; const prepared: string[][] = [];
  const f = fixture({ newConversation: async (_session, _revision, _key, signal) => { creations++; expect(signal.aborted).toBe(false); return created.promise; },
    prepare: async (session, _revision, key) => { prepared.push([session, key]); return { ok: true, value: caseReady() }; } });
  const use = f.controller.catalogServices('session').useSkill('source', 'old-key', origin.signal);
  const repeated = f.controller.catalogServices('session').useSkill('source', 'old-key', origin.signal);
  expect(creations).toBe(1);
  f.controller.bind('new', f.actions); created.resolve({ sessionId: 'new', itemKey: 'fresh-key' }); origin.abort();
  await Bun.sleep(0); expect(f.controller.casePanel()?.sessionId).toBe('new');
  f.controller.casePanel()!.finish({ locale: 'zh-CN', id: 'summary' }); await Promise.all([use, repeated]);
  expect(prepared).toEqual([['new', 'fresh-key'], ['new', 'fresh-key']]);
});
test('catalog create failure preserves old draft and never prepares or inserts', async () => {
  const f = fixture({ newConversation: async () => { throw new Error('新对话创建失败'); } });
  await expect(f.controller.catalogServices('session').useSkill('source', 'old-key', new AbortController().signal)).rejects.toThrow('创建失败');
  expect(f.state()).toBe(f.original); expect(f.counts().inserted).toBe(0);
});
test('catalog navigation superseded before composer binds never writes to the target or old draft', async () => {
  const f = fixture({ newConversation: async () => ({ sessionId: 'not-current', itemKey: 'new' }), current: () => false });
  await expect(f.controller.catalogServices('session').useSkill('source', 'old-key', new AbortController().signal)).rejects.toThrow('变化');
  expect(f.state()).toBe(f.original);
});

test('post-navigation skill verification failure is visible in the resident composer, not a vanished catalog button', async () => {
  const f = fixture({ newConversation: async () => ({ sessionId: 'fresh', itemKey: 'new-key' }),
    prepare: async () => ({ ok: true, value: { status: 'blocked', reason: 'skill-shadowed' } }) });
  f.controller.bind('fresh', f.actions);
  await expect(f.controller.catalogServices('session').useSkill('source', 'old-key', new AbortController().signal)).rejects.toThrow('同名');
  expect(f.controller.casePanel()).toMatchObject({ sessionId: 'fresh', busy: false }); expect(f.controller.casePanel()?.error).toContain('同名');
  expect(f.state()).toBe(f.original); f.controller.casePanel()!.finish(); expect(f.controller.casePanel()).toBeUndefined();
});
test('disposing the plugin cancels both a pending composer acquisition and a pending inline choice', async () => {
  const f = fixture({ newConversation: async () => ({ sessionId: 'not-bound', itemKey: 'new-key' }) });
  const waiting = f.controller.catalogServices('session').useSkill('source', 'old-key', new AbortController().signal);
  await Bun.sleep(0); f.controller.dispose(); await expect(waiting).rejects.toThrow(); expect(f.counts().inserted).toBe(0);
  const g = fixture(); g.prepare(async () => ({ ok: true, value: caseReady() }));
  const choosing = g.use(); await Bun.sleep(0); g.controller.dispose(); await expect(choosing).rejects.toThrow(); expect(g.counts().inserted).toBe(0);
});

for (const mode of ['case', 'custom', 'custom-with-image'] as const) {
  test(`empty new conversation ${mode} starts with the task, not a synthetic blank paragraph`, async () => {
    const f = fixture(); f.edit(''); f.position(0);
    if (mode === 'custom-with-image') f.content({ attachmentIds: ['native-image'] as never });
    const attachments = f.state().attachmentIds;
    if (mode === 'case') {
      f.prepare(async () => ({ ok: true, value: caseReady() }));
      await f.controller.services('session').useSkill('source', 'item', new AbortController().signal, undefined, async () => ({ locale: 'zh-CN', id: 'summary' }));
      expect(f.state().draft).toBe('使用 /actual-name 整理；再用 /actual-name 检查 {{other}}。');
    } else {
      await f.use(); expect(f.state().draft).toBe('请使用 /actual-name 完成本次任务。请补充任务目标和输入材料。');
    }
    expect(f.state().draft.startsWith('\n')).toBe(false); expect(f.state().draft.endsWith('\n')).toBe(false);
    expect(f.state().attachmentIds).toBe(attachments); expect(f.spans[0]).toMatchObject({ start: 0, end: 0 });
  });
}
for (const [draft, start, end, expected] of [
  ['已有材料', 4, 4, '已有材料\nTASK'],
  ['已有材料\n', 5, 5, '已有材料\nTASK'],
  ['已有材料\n\n', 6, 6, '已有材料\n\nTASK'],
  ['前文后文', 1, 2, '前文\nTASK\n后文'],
  ['前文\n后文', 1, 2, '前文\nTASK\n后文'],
  ['前文\n后文', 2, 3, '前文\nTASK\n后文'],
  ['后文', 0, 0, 'TASK\n后文'],
] as const) {
  test(`native selection ${start}-${end} in ${JSON.stringify(draft)} adds only missing separators`, async () => {
    const f = fixture(); f.edit(draft); f.position(start, end);
    const examples = { ...caseDocument, items: { 'zh-CN': [{ id: 'task', title: '任务', prompt: 'TASK {{skill}}' }] } };
    f.prepare(async () => ({ ok: true, value: caseReady(examples) }));
    const rev = f.state().draftRev;
    await f.controller.services('session').useSkill('source', 'item', new AbortController().signal, undefined, async () => ({ locale: 'zh-CN', id: 'task' }));
    expect(f.state().draft).toBe(expected.replace('TASK', 'TASK /actual-name')); expect(f.spans[0]).toEqual({ start: end, end, draftRev: rev });
  });
}
test('author prompt whitespace remains literal; separators never trim or normalize its body', () => {
  const empty = { draft: '', occurrences: [] };
  expect(skillTaskInsertion('\n  TASK  \n', empty, 0)).toBe('\n  TASK  \n');
  expect(skillTaskInsertion('\nTASK\n', { draft: 'beforeafter', occurrences: [] }, 6)).toBe('\nTASK\n');
});
test('native chip detect boundaries differ from expanded clipboard text without changing insertion spans', async () => {
  const occurrence = { occurrenceId: 1, source: 'files', ref: 'id', offset: 0, length: 21, label: 'input.txt', clipboardText: '@very-long-input-file' };
  const draft = occurrence.clipboardText + '\n'; occurrence.length = occurrence.clipboardText.length;
  expect(skillTaskInsertion('TASK', { draft, occurrences: [occurrence] }, 2)).toBe('TASK');
  expect(skillTaskInsertion('TASK', { draft, occurrences: [occurrence] }, 1)).toBe('\nTASK');
  expect(skillTaskInsertion('TASK', { draft, occurrences: [occurrence] }, 0)).toBe('TASK\n');
  const f = fixture(); f.content({ draft, occurrences: [occurrence], attachmentIds: ['image'] as never }); f.position(1, 2);
  const references = f.state().occurrences, images = f.state().attachmentIds; const rev = f.state().draftRev;
  let text = '', received: TokenSpan | undefined;
  f.actions.insertText = (value, span) => { text = value; received = span; return true; };
  await f.use(); expect(text).toBe('请使用 /actual-name 完成本次任务。请补充任务目标和输入材料。');
  expect(received).toEqual({ start: 2, end: 2, draftRev: rev }); expect(f.state().draft).toBe(draft);
  expect(f.state().occurrences).toBe(references); expect(f.state().attachmentIds).toBe(images);
});


test('separator-only projection handles multiple native refs, emoji and Chinese without moving the captured boundary', () => {
  const a = '@很长的输入文件.md', b = '@另一个文件.txt';
  const first = { occurrenceId: 1, source: 'files', ref: 'one', offset: 3, length: a.length, label: 'one', clipboardText: a };
  const second = { ...first, occurrenceId: 2, ref: 'two', offset: 3 + a.length + 2, length: b.length, label: 'two', clipboardText: b };
  const state = { draft: `前😀${a}中\n${b}\n尾`, occurrences: [first, second] };
  for (const [at, expected] of [[0, 'TASK\n'], [3, '\nTASK\n'], [4, '\nTASK\n'], [6, 'TASK\n'], [7, '\nTASK'], [8, 'TASK\n'], [9, '\nTASK']] as const) {
    expect(skillTaskInsertion('TASK', state, at)).toBe(expected);
  }
  const end = { draft: `前😀${a}中\n${b}\n`, occurrences: [first, second] };
  expect(skillTaskInsertion('TASK', end, 8)).toBe('TASK');
  expect(end.occurrences[0]).toBe(first); expect(end.occurrences[1]).toBe(second);
});
test('selected authored leading and trailing newlines remain intact in an empty new conversation', async () => {
  const f = fixture(); f.edit(''); f.position(0);
  f.prepare(async () => ({ ok: true, value: caseReady({ ...caseDocument, items: { 'zh-CN': [{ id: 'spaced', title: '作者排版', prompt: '\n  {{skill}} 完成任务  \n' }] } }) }));
  await f.controller.services('session').useSkill('source', 'item', new AbortController().signal, undefined, async () => ({ locale: 'zh-CN', id: 'spaced' }));
  expect(f.state().draft).toBe('\n  /actual-name 完成任务  \n');
});

test('verified modal entry hands off after detaching its abort signal and before showing resident case choices', async () => {
  const f = fixture(); const entry = new AbortController(); let preparations = 0, closed = false;
  f.prepare(async () => { preparations++; return { ok: true, value: caseReady() }; });
  const request = f.controller.services('session').useSkill('source', 'item', entry.signal, undefined, undefined, () => {
    expect(preparations).toBe(1); expect(f.counts().inserted).toBe(0); expect(f.controller.casePanel()).toBeUndefined();
    closed = true; entry.abort();
  });
  await Bun.sleep(0); expect(closed).toBe(true); expect(entry.signal.aborted).toBe(true);
  expect(f.controller.casePanel()?.name).toBe('actual-name'); expect(f.counts().inserted).toBe(0);
  f.controller.casePanel()!.finish({ locale: 'zh-CN', id: 'summary' }); await request;
  expect(preparations).toBe(2); expect(f.counts().inserted).toBe(1);
});
test('modal entry remains open until initial verification resolves; failed verification never invokes handoff', async () => {
  const f = fixture(); const gate = deferred<void>(); let closed = false;
  f.prepare(async () => { await gate.promise; return { ok: true, value: { status: 'blocked', reason: 'files-changed' } }; });
  const request = f.controller.services('session').useSkill('source', 'item', new AbortController().signal, undefined, undefined, () => { closed = true; });
  await Bun.sleep(0); expect(closed).toBe(false); expect(f.controller.casePanel()).toBeUndefined();
  gate.resolve(); await expect(request).rejects.toThrow('文件'); expect(closed).toBe(false); expect(f.counts().inserted).toBe(0);
});
test('case cancellation after modal handoff does not edit or reopen the original detail', async () => {
  const f = fixture(); f.prepare(async () => ({ ok: true, value: caseReady() }));
  const requests = new SkillUseButtonRequests(); let closed = 0, success = 0;
  const request = requests.run(signal => f.controller.services('session').useSkill('source', 'item', signal, undefined, undefined, () => { closed++; requests.reset(); }), () => { success++; });
  await Bun.sleep(0); expect(closed).toBe(1); f.controller.casePanel()!.finish(); await request;
  expect(closed).toBe(1); expect(success).toBe(0); expect(f.state()).toBe(f.original); expect(f.controller.casePanel()).toBeUndefined();
});
test('no-case entry uses final success callback and does not close via early handoff', async () => {
  const f = fixture(); const requests = new SkillUseButtonRequests(); let handoffs = 0, success = 0;
  await requests.run(signal => f.controller.services('session').useSkill('source', 'item', signal, undefined, undefined, () => { handoffs++; }), () => { success++; });
  expect(handoffs).toBe(0); expect(success).toBe(1); expect(f.counts().inserted).toBe(1);
});
test('modal handoff cannot bypass a session change, and final CAS still protects edits', async () => {
  const f = fixture(); f.prepare(async () => ({ ok: true, value: caseReady() }));
  await expect(f.controller.services('session').useSkill('source', 'item', new AbortController().signal, undefined, undefined, () => { f.invalidate(); })).rejects.toThrow();
  expect(f.controller.casePanel()).toBeUndefined(); expect(f.counts().inserted).toBe(0);
  const g = fixture(); const entry = new AbortController(), gate = deferred<void>(); let reads = 0;
  g.prepare(async () => { if (++reads === 2) await gate.promise; return { ok: true, value: caseReady() }; });
  const request = g.controller.services('session').useSkill('source', 'item', entry.signal, undefined, undefined, () => { entry.abort(); });
  await Bun.sleep(0); g.controller.casePanel()!.finish({ locale: 'zh-CN', id: 'summary' }); await Bun.sleep(0);
  g.edit('保留核验期间编辑'); gate.resolve(); await expect(request).rejects.toThrow('草稿已被编辑'); expect(g.state().draft).toBe('保留核验期间编辑');
});

test('shadowed guidance does not invent the winning scope or silently choose another skill', async () => {
  const f = fixture(); f.blocked('skill-shadowed');
  await expect(f.use()).rejects.toThrow('另一份同名技能');
  try { await f.use(); } catch (error) {
    expect(String(error)).not.toContain('当前项目');
    expect(String(error)).toContain('所选安装');
  }
  expect(f.state()).toBe(f.original); expect(f.counts().inserted).toBe(0);
});

test('historical use never falls back from a disabled location to a same-scope sibling', () => {
  const item = { itemKey: 'old', namespace: 'global', slug: 'notes', version: '1.0.0', scope: 'user' as const, state: 'present' as const, installationState: 'disabled' as const };
  const inventory = { items: [item], complete: true, unavailable: [], issues: [] };
  expect(exactInstalledSkill(inventory, item)).toBeUndefined();
  expect(exactInstalledSkill({ ...inventory, items: [item, { ...item, itemKey: 'other', installationState: 'enabled' }] }, item)).toBeUndefined();
});

// Skill-level entry resolves only on the Host, then follows the existing strict handoff.
for (const reason of ['no-installation', 'no-usable-installation', 'selection-unverified', 'multiple-usable-installations', 'selection-timeout'] as const) {
  test(`skill selector ${reason} never creates a conversation or edits a draft`, async () => {
    const { selectSkillItem } = await import('../src/client/skill-primary-actions.js');
    let created = 0;
    const f = fixture({ newConversation: async () => { created++; throw new Error('must not create'); } });
    await expect(f.controller.catalogServices('session').useSkill('source', undefined, new AbortController().signal,
      signal => selectSkillItem(async () => ({ ok: true, value: { status: 'blocked', reason } }), 'source', 'demo', 'skill', signal))).rejects.toThrow();
    expect(created).toBe(0); expect(f.state()).toBe(f.original); f.controller.dispose();
  });
}
test('skill selector ready passes its exact key into handoff and still rechecks before offering a task', async () => {
  const { selectSkillItem } = await import('../src/client/skill-primary-actions.js');
  const selected: string[] = [], prepared: string[] = [];
  const f = fixture({ newConversation: async (_session, _revision, key) => { selected.push(key); return { sessionId: 'fresh', itemKey: 'rebound-exact-key' }; },
    prepare: async (_session, _revision, key) => { prepared.push(key); return { ok: true, value: caseReady() }; } });
  f.controller.bind('fresh', f.actions);
  const use = f.controller.catalogServices('session').useSkill('source', undefined, new AbortController().signal,
    signal => selectSkillItem(async () => ({ ok: true, value: { status: 'ready', itemKey: 'host-unique-key' } }), 'source', 'demo', 'skill', signal));
  await Bun.sleep(0);
  expect(selected).toEqual(['host-unique-key']); expect(prepared).toEqual(['rebound-exact-key']);
  f.controller.casePanel()!.finish(); await expect(use).rejects.toThrow('取消');
  expect(f.state()).toBe(f.original); expect(f.counts().inserted).toBe(0); f.controller.dispose();
});
