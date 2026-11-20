import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { AccountDialog, AccountError, roleLabels, valueOf, visibilityLabels, type AccountRemote } from './account-common.js';
import type { AccountMember, AccountNamespace, EditableRole, MemberCandidate, MemberMutation, OwnedSkill } from '../host/account-types.js';
import { Button, Input, SelectField } from './ui.js';

type Action = { type: 'add' } | { type: 'role' | 'remove'; member: AccountMember } | { type: 'delete'; skill: OwnedSkill };
interface Props { source: string; remote: AccountRemote; revision: string; space: AccountNamespace; handle: string; upload(skill?: OwnedSkill): void; open(slug: string, version: string, skillId?: number, versionId?: number): void }

export function SpacePage({ source, remote, revision, space, handle, upload, open }: Props) {
  const team = space.type === 'TEAM';
  const canManage = team && space.status === 'ACTIVE' && ['ADMIN', 'OWNER'].includes(space.currentUserRole);
  const [tab, setTab] = useState<'owned' | 'team' | 'members'>('owned');
  const [page, setPage] = useState(0);
  const [cursors, setCursors] = useState(['']);
  const [action, setAction] = useState<Action>();
  const [notice, setNotice] = useState('');
  const [search, setSearch] = useState('');
  const [keyword, setKeyword] = useState('');
  const [selected, setSelected] = useState<MemberCandidate>();
  const [role, setRole] = useState<EditableRole>('MEMBER');
  const client = useQueryClient();
  const ns = space.slug;
  const owned = useQuery({ queryKey: ['account-owned', revision, ns, page], enabled: tab === 'owned', retry: false,
    queryFn: async ({ signal }) => valueOf(await remote.accountOwned(revision, ns, page, signal)) });
  const roster = useQuery({ queryKey: ['account-members', revision, ns, page], enabled: tab === 'members' && team, retry: false,
    queryFn: async ({ signal }) => valueOf(await remote.accountMembers(revision, ns, page, signal)) });
  const skills = useQuery({ queryKey: ['account-team', revision, ns, cursors[page]], enabled: tab === 'team' && team, retry: false,
    queryFn: async ({ signal }) => valueOf(await remote.accountTeam(revision, ns, cursors[page] ?? '', signal)) });
  const candidates = useQuery({ queryKey: ['account-candidates', revision, ns, keyword], enabled: canManage && action?.type === 'add' && keyword.length >= 2,
    retry: false, queryFn: async ({ signal }) => valueOf(await remote.accountCandidates(revision, ns, keyword, signal)) });
  const change = useMutation({ mutationKey: ['account-write', revision], retry: false, mutationFn: async () => {
    if (!action) throw new Error('请先选择操作。');
    const signal = new AbortController().signal;
    if (action.type === 'delete') return valueOf(await remote.deleteAccountSkill(revision, ns, action.skill.id, action.skill.slug, signal));
    if (!canManage) throw new Error('当前角色不能管理成员。');
    let command: MemberMutation;
    if (action.type === 'add' && selected) command = { action: 'add', userId: selected.userId, role };
    else if (action.type === 'role') command = { action: 'role', userId: action.member.userId, role };
    else if (action.type === 'remove') command = { action: 'remove', userId: action.member.userId };
    else throw new Error('请先选择成员。');
    return valueOf(await remote.changeAccountMember(revision, ns, command, signal));
  }, onSuccess: () => {
    setNotice(action?.type === 'add' ? '已添加成员 ' + selected?.userId : '已保存变更');
    setAction(undefined);
    void client.invalidateQueries({ predicate: q => String(q.queryKey[0]).startsWith('account-') && q.queryKey.includes(revision) });
  } });
  function show(a: Action) {
    change.reset(); setSearch(''); setKeyword(''); setSelected(undefined); setNotice('');
    setRole(a.type === 'role' && a.member.role === 'ADMIN' ? 'ADMIN' : 'MEMBER'); setAction(a);
  }
  const result = tab === 'owned' ? owned : tab === 'team' ? skills : roster;
  const more = tab === 'team' ? !!skills.data?.nextCursor : (page + 1) * 20 < ((tab === 'owned' ? owned.data : roster.data)?.total ?? 0);
  return <section className="astron-sh-account-page">
    <header><div><h1 tabIndex={-1}>{space.displayName}</h1><p>@{ns} · {space.status} · {roleLabels[space.currentUserRole]}</p></div>
      {space.status === 'ACTIVE' && space.type !== 'CATALOG' && <Button variant="default" onClick={() => upload()}>上传技能</Button>}</header>
    <nav aria-label="空间内容">{([['owned', '我的上传'], ...(team ? [['team','可安装技能'],['members','成员']] : [])] as const).map(([key,label]) =>
      <Button key={key} aria-pressed={tab === key} variant={tab === key ? 'default' : 'ghost'} onClick={() => { setTab(key as typeof tab); setPage(0); setCursors(['']); }}>{label}</Button>)}</nav>
    {tab === 'members' && canManage && <Button onClick={() => show({ type: 'add' })}>添加成员</Button>}
    <p role="status">{notice || (result.isPending ? '正在读取…' : !result.isError && !result.data?.items.length ? '暂无记录' : '')}</p>
    <AccountError error={result.error} retry={() => { void result.refetch(); }} />
    {!result.isError && tab === 'owned' && owned.data?.items.map(skill => <article key={skill.id}>
      <div><strong>{skill.displayName || skill.slug}</strong><p>{skill.slug} · {visibilityLabels[skill.visibility]} · {skill.status}</p></div><div>
      {skill.publishedVersion && <Button onClick={() => open(skill.slug, skill.publishedVersion!.version, skill.id, skill.publishedVersion!.id)}>查看</Button>}
      {space.status === 'ACTIVE' && <Button onClick={() => upload(skill)}>上传新版本</Button>}
      <Button onClick={() => show({ type: 'delete', skill })}>删除</Button></div></article>)}
    {!result.isError && tab === 'team' && skills.data?.items.map(skill => <article key={skill.versionId}><strong>{skill.slug}</strong><div>{skill.version} <Button onClick={() => open(skill.slug, skill.version, undefined, skill.versionId)}>查看技能</Button></div></article>)}
    {!result.isError && tab === 'members' && roster.data?.items.map(member => <article key={member.userId}><div><strong>{member.displayName || member.userId}{member.userId === handle && '（你）'}</strong><p>{member.userId}</p></div>
      <div>{roleLabels[member.role]} {canManage && member.role !== 'OWNER' && <><Button onClick={() => show({ type: 'role', member })}>修改角色</Button><Button onClick={() => show({ type: 'remove', member })}>移除</Button></>}</div></article>)}
    {(page > 0 || more) && <nav aria-label="分页"><Button disabled={!page} onClick={() => setPage(page - 1)}>上一页</Button><span>第 {page + 1} 页</span><Button disabled={!more} onClick={() => {
      if (tab === 'team' && skills.data?.nextCursor) setCursors([...cursors.slice(0, page + 1), skills.data.nextCursor]); setPage(page + 1);
    }}>下一页</Button></nav>}
    {action && <AccountDialog close={() => setAction(undefined)} busy={change.isPending}
      title={{ add: '添加成员', role: '修改角色', remove: '移除成员', delete: '删除技能' }[action.type]}
      description={action.type === 'delete' ? `从 ${source} 删除整个 @${ns}/${action.skill.slug}（ID ${action.skill.id}）？本机安装将保留，云端数据按来源服务规则处理。` : action.type === 'add' ? `将现有用户加入 @${ns}` : `@${ns} · ${action.member.displayName || action.member.userId}（${action.member.userId}）`}>
      {action.type === 'remove' && <p>移除后，该用户将失去此团队的成员权限。已上传技能、个人令牌和本机文件会保留。</p>}
      {action.type === 'add' && <><form onSubmit={event => { event.preventDefault(); setKeyword(search.trim()); }}>
        <label>昵称、邮箱或用户 ID<Input autoFocus value={search} onChange={event => { setSearch(event.target.value); setSelected(undefined); setKeyword(''); }} /></label>
        <Button type="submit" disabled={search.trim().length < 2}>搜索</Button></form>
        {!!keyword && <><AccountError error={candidates.error} retry={() => { void candidates.refetch(); }} />
          {candidates.isFetching ? <p>正在搜索…</p> : !candidates.isError && !candidates.data?.length && <p>未找到可添加的用户。</p>}
          {candidates.data?.map(user => <Button key={user.userId} aria-pressed={selected?.userId === user.userId} onClick={() => setSelected(user)}>{user.displayName} · {user.userId} {user.email}</Button>)}</>}
      </>}
      {(action.type === 'add' || action.type === 'role') && <label>成员角色<SelectField label="成员角色" value={role} onValueChange={v => setRole(v as EditableRole)} options={[{ value: 'MEMBER', label: '成员' },{ value: 'ADMIN', label: '管理员' }]} /></label>}
      {action.type === 'role' && action.member.userId === handle && role === 'MEMBER' && <p>保存后你将失去成员管理权限。</p>}
      <AccountError error={change.error} /><Button variant={action.type === 'remove' || action.type === 'delete' ? 'destructive' : 'default'} disabled={change.isPending || change.isError || (action.type === 'add' && !selected) || (action.type === 'role' && action.member.role === role)} onClick={() => change.mutate()}>{change.isPending ? '正在提交…' : '确认'}</Button>
    </AccountDialog>}
  </section>;
}
