import { isInventoryScopeVerified } from './installed-inventory.js';
import type { InstalledSkillList, NativeOperation } from '../host/types.js';
import type { InstallResult } from './install-result.js';

/** A historical receipt only matches inventory in its original scope and exact version. */
export function receiptInventoryIssue(result: Pick<InstallResult, 'namespace' | 'slug' | 'version' | 'targetLabel'>,
  inventory: InstalledSkillList | undefined, failed: boolean): string | null {
  if (failed) return '当前安装状态无法核验，请重新核验状态。';
  if (!inventory) return null;
  const scope = result.targetLabel === '当前用户' ? 'user' : result.targetLabel === '当前项目' ? 'project' : undefined;
  if (!scope || !isInventoryScopeVerified(inventory, scope)) return '当前库存读取不完整或原安装范围无法确认。';
  const current = inventory.items.find(item => item.namespace === result.namespace && item.slug === result.slug && item.scope === scope);
  if (!current) return '原安装范围内已无此技能的安装记录。';
  if (current.state !== 'present') return '原安装范围的本地文件状态异常，请核验当前记录。';
  if (current.version !== result.version) return `原安装范围当前为 v${current.version}，已不是此次安装的版本。`;
  return null;
}

/** Keep actionable uncertainty visible without forcing technical history open. */
export function receiptPresentation(operation: Pick<NativeOperation, 'phase' | 'warningCount' | 'recovery' | 'resultDetails'> | null | undefined, failed: boolean,
  inventoryPending: boolean, inventoryIssue: string | null): { label: string; tone: 'warning' | 'pending' | 'success'; message: string } {
  if (failed || operation === null) return { label: '状态待核验', tone: 'warning', message: '原操作结果无法核验，请勿重复安装。' };
  if (!operation) return { label: '核验中', tone: 'pending', message: '正在核验安装状态…' };
  const warning = operation.warningCount > 0 || !!operation.resultDetails?.warnings.length;
  if (operation.phase === 'completed') {
    if (inventoryPending) return { label: '当前待核验', tone: warning ? 'warning' : 'pending', message: `历史安装已完成，正在核验当前状态。${warning ? '原操作仍有告警。' : ''}` };
    if (inventoryIssue) return { label: '历史安装完成', tone: 'warning', message: `${inventoryIssue}${warning ? '原操作仍有告警。' : ''}` };
    return { label: warning ? '安装完成 · 有告警' : '安装完成', tone: warning ? 'warning' : 'success',
      message: warning ? '当前范围存在此版本记录，原操作仍有告警，请核验状态。' : '已核对当前范围的版本与文件记录。' };
  }
  if (operation.phase === 'failed') return { label: '安装未完成', tone: 'warning', message: (operation.recovery === 'restored'
    ? '本次安装未完成，原文件已恢复；可核验当前安装状态。' : operation.recovery === 'not-needed'
      ? '本次安装未完成，无需恢复文件；可核验当前安装状态。' : '本次安装未完成，请核验恢复状态，勿重复安装。') + (warning ? '原操作仍有告警。' : '') };
  if (operation.phase === 'unknown' || operation.phase === 'recovery-required') return {
    label: '需要核验', tone: 'warning', message: '操作需要恢复核验，请核验原操作，勿重复安装。',
  };
  return { label: '安装中', tone: 'pending', message: '正在安装，请稍候…' };
}
