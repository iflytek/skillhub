import { queryHasSource } from './read-cache.js';
import { useEffect, useId, useRef, useState } from 'react';
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
  const accountMenu = useRef<HTMLButtonElement>(null);
  const loginForm = useId();
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
    {toolbar && createPortal(<>
      <Button size="icon" variant="ghost" aria-label="切换深浅色外观" onClick={toggleTheme}><svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7">
        <path d="M20.6 14.2A9 9 0 0 1 9.8 3.4 9 9 0 1 0 20.6 14.2Z" /></svg></Button>
      {identity ? <Menu.Root modal={false}><Menu.Trigger asChild><Button ref={accountMenu} aria-label="账户菜单">{identity.displayName || identity.handle} ▾</Button></Menu.Trigger>
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
    {(modal === 'login' || modal === 'settings') && <AccountDialog close={() => setModal(null)} returnFocus={accountMenu.current} busy={busy}
      title={modal === 'login' ? '登录 SkillHub' : '个人设置'} description={modal === 'login' ? '使用当前来源签发的个人 API Token 登录。' : '管理当前来源的个人连接。'} closeLabel={modal === 'settings' ? '完成' : undefined}
      actions={modal === 'login' && <Button type="submit" form={loginForm} variant="default" disabled={busy}>{busy ? '正在验证…' : '登录'}</Button>}>
      {modal === 'login' ? <><dl className="astron-sh-account-source"><dt>当前技能来源</dt><dd>{source}</dd></dl>
      <form id={loginForm} onSubmit={e => { e.preventDefault(); void connection(true); }}><label htmlFor={`${loginForm}-token`}>个人 API Token</label>
        <Input id={`${loginForm}-token`} ref={token} type="password" required autoFocus autoComplete="off" spellCheck={false} maxLength={4096} disabled={busy}
          placeholder="粘贴个人 API Token" aria-invalid={!!error} aria-describedby={error ? `${loginForm}-error` : undefined} />
        <AccountError id={`${loginForm}-error`} error={error} />
      </form>
      <div className="astron-sh-account-token-help"><span>还没有 Token？</span><SkillWebsiteLink webUrl={source + '/dashboard/tokens'} enabled label="前往来源网页获取" /></div></> : <>
      <dl className="astron-sh-account-source"><dt>当前账号</dt><dd>{identity ? identity.displayName || identity.handle : '未登录'}</dd><dt>技能来源</dt><dd>{source}</dd></dl>
      <div className="astron-sh-account-setting-row"><span>访问令牌</span><Button onClick={() => show('login')}>更换个人 Token</Button></div>
      <div className="astron-sh-account-setting-row"><span>界面外观</span><Button size="icon" aria-label="切换深浅色外观" onClick={toggleTheme}><svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7"><path d="M20.6 14.2A9 9 0 0 1 9.8 3.4 9 9 0 1 0 20.6 14.2Z" /></svg></Button></div>
      <SkillWebsiteLink webUrl={source + '/settings/profile'} enabled label="到来源网页管理" /></>}</AccountDialog>}
    {identity && modal && typeof modal === 'object' && <UploadSkill remote={remote} revision={revision} spaces={spaces.data ?? []}
      spacesLoading={spaces.isFetching && !spaces.data} spacesError={spaces.error} retrySpaces={() => { void spaces.refetch(); }}
      initial={modal.initial} row={modal.row} close={() => setModal(null)} />}
  </>;
}
