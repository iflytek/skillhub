import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { AccountNamespace, OwnedSkill, UploadReceipt, Visibility } from '../host/account-types.js';
import { AccountDialog, AccountError, valueOf, visibilityLabels, type AccountRemote } from './account-common.js';
import { encodePackage, MAX_TRANSFER_BYTES } from './package-transfer.js';
import { Button, SelectField } from './ui.js';

export function UploadSkill({ remote, revision, spaces, initial, row, close }: {
  remote: AccountRemote; revision: string; spaces: AccountNamespace[]; initial?: string; row?: OwnedSkill; close(): void;
}) {
  const available = spaces.filter(ns => ns.status === 'ACTIVE' && ns.type !== 'CATALOG');
  const [space, setSpace] = useState(initial ?? available[0]?.slug ?? '');
  const [file, setFile] = useState<File>();
  const [selection, setSelection] = useState(0);
  const [chosen, setChosen] = useState<Visibility>();
  const [receipt, setReceipt] = useState<UploadReceipt>();
  const [confirm, setConfirm] = useState(false);
  const [closedWrite, setClosedWrite] = useState(false);
  const client = useQueryClient();
  const target = useQuery({
    queryKey: ['account-upload', revision, space, selection],
    enabled: !!file && available.some(ns => ns.slug === space) && !receipt,
    retry: false, gcTime: 0, refetchOnWindowFocus: false, refetchOnMount: 'always',
    queryFn: async ({ signal }) => {
      if (!file || !file.size || file.size > MAX_TRANSFER_BYTES) throw new Error('请选择不超过 8 MiB 的 ZIP 文件。');
      const archive = encodePackage(new Uint8Array(await file.arrayBuffer()));
      const result = valueOf(await remote.inspectUpload(revision, space, archive, signal));
      if (row && row.id !== result.existing?.id) throw new Error('技能包与所选技能不匹配。');
      return { archive, result };
    },
  });
  const visibility = chosen ?? target.data?.result.existing?.visibility ?? 'PRIVATE';
  const upload = useMutation({ mutationKey: ['account-write', revision], retry: false, mutationFn: async () => {
    if (!target.data || target.isError || !available.some(ns => ns.slug === space)) throw new Error('请重新选择上传目标。');
    return valueOf(await remote.publishUpload(revision, space, target.data.archive, visibility, target.data.result, new AbortController().signal));
  }, onSuccess: result => { setReceipt(result); void client.invalidateQueries({ queryKey: ['account-owned', revision, space] }); },
  onError: () => { setClosedWrite(true); setConfirm(false); } });
  const status = useQuery({ queryKey: ['account-version', revision, receipt?.namespace, receipt?.slug, receipt?.version],
    enabled: !!receipt, retry: false, queryFn: async ({ signal }) => valueOf(await remote.uploadStatus(revision, receipt!.namespace, receipt!.slug, receipt!.version, signal)) });
  return <AccountDialog title={receipt ? '已上传' : '上传技能'} busy={upload.isPending} close={close}
    description={receipt ? `@${receipt.namespace}/${receipt.slug} · ${receipt.version} · ${visibilityLabels[receipt.visibility]}` : '选择技能包和目标命名空间。'}>
    {receipt ? <section><p role="status">{status.isError ? '已上传，状态暂不可确认。' : status.data?.status ?? '正在读取版本状态…'}</p>
      <Button onClick={() => { void status.refetch(); }}>刷新版本状态</Button></section> : <>
      <label>目标命名空间<SelectField label="目标命名空间" disabled={!!row || upload.isPending} value={space} options={available.map(ns => ({ value: ns.slug, label: ns.displayName + ' · @' + ns.slug }))}
        onValueChange={v => { setSpace(v); setChosen(undefined); setConfirm(false); }} /></label>
      {!available.length && <p>当前账号没有可上传的命名空间。</p>}
      <label>技能包<input type="file" accept=".zip,application/zip" disabled={upload.isPending}
        onChange={e => { setFile(e.target.files?.[0]); setSelection(v => v + 1); setChosen(undefined); setConfirm(false); }} /></label>
      <label>可见性<SelectField label="可见性" value={visibility} disabled={!target.data || upload.isPending}
        options={Object.entries(visibilityLabels).map(([value, label]) => ({ value, label }))}
        onValueChange={v => { setChosen(v as Visibility); setConfirm(false); }} /></label>
      <AccountError error={target.error} retry={() => { void target.refetch(); }} />
      {target.isFetching && <p role="status">正在核对目标技能…</p>}
      {confirm && target.data && <section aria-label="确认上传目标"><strong>@{space}/{target.data.result.slug}</strong>
        <p>{target.data.result.existing && target.data.result.existing.visibility !== visibility
          ? `整个技能的可见性将从「${visibilityLabels[target.data.result.existing.visibility]}」改为「${visibilityLabels[visibility]}」。` : visibilityLabels[visibility]}</p>
        <p>同号未发布版本可能被替换。</p></section>}
      <AccountError error={upload.error} />
      {closedWrite && <p>请先到来源网页或“我的上传”核对结果。</p>}
      <Button variant="default" disabled={!target.data || target.isFetching || target.isError || upload.isPending || closedWrite}
        onClick={async () => {
          if (confirm) { upload.mutate(); return; }
          const current = await target.refetch();
          if (!current.isSuccess) return;
          if (JSON.stringify(current.data.result) !== JSON.stringify(target.data?.result)) setChosen(undefined);
          setConfirm(true);
        }}>{upload.isPending ? '正在上传…' : confirm ? '确认上传' : '核对上传信息'}</Button>
    </>}
  </AccountDialog>;
}
