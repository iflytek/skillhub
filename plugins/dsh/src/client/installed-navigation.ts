import type { InstalledSkillList } from '../host/types.js';

export interface InstalledNavigation {
  sessionId: string;
  sourceRevision: string;
  scope: 'all' | 'user' | 'project';
  keyword: string;
  selected: InstalledSkillList['items'][number] | null;
  completed: { itemKey: string; version: string } | null;
  removalCompleted: string | null;
  intent: 'detail' | 'upgrade' | 'remove';
  listScroll: number;
  triggerKey: string | null;
}

export function installedNavigationFor(owner: { installedNavigation?: InstalledNavigation }, sessionId: string, sourceRevision: string): InstalledNavigation {
  const previous = owner.installedNavigation;
  if (previous?.sessionId === sessionId && previous.sourceRevision === sourceRevision) return previous;
  return owner.installedNavigation = {
    sessionId, sourceRevision, scope: 'all', keyword: '', selected: null,
    completed: null, removalCompleted: null, intent: 'detail', listScroll: 0, triggerKey: null,
  };
}
