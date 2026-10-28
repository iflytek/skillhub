import { useCallback, useRef, useState } from 'react';
import type { ConversationManagementResult, InstalledSkillList } from '../host/types.js';
import { ManagementConfirmation, refreshManagementLocation, verifyManagementReference, type ManagementViewServices } from './management-tool-view.js';
import { Button, Dialog, DialogContent, DialogTitle, DialogDescription } from './ui.js';

type Item = InstalledSkillList['items'][number];
/** Both page and conversation actions use the same native confirmation and exact Host plan. */
export function ToggleControl({ services, revision, item, onRefreshed, eligible = true, presentation = 'menu', title }: {
  services?: ManagementViewServices; revision: string; item: Item; eligible?: boolean; presentation?: 'menu' | 'button'; title?: string; onRefreshed?(item: Item): void;
}) {
  const [open, setOpen] = useState(false);
  const [more, setMore] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  const [receipt, setReceipt] = useState<ConversationManagementResult>();
  const [container, setContainer] = useState<HTMLDivElement | null>(null);
  const request = useRef<string>();
  const lock = useRef(false);
  const trigger = useRef<HTMLButtonElement>(null);
  const refreshed = useRef(false);
  const refresh = useCallback(() => {
    if (!services || !receipt || refreshed.current) return;
    refreshed.current = true;
    void refreshManagementLocation(services, revision, receipt.item).then(list => {
      onRefreshed?.(list.items.find(candidate => candidate.locationKey === receipt.item.locationKey)!);
      setReceipt(undefined); setOpen(false); request.current = undefined; refreshed.current = false;
    }).catch(() => { refreshed.current = false; setError('状态已保存，重新读取失败，请刷新安装记录。'); });
  }, [services, revision, receipt, onRefreshed]);
  if (!services || item.state !== 'present') return null;
  const disabled = item.installationState === 'disabled';
  async function begin() {
    if (!services || !eligible || lock.current || receipt || error) return;
    lock.current = true; setPending(true); setMore(false); setOpen(true);
    try {
      await verifyManagementReference(services, revision, item.itemKey, new AbortController().signal);
      request.current ??= crypto.randomUUID();
      const response = await services.begin(revision, item.itemKey, disabled ? 'enable' : 'disable', null, request.current, new AbortController().signal);
      services.assertCurrent();
      if (!response.ok) throw new Error('安装记录已变化或请求未确认，请重新查询；不要重复操作。');
      if (response.value.status === 'cancelled') { request.current = undefined; setOpen(false); }
      else setReceipt(response.value);
    } catch { setError('安装记录已变化或请求未确认，请重新查询；不要重复操作。'); }
    finally { lock.current = false; setPending(false); }
  }
  return <div ref={setContainer} className="astron-sh-toggle-control" data-installation-state={item.installationState}
    onBlur={event => { if (!event.currentTarget.contains(event.relatedTarget as Node | null)) setMore(false); }}
    onKeyDown={event => { if (event.key === 'Escape' && more) { event.preventDefault(); setMore(false); trigger.current?.focus(); } }}>
    {receipt || error ? <Button size="sm" variant="ghost" onClick={() => setOpen(true)}>查看启停结果</Button>
      : disabled ? <Button size="sm" variant={presentation === 'button' ? 'default' : 'outline'} disabled={pending || !eligible} onClick={() => void begin()}>启用</Button>
        : presentation === 'button' ? <Button size="sm" variant="ghost" disabled={pending || !eligible} onClick={() => void begin()}>停用</Button>
          : <><Button ref={trigger} size="sm" variant="ghost" aria-label="更多操作" aria-expanded={more} disabled={pending || !eligible} onClick={() => setMore(value => !value)}>···</Button>
          {more && <Button size="sm" variant="ghost" disabled={pending || !eligible} onClick={() => void begin()}>停用</Button>}</>}
    {container && <Dialog open={open} onOpenChange={value => { if (!pending) setOpen(value); }}><DialogContent container={container} className="astron-sh-dialog">
      <DialogTitle>{receipt?.action === 'enable' || (!receipt && disabled) ? '启用技能' : '停用技能'}</DialogTitle>
      <DialogDescription className="astron-sh-sr-only">核对安装位置后确认操作</DialogDescription>
      {pending && <p role="status">正在核验安装状态…</p>}
      {error && <p role="alert">{error}</p>}
      {receipt && <ManagementConfirmation presentation="dialog" title={title} initial={receipt} services={services} onCompleted={refresh} onCancelled={() => { setReceipt(undefined); request.current = undefined; setOpen(false); }} />}
    </DialogContent></Dialog>}
  </div>;
}
