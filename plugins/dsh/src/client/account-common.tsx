import type { RemoteResult, TypertRemoteNamespaceMap } from '@deepseek-ai/dsh-typert-protocol';
import type { Visibility } from '../host/account-types.js';
import { Button, Dialog, DialogContent, DialogDescription, DialogTitle } from './ui.js';
import { catalogErrorMessage } from './catalog-error.js';
import { useState, type ReactNode } from 'react';

export type AccountRemote = TypertRemoteNamespaceMap['skillhub'];
export function valueOf<T>(result: RemoteResult<T>): T {
  if (!result.ok) {
    const error = new Error(catalogErrorMessage(result.error));
    const details = result.error.details;
    if (details && typeof details === 'object' && 'reason' in details && details.reason === 'AUTH_REQUIRED') error.name = 'AccountExpired';
    throw error;
  }
  return result.value;
}
export const visibilityLabels: Record<Visibility, string> = { PRIVATE: '私有', NAMESPACE_ONLY: '仅命名空间可见', PUBLIC: '公开' };
export const roleLabels = { ADMIN: '管理员', MEMBER: '成员', OWNER: '所有者' };
export function AccountDialog({ title, description, close, busy = false, children }: {
  title: string; description: string; close(): void; busy?: boolean; children: ReactNode;
}) {
  const [anchor, setAnchor] = useState<HTMLSpanElement | null>(null);
  return <><span ref={setAnchor} /><Dialog open onOpenChange={open => { if (!open && !busy) close(); }}>
    <DialogContent container={anchor} className="astron-sh-account-form">
      <DialogTitle>{title}</DialogTitle><DialogDescription>{description}</DialogDescription>
      {children}
      <Button size="sm" disabled={busy} onClick={close}>关闭</Button>
    </DialogContent>
  </Dialog></>;
}
export function AccountError({ error, retry }: { error: Error | null; retry?: () => void }) {
  return error ? <p role="alert">{error.message} {retry && <Button size="sm" onClick={retry}>重新读取</Button>}</p> : null;
}
