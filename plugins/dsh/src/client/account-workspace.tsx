import { queryHasSource } from './read-cache.js';
import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import * as Menu from '@radix-ui/react-dropdown-menu';
import type { OwnedSkill } from '../host/account-types.js';
import { AccountDialog, AccountError, valueOf, roleLabels, type AccountRemote } from './account-common.js';
import { SpacePage } from './account-space.js';
import { UploadSkill } from './account-upload.js';
import { Button, Input } from './ui.js';
import { SkillWebsiteLink } from './skill-website-link.js';

export type AccountPageName = null | { space?: string };
type Modal = 'login' | 'settings' | { upload: true; initial?: string; row?: OwnedSkill };
export function AccountWorkspace({ remote, revision, source, toolbar, primary, screen, navigate, settings, open }: {
  remote: AccountRemote; revision: string; source: string; toolbar: Element | null; primary: Element | null;
  screen: AccountPageName; navigate(page: AccountPageName): void; settings?: () => void; open(ns: string, slug: string, version: string, skillId?: number, versionId?: number): void;
}) {
  const [modal, setModal] = useState<Modal | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const [theme, setTheme] = useState(() => { try { return localStorage.getItem('skillhub-appearance'); } catch { return null; } });
  const token = useRef<HTMLInputElement>(null);
  const heading = useRef<HTMLHeadingElement>(null);
  const client = useQueryClient();
  useEffect(() => {
    const clear = (error: unknown) => {
      if (!(error instanceof Error) || error.name !== 'AccountExpired') return;
      setModal(null);
      client.setQueryData(['account-identity', revision], null);
      client.removeQueries({ predicate: q => q.queryKey[0] !== 'account-identity' && queryHasSource(q.queryKey, revision) });
      void client.invalidateQueries({ queryKey: ['skillhub-context'] });
    };
    const queries = client.getQueryCache().subscribe(event => {
      if (event.type === 'updated' && event.action.type === 'error' && queryHasSource(event.query.queryKey, revision)) clear(event.action.error);
    });
    const mutations = client.getMutationCache().subscribe(event => {
      if (event.type === 'updated' && event.action.type === 'error' && queryHasSource(event.mutation.options.mutationKey ?? [], revision)) clear(event.action.error);
    });
    return () => { queries(); mutations(); };
  }, [client, revision]);
  useEffect(() => { heading.current?.focus(); }, [screen]);
  useEffect(() => {
    const section = toolbar?.closest('section');
    if (!section || !theme) return;
    section.dataset.skillhubAppearance = theme;
    return () => { delete section.dataset.skillhubAppearance; };
  }, [theme, toolbar]);
  const account = useQuery({ queryKey: ['account-identity', revision], retry: false, refetchOnWindowFocus: 'always',
    queryFn: async ({ signal }) => {
      const result = await remote.accountIdentity(revision, signal);
      if (!result.ok) void client.invalidateQueries({ queryKey: ['skillhub-context'] });
      return valueOf(result);
    } });
  const identity = !account.isError && account.data;
  const spaces = useQuery({ queryKey: ['account-namespaces', revision], enabled: !!identity && (!!screen || typeof modal === 'object' && modal !== null),
    retry: false, queryFn: async ({ signal }) => valueOf(await remote.accountNamespaces(revision, signal)) });
  const selected = !spaces.isError && spaces.data?.find(ns => ns.slug === screen?.space);
  const show = (m: Modal) => { setError(null); setModal(m); };
  const toggleTheme = () => {
    const dark = theme ? theme === 'dark' : (toolbar?.closest('.dark,[data-theme="dark"]') || matchMedia('(prefers-color-scheme:dark)').matches);
    const next = dark ? 'light' : 'dark'; setTheme(next);
    try { localStorage.setItem('skillhub-appearance', next); } catch { /* Session-local preference. */ }
  };
  async function connection(login: boolean) {
    if (busy) return;
    setBusy(true); setError(null);
    try {
      if (login) {
        const raw = token.current?.value.trim() ?? '';
        if (token.current) token.current.value = '';
        valueOf(await remote.connectAccount(revision, raw, new AbortController().signal));
      } else { valueOf(await remote.disconnectAccount(revision)); navigate(null); }
      setModal(null);
      client.removeQueries({ predicate: q => q.queryKey[0] !== 'skillhub-context' && queryHasSource(q.queryKey, revision) });
      await client.invalidateQueries({ queryKey: ['skillhub-context'] });
    } catch (cause) { setError(cause instanceof Error ? cause : new Error('连接失败')); }
    finally { setBusy(false); }
  }
  return <>
    <style>{`
      .astron-sh-account-page{max-width:1100px;margin:auto;padding:20px;min-width:0}
      .astron-sh-account-page header,.astron-sh-account-page article,.astron-sh-account-page nav{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap}
      .astron-sh-account-page nav{justify-content:flex-start;margin-block:16px}
      .astron-sh-account-page article{padding:14px 0;border-bottom:1px solid var(--border,currentColor);overflow-wrap:anywhere}
      .astron-sh-account-page article p{margin:.3em 0;opacity:.75}
      .astron-sh-account-form{display:grid;gap:12px;max-height:90vh;overflow:auto}
      .astron-sh-account-form label{display:grid;gap:6px}
      .astron-sh-account-menu{min-width:170px;padding:6px;border:1px solid var(--border,#ccc);border-radius:8px;background:Canvas;color:CanvasText;z-index:100}
      .astron-sh-account-menu [role=menuitem]{padding:8px;cursor:pointer}
      .astron-sh-account-menu [data-highlighted]{outline:2px solid currentColor}
      .astron-sh-catalog[data-skillhub-appearance=dark]{color-scheme:dark}
      .astron-sh-catalog[data-skillhub-appearance=light]{color-scheme:light}
      .astron-sh-workbench-frame[hidden]{display:none}
    `}</style>
    {toolbar && createPortal(<>
      <Button size="icon" variant="ghost" aria-label="切换深浅色外观" onClick={toggleTheme}><svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7">
        <path d="M20.6 14.2A9 9 0 0 1 9.8 3.4 9 9 0 1 0 20.6 14.2Z" /></svg></Button>
      {identity ? <Menu.Root><Menu.Trigger asChild><Button aria-label="账户菜单">{identity.displayName || identity.handle} ▾</Button></Menu.Trigger>
        <Menu.Portal container={toolbar}><Menu.Content className="astron-sh-account-menu" align="end" sideOffset={6}>
          <Menu.Item onSelect={() => navigate({})}>我的命名空间</Menu.Item><Menu.Item onSelect={() => show('settings')}>个人设置</Menu.Item>
          <Menu.Item onSelect={() => settings ? settings() : show('login')}>服务连接</Menu.Item>
          <Menu.Separator /><Menu.Item disabled={busy} onSelect={() => { void connection(false); }}>断开连接</Menu.Item>
        </Menu.Content></Menu.Portal></Menu.Root> : <Button onClick={() => show('login')}>登录</Button>}
    </>, toolbar)}
    {primary && createPortal(<Button variant="default" onClick={() => show(identity ? { upload: true } : 'login')}>上传技能</Button>, primary)}
    {error && !modal && <AccountError error={error} />}
    {screen && <section className="astron-sh-account-page"><nav aria-label="当前位置"><Button onClick={() => navigate(null)}>技能广场</Button>
      {screen.space && <Button onClick={() => navigate({})}>我的命名空间</Button>}</nav>
      {screen.space ? <h2 ref={heading} tabIndex={-1}>@{screen.space}</h2> : <h1 ref={heading} tabIndex={-1}>我的命名空间</h1>}
      {!identity ? <Button onClick={() => show('login')}>登录后查看</Button> : <><AccountError error={spaces.error} retry={() => { void spaces.refetch(); }} />
        {spaces.isLoading ? <p>正在读取命名空间…</p> : spaces.isError ? null : screen.space ? selected
          ? <SpacePage source={source} key={selected.slug} space={selected} revision={revision} remote={remote} handle={identity.handle}
            upload={row => show({ upload: true, initial: selected.slug, row })} open={(slug, version, skillId, versionId) => open(selected.slug, slug, version, skillId, versionId)} /> : <p>当前账号无法访问该命名空间。</p>
          : spaces.data?.length ? spaces.data.map(ns => <article key={ns.id}><div><strong>{ns.displayName}</strong><p>@{ns.slug} · {ns.status} · {roleLabels[ns.currentUserRole]}</p></div>
            <Button onClick={() => navigate({ space: ns.slug })}>进入空间</Button></article>) : <p>当前账号还没有所属命名空间。</p>}</>}
    </section>}
    {modal === 'login' && <AccountDialog close={() => setModal(null)} busy={busy} title="登录 SkillHub" description={source}>
      <form onSubmit={e => { e.preventDefault(); void connection(true); }}><label>个人 API Token<Input ref={token} type="password" required autoFocus autoComplete="off" maxLength={4096} /></label>
        <Button type="submit" variant="default" disabled={busy}>{busy ? '正在验证…' : '登录'}</Button></form>
      <SkillWebsiteLink webUrl={source + '/dashboard/tokens'} enabled label="前往来源网页获取令牌" /><AccountError error={error} /></AccountDialog>}
    {modal === 'settings' && <AccountDialog close={() => setModal(null)} title="个人设置" description={identity ? identity.handle + ' · ' + source : source}>
      <Button onClick={() => show('login')}>更换个人 Token</Button><Button onClick={toggleTheme}>切换外观</Button>
      <SkillWebsiteLink webUrl={source + '/settings/profile'} enabled label="到来源网页管理" /></AccountDialog>}
    {identity && modal && typeof modal === 'object' && (spaces.isFetching && !spaces.data || spaces.isError ?
      <AccountDialog close={() => setModal(null)} title="上传技能" description={source}>{spaces.isFetching ? '正在读取目标命名空间…' : <AccountError error={spaces.error} retry={() => { void spaces.refetch(); }} />}</AccountDialog>
      : <UploadSkill remote={remote} revision={revision} spaces={spaces.data ?? []} initial={modal.initial} row={modal.row} close={() => setModal(null)} />)}
  </>;
}
