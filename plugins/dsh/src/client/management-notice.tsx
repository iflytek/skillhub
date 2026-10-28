import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { Button } from './ui.js';

interface Notice { title: string; content: ReactNode; success?: boolean }
const NoticeContext = createContext<((notice: Notice | undefined) => void) | undefined>(undefined);
export const useManagementNotice = () => useContext(NoticeContext);
/** One scoped slot replaces repeated checks; leaving the skill disposes its timer and notice. */
export function ManagementNoticeBoundary({ children, container }: { children: ReactNode; container: HTMLElement | null }) {
  const [notice, setNotice] = useState<Notice>();
  useEffect(() => {
    if (!notice?.success) return;
    const timer = setTimeout(() => setNotice(undefined), 5000);
    return () => clearTimeout(timer);
  }, [notice]);
  return <NoticeContext.Provider value={setNotice}>{children}{notice && createPortal(
    <aside className="skillhub-controls astron-sh-management-notice" aria-label="安装检查通知">
      <div role={notice.success ? 'status' : 'alert'}><strong>{notice.title}</strong>{notice.content}</div>
      <Button size="icon" variant="ghost" aria-label="关闭安装检查通知" onClick={() => setNotice(undefined)}>×</Button>
    </aside>, container?.closest('dialog') ?? document.body)}</NoticeContext.Provider>;
}
