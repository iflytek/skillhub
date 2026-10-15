import { useCallback, useState, useSyncExternalStore, type FormEvent } from 'react';
import type { ConfigForm } from '@deepseek-ai/dsh-client-ui-settings/client';
import type { SkillHubConfig, TabMode } from '../host/types.js';

const modules = [
  ['versions', '版本'], ['securityAudit', '安全审核'], ['reviews', '评论'],
] as const;

export function ConfigPage({ form }: { form: ConfigForm<SkillHubConfig> }) {
  const subscribe = useCallback((listener: () => void) => form.subscribe(listener), [form]);
  const read = useCallback(() => form.getSnapshot(), [form]);
  const state = useSyncExternalStore(subscribe, read);
  const [draft, setDraft] = useState<{ value: SkillHubConfig; revision: number }>();
  const [pending, setPending] = useState(false);
  const [message, setMessage] = useState('');
  const value = draft?.value ?? state.value;

  if (state.status === 'loading') return <p role="status">正在读取配置…</p>;
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
        setMessage('配置未保存，可能已被其他页面修改。请重新加载后再试。');
      }
    } catch {
      setMessage('保存失败，请检查连接后重试。');
    } finally { setPending(false); }
  }

  return <form onSubmit={event => void save(event)} style={{ maxWidth: 680, paddingBlock: 20 }} aria-label="SkillHub 配置">
    <h2>连接 SkillHub</h2>
    <p>浏览和安装该实例公开的技能，无需登录。</p>
    {!state.writable && <p role="status">当前 Host 配置为只读。</p>}
    <fieldset disabled={!state.writable || pending} style={{ border: 0, padding: 0, display: 'grid', gap: 18 }}>
      <label style={{ display: 'grid', gap: 8 }}>Registry 地址
        <input type="url" value={value.registryUrl} placeholder="https://your-skillhub.example"
          onChange={event => change({ ...value, registryUrl: event.target.value })}
          style={{ padding: 10, border: '1px solid currentColor', borderRadius: 6, background: 'transparent', color: 'inherit' }} />
      </label>
      <p>留空则断开来源。修改来源后需重新读取技能列表。</p>
      <h3>技能详情 Tab</h3>
      {modules.map(([key, label]) => <label key={key} style={{ display: 'flex', justifyContent: 'space-between', gap: 16 }}>
        {label}
        <select value={value.detailTabs[key]} onChange={event => change({
          ...value, detailTabs: { ...value.detailTabs, [key]: event.target.value as TabMode },
        })}>
          <option value="auto">自动检测</option><option value="show">显示</option><option value="hide">隐藏</option>
        </select>
      </label>)}
      <p>“显示”不会改变服务端访问规则；评论仅供阅读。</p>
      <div style={{ display: 'flex', gap: 12 }}>
        <button type="submit" disabled={!draft}>{pending ? '保存中…' : '保存配置'}</button>
        <button type="button" onClick={() => { setDraft(undefined); setMessage('已重新读取当前配置。'); }}>重新加载</button>
      </div>
    </fieldset>
    <p role="status" aria-live="polite">{message}</p>
  </form>;
}
