import { queryHasSource } from './read-cache.js';
import { useEffect, useId, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import * as Menu from '@radix-ui/react-dropdown-menu';
import type { AccountIdentity, OwnedSkill } from '../host/account-types.js';
import { AccountDialog, AccountError, valueOf, roleLabels, namespaceStatusLabels, namespaceTypeLabels, type AccountRemote } from './account-common.js';
import { SpacePage } from './account-space.js';
import { UploadSkill } from './account-upload.js';
import { Badge, Button, Input } from './ui.js';
import { SkillWebsiteLink } from './skill-website-link.js';
import { AppearanceToggle } from './appearance-toggle.js';

export type AccountPageName = null | { space?: string };
type Modal = 'login' | 'settings' | { upload: true; initial?: string; row?: OwnedSkill };
export function AccountWorkspace({ remote, revision, source, toolbar, primary, screen, navigate, settings, open, clearFailed }: {
  remote: AccountRemote; revision: string; source: string; toolbar: Element | null; primary: Element | null;
  screen: AccountPageName; navigate(page: AccountPageName): void; settings?: () => void; open(ns: string, slug: string, version: string, skillId?: number, versionId?: number): void;
  clearFailed?: boolean;
}) {
  const [modal, setModal] = useState<Modal | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<Error | null>(null);
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
  const account = useQuery<AccountIdentity | null>({ queryKey: ['account-identity', revision], retry: false, networkMode: 'always',
    refetchOnWindowFocus: query => query.state.error?.name === 'AccountStorageError' ? false : 'always',
    refetchOnReconnect: query => query.state.error?.name !== 'AccountStorageError',
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
  const configureSource = () => { setModal(null); settings?.(); };
  async function connection(login: boolean) {
    if (busy) return;
    setBusy(true); setError(null);
    try {
      if (login) {
        const raw = token.current?.value.trim() ?? '';
        if (token.current) token.current.value = '';
        valueOf(await remote.connectAccount(revision, raw, new AbortController().signal));
      } else { valueOf(await remote.disconnectAccount(revision)); }
      setModal(null);
      if (login) {
        client.removeQueries({ predicate: q => q.queryKey[0] !== 'skillhub-context' && queryHasSource(q.queryKey, revision) });
        await client.invalidateQueries({ queryKey: ['skillhub-context'] });
      }
    } catch (cause) { setError(cause instanceof Error ? cause : new Error('连接失败')); }
    finally {
      if (!login) {
        setModal(null); navigate(null);
        client.removeQueries({ predicate: q => q.queryKey[0] !== 'skillhub-context' && queryHasSource(q.queryKey, revision) });
        void client.invalidateQueries({ queryKey: ['skillhub-context'] });
      }
      setBusy(false);
    }
  }
  return <>
    {toolbar && createPortal(<>
      {identity ? <Menu.Root modal={false}><Menu.Trigger asChild><Button ref={accountMenu} aria-label="账户菜单">{identity.displayName || identity.handle} ▾</Button></Menu.Trigger>
        <Menu.Portal container={toolbar}><Menu.Content className="astron-sh-account-menu" align="end" sideOffset={6}>
          <Menu.Item onSelect={() => navigate({})}>我的命名空间</Menu.Item><Menu.Item onSelect={() => show('settings')}>个人设置</Menu.Item>
          {settings && <Menu.Item onSelect={configureSource}>技能来源</Menu.Item>}
          <Menu.Separator /><Menu.Item disabled={busy} onSelect={() => { void connection(false); }}>断开连接</Menu.Item>
      </Menu.Content></Menu.Portal></Menu.Root> : <>
        {account.isPending ? <Button disabled>正在连接…</Button> : <>
          {account.isError && <Button onClick={() => { void account.refetch(); }}>重新连接</Button>}
          <Button onClick={() => show('login')}>登录</Button>
        </>}
      </>}
    </>, toolbar)}
    {primary && createPortal(<Button variant="default" onClick={() => show(identity ? { upload: true } : 'login')}>上传技能</Button>, primary)}
    {error && !modal && <AccountError error={error} />}
    {account.isError && <><AccountError error={account.error} />
      {!clearFailed && <Button disabled={busy} onClick={() => { void connection(false); }}>清除保存的登录</Button>}</>}
    {clearFailed && <p role="alert" className="astron-sh-account-error">本次连接已断开，但保存的登录信息未清除，重启后可能再次恢复。
      <Button disabled={busy} onClick={() => { void connection(false); }}>清除保存的登录</Button></p>}
    {screen && <section className="astron-sh-account-page"><nav className="astron-sh-account-breadcrumbs" aria-label="当前位置"><Button variant="ghost" size="sm" onClick={() => navigate(null)}>技能广场</Button><span aria-hidden="true">/</span>
      {screen.space ? <><Button variant="ghost" size="sm" onClick={() => navigate({})}>我的命名空间</Button><span aria-hidden="true">/</span><span aria-current="page">空间详情</span></> : <span aria-current="page">我的命名空间</span>}</nav>
      {(!screen.space || !selected) && <header className="astron-sh-namespace-heading"><div><h1 ref={heading} tabIndex={-1}>{screen.space ? '命名空间' : '我的命名空间'}</h1><p>{screen.space ? '查看空间内的技能与成员。' : '查看所属空间，管理空间内的技能与成员。'}</p></div>{!screen.space && spaces.data && !spaces.isError && <Badge variant="neutral">{spaces.data.length} 个空间</Badge>}</header>}
      {!identity ? <Button onClick={() => show('login')}>登录后查看</Button> : <><AccountError error={spaces.error} retry={() => { void spaces.refetch(); }} />
        {spaces.isLoading ? <p>正在读取命名空间…</p> : spaces.isError ? null : screen.space ? selected
          ? <SpacePage source={source} key={selected.slug} space={selected} revision={revision} remote={remote} handle={identity.handle}
            upload={row => show({ upload: true, initial: selected.slug, row })} open={(slug, version, skillId, versionId) => open(selected.slug, slug, version, skillId, versionId)} /> : <p>当前账号无法访问该命名空间。</p>
          : spaces.data?.length ? <ul className="astron-sh-namespace-list" aria-label="所属命名空间">{spaces.data.map(ns => <li key={ns.id} className="astron-sh-namespace-list-row"><div className="astron-sh-namespace-identity"><strong>{ns.displayName || ns.slug}</strong><p>@{ns.slug} · {namespaceTypeLabels[ns.type] ?? '命名空间'}</p></div>
            <div className="astron-sh-account-row-actions"><Badge variant={ns.status === 'ACTIVE' ? 'neutral' : 'warning'}>{namespaceStatusLabels[ns.status] ?? '状态未知'}</Badge><span className="astron-sh-namespace-role">{roleLabels[ns.currentUserRole]}</span><Button size="sm" onClick={() => navigate({ space: ns.slug })}>进入空间</Button></div></li>)}</ul> : <p className="astron-sh-namespace-empty">当前账号还没有所属命名空间。</p>}</>}
    </section>}
    {(modal === 'login' || modal === 'settings') && <AccountDialog close={() => setModal(null)} returnFocus={accountMenu.current} busy={busy}
      title={modal === 'login' ? '登录 SkillHub' : '个人设置'} description={modal === 'login' ? '使用当前来源的个人 API Token；桌面版会在本机保留登录。' : '管理当前来源的本机登录与连接。'} closeLabel={modal === 'settings' ? '完成' : undefined}
      actions={modal === 'login' && <Button type="submit" form={loginForm} variant="default" disabled={busy}>{busy ? '正在验证…' : '登录'}</Button>}>
      {modal === 'login' ? <><div className="astron-sh-account-source-row"><dl className="astron-sh-account-source"><dt>当前技能来源</dt><dd>{source}</dd></dl>
        {settings && <Button size="sm" disabled={busy} onClick={configureSource}>更改来源</Button>}</div>
      <form id={loginForm} onSubmit={e => { e.preventDefault(); void connection(true); }}><label htmlFor={`${loginForm}-token`}>个人 API Token</label>
        <Input id={`${loginForm}-token`} ref={token} type="password" required autoFocus autoComplete="off" spellCheck={false} maxLength={4096} disabled={busy}
          placeholder="粘贴个人 API Token" aria-invalid={!!error} aria-describedby={error ? `${loginForm}-error` : undefined} />
        <AccountError id={`${loginForm}-error`} error={error} />
      </form>
      <div className="astron-sh-account-token-help"><span>还没有 Token？</span><SkillWebsiteLink webUrl={source + '/dashboard/tokens'} enabled label="前往来源网页获取" /></div></> : <>
      <dl className="astron-sh-account-source"><dt>当前账号</dt><dd>{identity ? identity.displayName || identity.handle : '未登录'}</dd><dt>技能来源</dt><dd>{source}</dd></dl>
      {settings && <div className="astron-sh-account-setting-row"><span>技能来源</span><Button onClick={configureSource}>管理来源</Button></div>}
      <div className="astron-sh-account-setting-row"><span>访问令牌</span><Button onClick={() => show('login')}>更换个人 Token</Button></div>
      <div className="astron-sh-account-setting-row"><span>界面外观</span><AppearanceToggle /></div>
      <SkillWebsiteLink webUrl={source + '/settings/profile'} enabled label="到来源网页管理" /></>}</AccountDialog>}
    {identity && modal && typeof modal === 'object' && <UploadSkill remote={remote} revision={revision} spaces={spaces.data ?? []}
      spacesLoading={spaces.isFetching && !spaces.data} spacesError={spaces.error} retrySpaces={() => { void spaces.refetch(); }}
      initial={modal.initial} row={modal.row} close={() => setModal(null)} />}
  </>;
}
