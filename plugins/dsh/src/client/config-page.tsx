import { editSavedRegistry, registryChoices, preserveCurrentRegistry, RegistryConfigError, type SavedRegistry, sameRegistry, validateRegistryConfig } from './registry-config.js';
import { catalogErrorMessage } from './catalog-error.js';
import { Button, Card, CardHeader, CardContent, Input, SelectField, Skeleton, Tabs, TabsList, TabsTrigger, TabsContent } from './ui.js';
import { ConfigStyles } from './config-styles.js';
import { NativeControlStyles } from './native-control-styles.js';
import { useCallback, useEffect, useId, useRef, useState, useSyncExternalStore, type FormEvent } from 'react';
import type { ConfigForm } from '@deepseek-ai/dsh-client-ui-settings/client';
import type { RegistryConnectionTest, SkillHubConfig, TabMode } from '../host/types.js';

const modules = [
  ['versions', '版本', '查看发布版本、更新说明及可下载状态。'],
  ['securityAudit', '安全审核', '展示来源提供的公开审核信息。'],
  ['reviews', '评论', '阅读其他用户的评价与使用反馈。'],
] as const;

type ConnectionResponse = { ok: true; value: RegistryConnectionTest } | { ok: false; error: { code: string; details?: unknown } };
export function ConfigPage({ form, testConnection }: { form: ConfigForm<SkillHubConfig>;
  testConnection(url: string, signal: AbortSignal): Promise<ConnectionResponse>;
}) {
  const id = useId();
  const subscribe = useCallback((listener: () => void) => form.subscribe(listener), [form]);
  const read = useCallback(() => form.getSnapshot(), [form]);
  const state = useSyncExternalStore(subscribe, read);
  const [draft, setDraft] = useState<{ value: SkillHubConfig; revision: number }>();
  const [pending, setPending] = useState(false);
  const [message, setMessage] = useState('');
  const [messageTone, setMessageTone] = useState<'success' | 'error' | 'info'>('info');
  const [tab, setTab] = useState('source');
  const [editingSource, setEditingSource] = useState<number | null>(null);
  const [activeEditor, setActiveEditor] = useState<number | null>(null);
  const editorRevision = useRef(state.revision);
  useEffect(() => {
    if (editorRevision.current === state.revision) return;
    editorRevision.current = state.revision;
    if (!draft) { setEditingSource(null); setActiveEditor(null); }
  }, [state.revision, draft]);
  const value = draft?.value ?? state.value;
  const registryUrl = value?.registryUrl.trim() ?? '';
  const request = useRef<AbortController>();
  const [connection, setConnection] = useState<{ url: string; pending: boolean; passed?: boolean; message: string }>();
  useEffect(() => {
    request.current?.abort();
    request.current = undefined;
    setConnection(undefined);
    return () => { request.current?.abort(); };
  }, [registryUrl]);

  async function test() {
    if (!registryUrl || request.current) return;
    const controller = new AbortController();
    request.current = controller;
    setConnection({ url: registryUrl, pending: true, message: '正在测试公开目录连接，最长 10 秒…' });
    try {
      const result = await testConnection(registryUrl, controller.signal);
      if (controller.signal.aborted || request.current !== controller) return;
      setConnection({ url: registryUrl, pending: false, passed: result.ok, message: result.ok
        ? `连接成功 · ${result.value.mode === 'paged' ? '公开目录可用' : '仅支持基础搜索'} · ${result.value.elapsedMs} ms`
        : catalogErrorMessage(result.error) });
    } catch {
      if (!controller.signal.aborted && request.current === controller) {
        setConnection({ url: registryUrl, pending: false, passed: false, message: '连接失败或超时，请检查地址与 Host 网络后重试。' });
      }
    } finally {
      if (request.current === controller) request.current = undefined;
    }
  }
  const visibleConnection = connection?.url === registryUrl ? connection : undefined;

  if (state.status === 'loading') return <section className="skillhub-controls astron-sh-config" aria-label="SkillHub 配置加载">
    <NativeControlStyles /><ConfigStyles /><h2>SkillHub 设置</h2><p role="status">正在读取配置…</p>
    <div className="astron-sh-config-stack" aria-busy="true">{[0, 1].map(key => <Card key={key} aria-hidden="true"><CardHeader><Skeleton className="astron-sh-config-skeleton-title" /><Skeleton /></CardHeader><CardContent className="astron-sh-config-skeleton-content"><Skeleton /><Skeleton /><Skeleton /></CardContent></Card>)}</div>
  </section>;
  if (!value || state.status === 'unavailable') return <p role="status">当前连接无法编辑 Host 配置。</p>;

  const sources = registryChoices(value);

  function change(next: SkillHubConfig) {
    if (state.revision === undefined) return;
    const current = state.value;
    const unchanged = current && next.registryUrl === current.registryUrl
      && modules.every(([key]) => next.detailTabs[key] === current.detailTabs[key])
      && (next.savedRegistries?.length ?? 0) === (current.savedRegistries?.length ?? 0)
      && (next.savedRegistries ?? []).every((entry, index) => entry.name === current.savedRegistries?.[index]?.name && entry.url === current.savedRegistries?.[index]?.url);
    setDraft(unchanged && (!draft || draft.revision === state.revision) ? undefined : { value: next, revision: draft?.revision ?? state.revision });
    setMessage(''); setMessageTone('info');
  }

  function editSource(index: number, next: SavedRegistry) {
    const active = activeEditor === index || sameRegistry(value?.registryUrl, value?.savedRegistries?.[index]?.url);
    if (active) setActiveEditor(index);
    const changed = editSavedRegistry(value!, index, next);
    change(active ? { ...changed, registryUrl: next.url } : changed);
  }

  async function save(event: FormEvent) {
    event.preventDefault();
    if (!draft || !state.writable || pending) return;
    let validated: SkillHubConfig;
    try { validated = validateRegistryConfig(draft.value); }
    catch (error) {
      setTab('source'); setMessageTone('error');
      if (error instanceof RegistryConfigError && error.savedIndex !== undefined) setEditingSource(error.savedIndex);
      else {
        const next = preserveCurrentRegistry(draft.value);
        setDraft({ value: next, revision: draft.revision });
        setEditingSource(next.savedRegistries?.findIndex(entry => sameRegistry(entry.url, next.registryUrl)) ?? null);
      }
      setMessage(error instanceof Error ? error.message : '请检查来源地址。'); return;
    }
    setPending(true);
    try {
      const accepted = await form.mutate([
        { op: 'set', path: ['registryUrl'], value: validated.registryUrl },
        { op: 'set', path: ['savedRegistries'], value: validated.savedRegistries },
        ...modules.map(([key]) => ({ op: 'set' as const, path: ['detailTabs', key], value: draft.value.detailTabs[key] })),
      ], draft.revision);
      if (accepted) {
        setDraft(undefined); setEditingSource(null); setActiveEditor(null);
        setMessageTone('success'); setMessage('配置已保存。');
      } else {
        setMessageTone('error'); setMessage('配置未保存，可能已被其他页面修改。请重置修改后再试。');
      }
    } catch {
      setMessageTone('error'); setMessage('保存失败，请检查连接后重试。');
    } finally { setPending(false); }
  }

  return <form className="skillhub-controls astron-sh-config" noValidate onSubmit={event => void save(event)} aria-label="SkillHub 配置">
    <NativeControlStyles /><ConfigStyles />
    <header className="astron-sh-config-heading"><h2>SkillHub 设置</h2><p>连接技能来源，调整详情页展示。</p></header>
    {!state.writable && <p className="astron-sh-config-notice" role="status">当前 Host 配置为只读。</p>}
    <Tabs value={tab} onValueChange={setTab}>
      <TabsList className="astron-sh-config-tabs" aria-label="设置分类"><TabsTrigger value="source">技能来源</TabsTrigger><TabsTrigger value="display">页面显示</TabsTrigger></TabsList>
      <fieldset disabled={!state.writable || pending} className="astron-sh-config-stack">
      <TabsContent value="source" className="astron-sh-config-panel"><Card className="astron-sh-config-content-card">
        <CardHeader><div className="astron-sh-config-section-heading"><div><h3>技能来源</h3><p>选择一个来源，保存后生效。</p></div>
          <Button size="sm" variant="outline" onClick={() => {
            const next = preserveCurrentRegistry(value);
            const index = next.savedRegistries?.length ?? 0;
            change({ ...next, savedRegistries: [...(next.savedRegistries ?? []), { name: '', url: '' }] }); setEditingSource(index);
          }}>添加来源</Button></div></CardHeader>
        <CardContent><div className="astron-sh-source-list" role="radiogroup" aria-label="技能来源">
          {sources.length === 0 && <p className="astron-sh-config-empty">尚未添加来源。</p>}
          {sources.map((entry, rowIndex) => {
            const selected = sameRegistry(entry.url, value.registryUrl);
            const current = sameRegistry(entry.url, state.value?.registryUrl);
            const index = entry.savedIndex;
            const editing = index !== null && index === editingSource;
            const key = index === null ? 'legacy' : `saved-${index}`;
            return <div className="astron-sh-source-entry" key={key} data-selected={selected}>
              <div className="astron-sh-source-row"><label className="astron-sh-source-choice" htmlFor={`${id}-${key}`}>
                <input id={`${id}-${key}`} type="radio" name={`${id}-source`} aria-label={`选择来源 ${rowIndex + 1}：${entry.name || '新来源'}`} checked={selected} disabled={!entry.url.trim()}
                  onChange={() => { setActiveEditor(null); change({ ...preserveCurrentRegistry(value), registryUrl: entry.url }); }} />
                <span className="astron-sh-source-text"><strong>{entry.name || '新来源'}</strong><span>{entry.url || '填写名称和地址'}</span></span>
                {(selected || current) && <span className="astron-sh-config-state">{selected && !current ? '待保存' : '当前使用'}</span>}
              </label><div className="astron-sh-source-actions">
                <Button size="sm" variant="ghost" aria-label={`编辑来源 ${rowIndex + 1}`} aria-expanded={editing} onClick={() => {
                  if (index === null) { const next = preserveCurrentRegistry(value); const nextIndex = (next.savedRegistries?.length ?? 1) - 1; change(next); setEditingSource(nextIndex); setActiveEditor(nextIndex); }
                  else { setEditingSource(editing ? null : index); if (selected) setActiveEditor(index); }
                }}>{editing ? '收起' : '编辑'}</Button>
                <Button size="sm" variant="ghost" aria-label={`移除来源 ${rowIndex + 1}`} onClick={() => {
                  change(index === null ? { ...value, registryUrl: '' } : editSavedRegistry(value, index));
                  setEditingSource(previous => index === null || previous === null ? previous : previous === index ? null : previous > index ? previous - 1 : previous);
                  setActiveEditor(previous => index === null || previous === null ? previous : previous === index ? null : previous > index ? previous - 1 : previous);
                }}>移除</Button>
              </div></div>
              {editing && <div className="astron-sh-source-editor" role="group" aria-label={`编辑来源 ${rowIndex + 1} 表单`}>
                <label>名称<Input aria-label={`来源 ${rowIndex + 1} 名称`} value={entry.name} onChange={event => editSource(index, { name: event.target.value, url: entry.url })} /></label>
                <label>Registry 地址<Input aria-label={`来源 ${rowIndex + 1} 地址`} type="url" value={entry.url} placeholder="https://your-skillhub.example" onChange={event => editSource(index, { name: entry.name, url: event.target.value })} /></label>
              </div>}
            </div>;
          })}
          <label className="astron-sh-source-disconnect" htmlFor={`${id}-disconnected`}><input id={`${id}-disconnected`} type="radio" name={`${id}-source`} checked={!registryUrl}
            onChange={() => { setActiveEditor(null); change({ ...preserveCurrentRegistry(value), registryUrl: '' }); }} /><span>不连接技能来源</span>
            {!registryUrl && <span className="astron-sh-config-state">{state.value?.registryUrl.trim() ? '待保存' : '当前使用'}</span>}
          </label>
        </div>
        <div className="astron-sh-source-footer"><p className="astron-sh-config-help">切换或移除来源不会删除已安装技能。</p>
          {registryUrl && <Button size="sm" variant="outline" disabled={visibleConnection?.pending} onClick={() => void test()}>{visibleConnection?.pending ? '测试中…' : '测试连接'}</Button>}
        </div>
        {visibleConnection && <p className="astron-sh-config-connection-result" role="status" aria-live="polite" data-passed={visibleConnection.passed}>{visibleConnection.message}</p>}
        </CardContent></Card></TabsContent>
      <TabsContent value="display" className="astron-sh-config-panel"><Card className="astron-sh-config-content-card"><CardHeader><div className="astron-sh-config-section-heading"><div><h3>详情页内容</h3><p>默认按来源能力自动显示。</p></div></div></CardHeader>
        <CardContent><div className="astron-sh-config-modules">{modules.map(([key, label, description]) => <div key={key} className="astron-sh-config-module">
          <div><strong>{label}</strong><p>{description}</p></div><SelectField label={label} value={value.detailTabs[key]} disabled={!state.writable || pending} onValueChange={mode => change({
            ...value, detailTabs: { ...value.detailTabs, [key]: mode as TabMode },
          })} options={[{ value: 'auto', label: '自动检测' }, { value: 'show', label: '显示' }, { value: 'hide', label: '隐藏' }]} />
        </div>)}</div></CardContent></Card>
      </TabsContent>
      <div className="astron-sh-config-actions"><Button size="sm" type="submit" variant="default" disabled={!draft}>{pending ? '保存中…' : '保存配置'}</Button>
        <Button size="sm" variant="ghost" disabled={!draft} onClick={() => { request.current?.abort(); request.current = undefined; setConnection(undefined); setDraft(undefined); setEditingSource(null); setActiveEditor(null); setMessageTone('info'); setMessage('已重置为当前配置，未保存的修改已清除。'); }}>重置修改</Button>
        <span>{draft ? '有未保存的修改' : '修改后保存生效'}</span>
      </div>
      </fieldset>
    </Tabs>
    <p className="astron-sh-config-feedback" role={messageTone === 'error' ? 'alert' : 'status'} aria-live="polite" data-tone={messageTone}>{message}</p>
  </form>;
}
