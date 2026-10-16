import { catalogErrorMessage } from './catalog-error.js';
import { Button, Card, CardHeader, CardTitle, CardDescription, CardContent, Input, SelectField, Skeleton } from './ui.js';
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

  function change(next: SkillHubConfig) {
    if (state.revision === undefined) return;
    setDraft({ value: next, revision: draft?.revision ?? state.revision });
    setMessage('');
  }

  async function save(event: FormEvent) {
    event.preventDefault();
    if (!draft || !state.writable || pending) return;
    const registryUrl = draft.value.registryUrl.trim();
    if (registryUrl) {
      try {
        const url = new URL(registryUrl);
        if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.search || url.hash) throw new Error();
      } catch {
        setMessage('请输入 HTTP 或 HTTPS 实例地址，不包含凭证、查询参数或片段。');
        return;
      }
    }
    setPending(true);
    try {
      const accepted = await form.mutate([
        { op: 'set', path: ['registryUrl'], value: registryUrl },
        ...modules.map(([key]) => ({ op: 'set' as const, path: ['detailTabs', key], value: draft.value.detailTabs[key] })),
      ], draft.revision);
      if (accepted) {
        setDraft(undefined);
        setMessage('配置已保存。');
      } else {
        setMessage('配置未保存，可能已被其他页面修改。请重置修改后再试。');
      }
    } catch {
      setMessage('保存失败，请检查连接后重试。');
    } finally { setPending(false); }
  }

  return <form className="skillhub-controls astron-sh-config" onSubmit={event => void save(event)} aria-label="SkillHub 配置">
    <NativeControlStyles /><ConfigStyles />
    <header className="astron-sh-config-heading"><h2>SkillHub 设置</h2><p>管理技能来源和详情页面的展示内容。</p></header>
    {!state.writable && <p className="astron-sh-config-notice" role="status">当前 Host 配置为只读。</p>}
    <fieldset disabled={!state.writable || pending} className="astron-sh-config-stack">
      <Card><CardHeader><div className="astron-sh-config-card-heading"><CardTitle>技能来源</CardTitle><span>{state.value?.registryUrl.trim() ? '已配置' : '未配置'}</span></div><CardDescription>浏览和安装实例公开的技能，无需登录。</CardDescription></CardHeader>
        <CardContent><label className="astron-sh-config-field" htmlFor={`${id}-registry`}>Registry 地址</label>
          <Input id={`${id}-registry`} type="url" value={value.registryUrl} placeholder="https://your-skillhub.example" aria-describedby={`${id}-registry-help`}
            onChange={event => change({ ...value, registryUrl: event.target.value })} />
          <div className="astron-sh-config-connection"><Button size="sm" variant="outline" disabled={!registryUrl || visibleConnection?.pending} onClick={() => void test()}>{visibleConnection?.pending ? '测试中…' : '测试连接'}</Button>
            <span>{registryUrl !== (state.value?.registryUrl.trim() ?? '') ? '测试当前输入，尚未保存' : '测试当前来源'}</span>
          </div>
          {visibleConnection && <p className="astron-sh-config-connection-result" role="status" aria-live="polite" data-passed={visibleConnection.passed}>{visibleConnection.message}</p>}
          <p className="astron-sh-config-help">测试不会保存地址，连接成功仅表示公开目录可读。</p>
          <p id={`${id}-registry-help`} className="astron-sh-config-help">留空可断开来源。保存新地址后需重新读取技能列表。</p>
        </CardContent></Card>
      <Card><CardHeader><CardTitle>详情标签</CardTitle><CardDescription>按来源能力展示版本、审核和评论，建议使用自动检测。</CardDescription></CardHeader>
        <CardContent><div className="astron-sh-config-modules">{modules.map(([key, label, description]) => <div key={key} className="astron-sh-config-module">
          <div><strong>{label}</strong><p>{description}</p></div><SelectField label={label} value={value.detailTabs[key]} disabled={!state.writable || pending} onValueChange={mode => change({
            ...value, detailTabs: { ...value.detailTabs, [key]: mode as TabMode },
          })} options={[{ value: 'auto', label: '自动检测' }, { value: 'show', label: '显示' }, { value: 'hide', label: '隐藏' }]} />
        </div>)}</div><p className="astron-sh-config-help">“显示”不会改变服务端访问规则；评论仅供阅读。</p></CardContent>
      </Card>
      <div className="astron-sh-config-actions"><Button size="sm" type="submit" variant="default" disabled={!draft}>{pending ? '保存中…' : '保存配置'}</Button>
        <Button size="sm" variant="outline" disabled={!draft} onClick={() => { request.current?.abort(); request.current = undefined; setConnection(undefined); setDraft(undefined); setMessage('已重置为当前配置，未保存的修改已清除。'); }}>重置修改</Button>
        <span>{draft ? '有未保存的修改' : '修改后保存生效'}</span>
      </div>
    </fieldset>
    <p className="astron-sh-config-feedback" role="status" aria-live="polite">{message}</p>
  </form>;
}
