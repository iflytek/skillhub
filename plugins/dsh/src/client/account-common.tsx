import type { RemoteResult, TypertRemoteNamespaceMap } from '@deepseek-ai/dsh-typert-protocol';
import type { Visibility } from '../host/account-types.js';
import { Button, Dialog, DialogContent, DialogDescription, DialogTitle } from './ui.js';
import { catalogError } from './catalog-error.js';
import { useState, type ReactNode } from 'react';

export type AccountRemote = TypertRemoteNamespaceMap['skillhub'];
export function valueOf<T>(result: RemoteResult<T>): T {
  if (!result.ok) throw catalogError(result.error);
  return result.value;
}
export const visibilityLabels: Record<Visibility, string> = { PRIVATE: '私有', NAMESPACE_ONLY: '仅命名空间可见', PUBLIC: '公开' };
export const roleLabels = { ADMIN: '管理员', MEMBER: '成员', OWNER: '所有者' };
export const namespaceStatusLabels: Record<string, string> = { ACTIVE: '正常', FROZEN: '已冻结', ARCHIVED: '已归档' };
export const namespaceTypeLabels: Record<string, string> = { TEAM: '团队空间', GLOBAL: '全局空间', CATALOG: '目录空间' };
// Native selects avoid nested modal layers inside account dialogs.
export function AccountSelect({ label, value, disabled, options, onValueChange }: {
  label: string; value: string; disabled?: boolean; options: { value: string; label: string }[]; onValueChange(value: string): void;
}) {
  return <select className="astron-sh-input" aria-label={label} value={value} disabled={disabled} onChange={event => onValueChange(event.target.value)}>
    {options.map(option => <option key={option.value} value={option.value}>{option.label}</option>)}
  </select>;
}
export function AccountDialog({ title, description, close, busy = false, children, actions, closeLabel, returnFocus }: {
  title: string; description: string; close(): void; busy?: boolean; children: ReactNode; actions?: ReactNode; closeLabel?: string; returnFocus?: HTMLElement | null | (() => HTMLElement | null);
}) {
  const [anchor, setAnchor] = useState<HTMLSpanElement | null>(null);
  const [opener] = useState(() => typeof document === 'undefined' ? null : document.activeElement);
  return <><span ref={setAnchor} /><Dialog open onOpenChange={open => { if (!open && !busy) close(); }}>
    <DialogContent container={anchor} className="astron-sh-account-form" aria-busy={busy} onOpenAutoFocus={event => {
      if (!(event.target instanceof HTMLElement)) return;
      const field = event.target.querySelector('.astron-sh-account-dialog-body')?.querySelector<HTMLElement>('input:not([disabled]):not([hidden]):not([type="hidden"]),select:not([disabled]),button:not([disabled]),[role="combobox"]:not([disabled])')
        ?? event.target.querySelector<HTMLElement>('[data-account-cancel]');
      if (field) { event.preventDefault(); field.focus(); }
    }} onCloseAutoFocus={event => {
      const target = (typeof returnFocus === 'function' ? returnFocus() : returnFocus) ?? opener;
      if (target instanceof HTMLElement && target.isConnected) { event.preventDefault(); target.focus(); }
    }}>
      <header className="astron-sh-account-dialog-header"><div><DialogTitle>{title}</DialogTitle><DialogDescription>{description}</DialogDescription></div>
        <Button size="icon" variant="ghost" aria-label={`关闭${title}`} disabled={busy} onClick={close}><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" aria-hidden="true"><path d="m6 6 12 12M6 18 18 6" /></svg></Button>
      </header>
      <div className="astron-sh-account-dialog-body">{children}</div>
      <footer className="astron-sh-account-dialog-footer"><Button data-account-cancel disabled={busy} onClick={close}>{closeLabel ?? (actions ? '取消' : '关闭')}</Button>{actions}</footer>
    </DialogContent>
  </Dialog></>;
}
export function AccountError({ error, retry, id }: { error: Error | null; retry?: () => void; id?: string }) {
  return error ? <p role="alert" id={id} className="astron-sh-account-error">{error.message} {retry && <Button size="sm" onClick={retry}>重新读取</Button>}</p> : null;
}
