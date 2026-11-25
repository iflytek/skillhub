import { useEffect, useId, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { AccountDialog, AccountError, AccountSelect, roleLabels, namespaceStatusLabels, namespaceTypeLabels, valueOf, visibilityLabels, type AccountRemote } from './account-common.js';
import type { AccountMember, AccountNamespace, EditableRole, MemberCandidate, MemberMutation, OwnedSkill } from '../host/account-types.js';
import { invalidateRegistrySkill } from './read-cache.js';
import { Badge, Button, Input, Tabs, TabsList, TabsTrigger, TabsContent } from './ui.js';

type Action = { type: 'add' } | { type: 'role' | 'remove'; member: AccountMember } | { type: 'delete'; skill: OwnedSkill };
const skillStatusLabels: Record<string, string> = { ACTIVE: '正常', HIDDEN: '已隐藏', ARCHIVED: '已归档' };
interface Props { source: string; remote: AccountRemote; revision: string; space: AccountNamespace; handle: string; upload(skill?: OwnedSkill): void; open(slug: string, version: string, skillId?: number, versionId?: number): void }

export function SpacePage({ source, remote, revision, space, handle, upload, open }: Props) {
  const team = space.type === 'TEAM';
  const canManage = team && space.status === 'ACTIVE' && ['ADMIN', 'OWNER'].includes(space.currentUserRole);
  const restrictionId = useId();
  const restriction = canManage ? undefined : space.status !== 'ACTIVE'
    ? `${namespaceStatusLabels[space.status] ?? '当前状态'}的命名空间不支持上传技能或管理成员。`
    : '当前角色为成员，可查看成员列表；成员管理由所有者或管理员操作。';
  const [section, setSection] = useState<'skills' | 'members'>('skills');
  const [skillView, setSkillView] = useState<'owned' | 'team'>(team ? 'team' : 'owned');
  const tab = section === 'members' ? 'members' : skillView;
  const heading = useRef<HTMLHeadingElement>(null);
  const membersTab = useRef<HTMLButtonElement>(null);
  const afterChangeFocus = useRef<HTMLElement | null>(null);
  useEffect(() => { heading.current?.focus(); }, [space.slug]);
  const [page, setPage] = useState(0);
  const [cursors, setCursors] = useState(['']);
  const [action, setAction] = useState<Action>();
  const [notice, setNotice] = useState('');
  const [search, setSearch] = useState('');
  const [keyword, setKeyword] = useState('');
  const [selected, setSelected] = useState<MemberCandidate>();
  const [role, setRole] = useState<EditableRole>('MEMBER');
  const client = useQueryClient();
  const submitting = useRef(false);
  const ns = space.slug;
  const owned = useQuery({ queryKey: ['account-owned', revision, ns, page], enabled: tab === 'owned', retry: false,
    queryFn: async ({ signal }) => valueOf(await remote.accountOwned(revision, ns, page, signal)) });
  const roster = useQuery({ queryKey: ['account-members', revision, ns, page], enabled: tab === 'members' && team, retry: false,
    queryFn: async ({ signal }) => valueOf(await remote.accountMembers(revision, ns, page, signal)) });
  const skills = useQuery({ queryKey: ['account-team', revision, ns, cursors[page]], enabled: tab === 'team' && team, retry: false,
    queryFn: async ({ signal }) => valueOf(await remote.accountTeam(revision, ns, cursors[page] ?? '', signal)) });
  const candidates = useQuery({ queryKey: ['account-candidates', revision, ns, keyword], enabled: canManage && action?.type === 'add' && keyword.length >= 2,
    retry: false, queryFn: async ({ signal }) => valueOf(await remote.accountCandidates(revision, ns, keyword, signal)) });
  const change = useMutation({ mutationKey: ['account-write', revision], retry: false, networkMode: 'always', mutationFn: async () => {
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
    afterChangeFocus.current = action?.type === 'delete' ? heading.current : membersTab.current;
    setNotice(action?.type === 'add' ? '已添加成员 ' + selected?.userId : '已保存变更');
    setAction(undefined);
    if (action?.type === 'delete') invalidateRegistrySkill(client, revision, ns, action.skill.slug);
    else void client.invalidateQueries({ predicate: q => String(q.queryKey[0]).startsWith('account-') && q.queryKey.includes(revision) });
  }, onSettled: () => { submitting.current = false; } });
  function submitChange() {
    if (submitting.current) return;
    submitting.current = true;
    change.mutate();
  }
  function show(a: Action) {
    afterChangeFocus.current = null;
    change.reset(); setSearch(''); setKeyword(''); setSelected(undefined); setNotice('');
    setRole(a.type === 'role' && a.member.role === 'ADMIN' ? 'ADMIN' : 'MEMBER'); setAction(a);
  }
  const result = tab === 'owned' ? owned : tab === 'team' ? skills : roster;
  const more = tab === 'team' ? !!skills.data?.nextCursor : (page + 1) * 20 < ((tab === 'owned' ? owned.data : roster.data)?.total ?? 0);
  function selectSection(value: typeof section) {
    setSection(value); setNotice(''); setPage(0); setCursors(['']);
  }
  return <section className="astron-sh-namespace">
    <header className="astron-sh-namespace-heading"><div><div className="astron-sh-namespace-title"><h1 ref={heading} tabIndex={-1}>@{ns}</h1><Badge variant={space.status === 'ACTIVE' ? 'neutral' : 'warning'}>{namespaceStatusLabels[space.status] ?? '状态未知'}</Badge></div><p>{space.displayName} · {namespaceTypeLabels[space.type] ?? '命名空间'} · 当前角色：{roleLabels[space.currentUserRole]}</p></div>
      <div className="astron-sh-account-row-actions">{team && <Button onClick={() => { selectSection('members'); membersTab.current?.focus(); }}>成员管理</Button>}
      {space.status === 'ACTIVE' && space.type !== 'CATALOG' && <Button variant="default" onClick={() => upload()}>上传技能</Button>}</div></header>
    {space.status !== 'ACTIVE' && <p id={restrictionId} className="astron-sh-namespace-notice" role="note">{restriction}</p>}
    {space.status === 'ACTIVE' && space.type === 'CATALOG' && <p className="astron-sh-namespace-notice" role="note">目录空间不支持手动上传技能。</p>}
    <Tabs value={section} onValueChange={value => selectSection(value as typeof section)}>
    <TabsList className="astron-sh-namespace-tabs" aria-label="空间内容"><TabsTrigger value="skills">技能</TabsTrigger>{team && <TabsTrigger ref={membersTab} value="members">成员</TabsTrigger>}</TabsList>
    <TabsContent key={section} value={section} className="astron-sh-namespace-panel">
    <header className="astron-sh-namespace-section-heading"><div><h2>{tab === 'members' ? '空间成员' : tab === 'owned' ? '我的上传' : '可安装技能'}</h2>
      <p>{tab === 'members' ? roster.data && !roster.isError ? `${roster.data.total} 位成员 · 按用户 ID 识别` : '查看空间成员与角色' : tab === 'owned' ? '我在此空间上传的技能，包含尚未发布的版本。' : '此空间内已发布、当前账号可安装的技能。'}</p></div>
      {tab === 'members' ? <Button variant="default" disabled={!canManage} title={restriction} aria-describedby={restriction ? restrictionId : undefined} onClick={() => show({ type: 'add' })}>添加成员</Button> : team && <div className="astron-sh-namespace-view" role="group" aria-label="技能范围">{([['team', '可安装技能'], ['owned', '我的上传']] as const).map(([value, label]) => <Button key={value} size="sm" variant={skillView === value ? 'default' : 'ghost'} aria-pressed={skillView === value} onClick={() => { setSkillView(value); setNotice(''); setPage(0); setCursors(['']); }}>{label}</Button>)}</div>}
    </header>
    {tab === 'members' && space.status === 'ACTIVE' && !canManage && <p id={restrictionId} className="astron-sh-namespace-notice" role="note">{restriction}</p>}
    {notice && <p className="astron-sh-namespace-notice" role="status">{notice}</p>}
    {(result.isPending || !result.isError && !result.data?.items.length) && <p className="astron-sh-namespace-empty" role="status">{result.isPending ? '正在读取…' : tab === 'members' ? '当前空间暂无成员记录。' : tab === 'owned' ? '你还没有在此空间上传技能。' : '当前空间暂无可安装技能。'}</p>}
    <AccountError error={result.error} retry={() => { void result.refetch(); }} />
    {!result.isError && tab === 'owned' && !!owned.data?.items.length && <ul className="astron-sh-namespace-list" aria-label="我的上传">{owned.data.items.map(skill => <li className="astron-sh-namespace-list-row" key={skill.id}>
      <div className="astron-sh-namespace-identity"><strong>{skill.displayName || skill.slug}</strong><p>{skill.slug} · {visibilityLabels[skill.visibility]} · {skillStatusLabels[skill.status] ?? '状态未知'}{!skill.publishedVersion && ' · 暂无发布版本'}</p></div><div className="astron-sh-account-row-actions">
      {skill.publishedVersion && <Button onClick={() => open(skill.slug, skill.publishedVersion!.version, skill.id, skill.publishedVersion!.id)}>查看</Button>}
      {space.status === 'ACTIVE' && space.type !== 'CATALOG' && <Button onClick={() => upload(skill)}>上传新版本</Button>}
      <Button size="sm" onClick={() => show({ type: 'delete', skill })}>删除</Button></div></li>)}</ul>}
    {!result.isError && tab === 'team' && !!skills.data?.items.length && <ul className="astron-sh-namespace-list" aria-label="可安装技能">{skills.data.items.map(skill => <li className="astron-sh-namespace-list-row" key={skill.versionId}><div className="astron-sh-namespace-identity"><strong>{skill.slug}</strong><p>@{ns} · v{skill.version}</p></div><Button size="sm" onClick={() => open(skill.slug, skill.version, undefined, skill.versionId)}>查看技能</Button></li>)}</ul>}
    {!result.isError && tab === 'members' && !!roster.data?.items.length && <div className="astron-sh-namespace-member-table">
      <div className="astron-sh-namespace-member-header" aria-hidden="true"><span>成员</span><span>角色</span><span>操作</span></div>
      <ul className="astron-sh-namespace-members" aria-label="空间成员">{roster.data.items.map(member => <li key={member.userId} className="astron-sh-namespace-member-row"><div className="astron-sh-namespace-identity"><strong>{member.displayName || member.userId}{member.userId === handle && <span className="astron-sh-namespace-self">（你）</span>}</strong><p className="astron-sh-namespace-user-id">{member.userId}</p></div>
      <Badge variant="neutral">{roleLabels[member.role]}</Badge><div className="astron-sh-account-row-actions">{member.role === 'OWNER' ? <span className="astron-sh-namespace-protected">所有者受保护</span> : <><Button size="sm" disabled={!canManage} title={restriction} aria-describedby={restriction ? restrictionId : undefined} onClick={() => show({ type: 'role', member })}>修改角色</Button><Button size="sm" variant="destructive" disabled={!canManage} title={restriction} aria-describedby={restriction ? restrictionId : undefined} onClick={() => show({ type: 'remove', member })}>移除</Button></>}</div></li>)}</ul></div>}
    {(page > 0 || more) && <nav className="astron-sh-namespace-pagination" aria-label="分页"><Button disabled={!page} onClick={() => setPage(page - 1)}>上一页</Button><span>第 {page + 1} 页</span><Button disabled={!more} onClick={() => {
      if (tab === 'team' && skills.data?.nextCursor) setCursors([...cursors.slice(0, page + 1), skills.data.nextCursor]); setPage(page + 1);
    }}>下一页</Button></nav>}
    </TabsContent></Tabs>
    {action && <AccountDialog close={() => setAction(undefined)} busy={change.isPending} returnFocus={() => afterChangeFocus.current}
      title={{ add: '添加成员', role: '修改角色', remove: '移除成员', delete: '删除技能' }[action.type]}
      actions={<Button variant={action.type === 'remove' || action.type === 'delete' ? 'destructive' : 'default'} disabled={change.isPending || change.isError || (action.type === 'add' && !selected) || (action.type === 'role' && action.member.role === role)} onClick={submitChange}>{change.isPending ? '正在提交…' : { add: '确认添加', role: '保存角色', remove: '移除成员', delete: '删除技能' }[action.type]}</Button>}
      description={action.type === 'delete' ? `从 ${source} 删除整个 @${ns}/${action.skill.slug}（ID ${action.skill.id}）？本机安装将保留，云端数据按来源服务规则处理。` : action.type === 'add' ? `搜索已注册用户，确认后直接加入 @${ns}` : `@${ns} · ${action.member.displayName || action.member.userId}（${action.member.userId}）`}>
      {action.type === 'remove' && <p>移除后，该用户将失去此团队的成员权限。已上传技能、个人令牌和本机文件会保留。</p>}
      {action.type === 'add' && <><form className="astron-sh-account-search" onSubmit={event => { event.preventDefault(); setKeyword(search.trim()); }}>
        <label>昵称、邮箱或用户 ID<Input autoFocus disabled={change.isPending} value={search} onChange={event => { setSearch(event.target.value); setSelected(undefined); setKeyword(''); }} /></label>
        <Button type="submit" disabled={change.isPending || search.trim().length < 2}>搜索</Button></form>
        {!!keyword && <><AccountError error={candidates.error} retry={() => { void candidates.refetch(); }} />
          {candidates.isFetching ? <p>正在搜索…</p> : !candidates.isError && !candidates.data?.length && <p>未找到可添加的用户。</p>}
          {!!candidates.data?.length && <div className="astron-sh-account-candidates" role="group" aria-label="搜索结果">{candidates.data.map(user => <Button className="astron-sh-account-candidate" key={user.userId} disabled={change.isPending} aria-pressed={selected?.userId === user.userId} onClick={() => setSelected(user)}><strong>{user.displayName || user.userId}</strong><span>{user.userId}{user.email && ` · ${user.email}`}</span></Button>)}</div>}</>}
      </>}
      {(action.type === 'add' || action.type === 'role') && <label>成员角色<AccountSelect label="成员角色" disabled={change.isPending} value={role} onValueChange={v => setRole(v as EditableRole)} options={[{ value: 'MEMBER', label: '成员' },{ value: 'ADMIN', label: '管理员' }]} /></label>}
      {action.type === 'role' && action.member.userId === handle && role === 'MEMBER' && <p>保存后你将失去成员管理权限。</p>}
      <AccountError error={change.error} />
    </AccountDialog>}
  </section>;
}
