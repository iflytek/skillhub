import { useEffect, useId, useRef, useState, type DragEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { AccountNamespace, OwnedSkill, UploadReceipt, Visibility } from '../host/account-types.js';
import { AccountDialog, AccountError, AccountSelect, valueOf, visibilityLabels, type AccountRemote } from './account-common.js';
import { encodePackage, MAX_TRANSFER_BYTES } from './package-transfer.js';
import { Button } from './ui.js';
import { invalidateRegistrySkill } from './read-cache.js';
import { Icon } from './skill-card.js';

export function UploadSkill({ remote, revision, spaces, initial, row, close, spacesLoading, spacesError, retrySpaces }: {
  remote: AccountRemote; revision: string; spaces: AccountNamespace[]; initial?: string; row?: OwnedSkill; close(): void;
  spacesLoading?: boolean; spacesError?: Error | null; retrySpaces?: () => void;
}) {
  const available = spaces.filter(ns => ns.status === 'ACTIVE' && ns.type !== 'CATALOG');
  const [chosenSpace, setSpace] = useState(initial);
  const space = chosenSpace ?? available[0]?.slug ?? '';
  const [file, setFile] = useState<File>();
  const [fileError, setFileError] = useState<Error | null>(null);
  const fileInput = useRef<HTMLInputElement>(null);
  const picker = useRef<HTMLButtonElement>(null);
  const fileId = useId();
  const dragDepth = useRef(0);
  const [dragging, setDragging] = useState(false);
  const [selection, setSelection] = useState(0);
  const [chosen, setChosen] = useState<Visibility>();
  const [receipt, setReceipt] = useState<UploadReceipt>();
  const [confirm, setConfirm] = useState(false);
  const confirmationVersion = useRef(0);
  const submitting = useRef(false);
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
  const upload = useMutation({ mutationKey: ['account-write', revision], retry: false, networkMode: 'always', mutationFn: async () => {
    if (!target.data || target.isError || !available.some(ns => ns.slug === space)) throw new Error('请重新选择上传目标。');
    return valueOf(await remote.publishUpload(revision, space, target.data.archive, visibility, target.data.result, new AbortController().signal));
  }, onSuccess: result => { setReceipt(result); invalidateRegistrySkill(client, revision, result.namespace, result.slug); },
  onError: error => {
    setConfirm(false);
    if (error.name === 'UploadTargetChanged') {
      setChosen(undefined);
      void target.refetch();
    } else setClosedWrite(true);
  }, onSettled: () => { submitting.current = false; } });
  const status = useQuery({ queryKey: ['account-version', revision, receipt?.namespace, receipt?.slug, receipt?.version],
    enabled: !!receipt, retry: false, queryFn: async ({ signal }) => valueOf(await remote.uploadStatus(revision, receipt!.namespace, receipt!.slug, receipt!.version, signal)) });
  useEffect(() => {
    if (receipt && status.data?.status === 'PUBLISHED') invalidateRegistrySkill(client, revision, receipt.namespace, receipt.slug);
  }, [client, receipt, revision, status.data?.status]);
  function selectFiles(files?: File[]) {
    if (upload.isPending) return;
    const selected = files?.[0];
    const error = files && files.length !== 1 ? '一次只能选择一个 ZIP 技能包。'
      : selected && !/\.zip$/i.test(selected.name) ? '请选择 ZIP 格式的技能包。'
      : selected && (!selected.size || selected.size > MAX_TRANSFER_BYTES) ? '请选择非空且不超过 8 MiB 的 ZIP 文件。' : undefined;
    setFile(error ? undefined : selected); setFileError(error ? new Error(error) : null);
    setSelection(v => v + 1); setChosen(undefined); resetConfirmation();
    dragDepth.current = 0; setDragging(false);
  }
  function preventFileNavigation(event: DragEvent) {
    if (event.dataTransfer.types.includes('Files')) event.preventDefault();
  }
  function resetConfirmation() {
    confirmationVersion.current++; setConfirm(false);
  }
  const fileDescription = `${fileId}-help${fileError ? ` ${fileId}-error` : ''}`;
  return <div onDragOver={preventFileNavigation} onDrop={preventFileNavigation}><AccountDialog title={receipt ? '已上传' : '上传技能'} busy={upload.isPending} close={close}
    description={receipt ? `@${receipt.namespace}/${receipt.slug} · ${receipt.version} · ${visibilityLabels[receipt.visibility]}` : '选择目标命名空间、技能包和可见性。'}
    actions={!spacesLoading && !spacesError && !receipt && <Button variant="default" disabled={!target.data || target.isFetching || target.isError || upload.isPending || closedWrite}
      onClick={async () => {
        if (confirm) {
          if (!submitting.current) { submitting.current = true; upload.mutate(); }
          return;
        }
        const version = confirmationVersion.current;
        const current = await target.refetch();
        if (!current.isSuccess || version !== confirmationVersion.current) return;
        if (JSON.stringify(current.data.result) !== JSON.stringify(target.data?.result)) setChosen(undefined);
        setConfirm(true);
      }}>{upload.isPending ? '正在上传…' : confirm ? '确认上传' : '核对上传信息'}</Button>}>
    {spacesLoading ? <p role="status">正在读取目标命名空间…</p> : spacesError ? <AccountError error={spacesError} retry={retrySpaces} /> : receipt ? <section className="astron-sh-account-summary"><p role="status">{status.isError ? '已上传，状态暂不可确认。' : status.data?.status ?? '正在读取版本状态…'}</p>
      <Button onClick={() => { void status.refetch(); }}>刷新版本状态</Button></section> : <>
      <label>目标命名空间<AccountSelect label="目标命名空间" disabled={!!row || upload.isPending} value={space} options={available.map(ns => ({ value: ns.slug, label: ns.displayName + ' · @' + ns.slug }))}
        onValueChange={v => { setSpace(v); setChosen(undefined); resetConfirmation(); }} /></label>
      {!available.length && <p>当前账号没有可上传的命名空间。</p>}
      <div className="astron-sh-upload-field"><label htmlFor={fileId}>技能包</label>
        <input ref={fileInput} id={fileId} type="file" hidden accept=".zip,application/zip" disabled={upload.isPending} aria-describedby={fileDescription} aria-invalid={!!fileError}
          onChange={event => { if (event.currentTarget.files?.length) selectFiles(Array.from(event.currentTarget.files)); event.currentTarget.value = ''; }} />
        <Button ref={picker} className="astron-sh-upload-dropzone" type="button" disabled={upload.isPending} aria-label={file ? '更换技能包文件' : '选择技能包文件'} aria-describedby={fileDescription} aria-invalid={!!fileError} data-dragging={dragging && !upload.isPending}
          onClick={() => fileInput.current?.click()}
          onDragEnter={event => { if (!upload.isPending && event.dataTransfer.types.includes('Files')) { event.preventDefault(); dragDepth.current++; setDragging(true); } }}
          onDragLeave={() => { dragDepth.current = Math.max(0, dragDepth.current - 1); if (!dragDepth.current) setDragging(false); }}
          onDragOver={event => { event.preventDefault(); event.dataTransfer.dropEffect = !upload.isPending && event.dataTransfer.types.includes('Files') ? 'copy' : 'none'; }}
          onDrop={event => { event.preventDefault(); dragDepth.current = 0; setDragging(false); if (event.dataTransfer.types.includes('Files')) selectFiles(Array.from(event.dataTransfer.files)); }}>
          <Icon name="upload" /><strong>{dragging && !upload.isPending ? '松开以选择文件' : '将 ZIP 技能包拖到这里'}</strong><span className="astron-sh-upload-picker-label">{file ? '更换文件' : '选择文件'}</span>
        </Button>
        <span id={`${fileId}-help`} className="astron-sh-account-hint">支持拖拽或点击选择单个 ZIP 文件，不超过 8 MiB。</span>
        {file && <div className="astron-sh-upload-file"><Icon name="file" /><div role="status"><strong>{file.name}</strong><span>{file.size < 1024 ? `${file.size} B` : file.size < 1024 * 1024 ? `${(file.size / 1024).toFixed(1)} KiB` : `${(file.size / 1024 / 1024).toFixed(2)} MiB`}</span></div><Button size="sm" type="button" disabled={upload.isPending} onClick={() => { selectFiles(); picker.current?.focus(); }}>移除文件</Button></div>}
        <AccountError id={`${fileId}-error`} error={fileError} />
      </div>
      <label>可见性<AccountSelect label="可见性" value={visibility} disabled={!target.data || upload.isPending}
        options={Object.entries(visibilityLabels).map(([value, label]) => ({ value, label }))}
        onValueChange={v => { setChosen(v as Visibility); resetConfirmation(); }} /></label>
      <AccountError error={target.error} retry={() => { void target.refetch(); }} />
      {target.isFetching && <p role="status">正在核对目标技能…</p>}
      {confirm && target.data && <section className="astron-sh-account-summary" aria-label="确认上传目标"><strong>@{space}/{target.data.result.slug}</strong>
        <p>{target.data.result.existing && target.data.result.existing.visibility !== visibility
          ? `整个技能的可见性将从「${visibilityLabels[target.data.result.existing.visibility]}」改为「${visibilityLabels[visibility]}」。` : visibilityLabels[visibility]}</p>
        <p>同号未发布版本可能被替换。</p></section>}
      <AccountError error={upload.error} />
      {closedWrite && <p>请先到来源网页或“我的上传”核对结果。</p>}
    </>}
  </AccountDialog></div>;
}
