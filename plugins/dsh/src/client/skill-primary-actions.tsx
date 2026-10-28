import type { InstalledSkillList, SkillUseSelection } from '../host/types.js';
import type { RemoteResult } from '@deepseek-ai/dsh-typert-protocol';
import { UseSkillButton, type SkillUseServices } from './use-skill.js';
import { Button } from './ui.js';

type Item = InstalledSkillList['items'][number];
export type SelectSkillUse = (revision: string, namespace: string, slug: string, signal: AbortSignal) => Promise<RemoteResult<SkillUseSelection>>;
const reasons: Record<Extract<SkillUseSelection, { status: 'blocked' }>['reason'], string> = {
  'no-installation': '安装记录已变化，请查看管理安装。',
  'no-usable-installation': '当前没有可用安装。',
  'selection-unverified': '当前可用安装尚未确认。',
  'multiple-usable-installations': '存在多份可用安装，请管理安装。',
  'selection-timeout': '核验超时，请重试或管理安装。',
};
export async function selectSkillItem(select: SelectSkillUse, revision: string, namespace: string, slug: string, signal: AbortSignal): Promise<string> {
  const response = await select(revision, namespace, slug, signal);
  signal.throwIfAborted();
  if (!response.ok) throw new Error('会话或安装状态已变化，请重新查询。');
  if (response.value.status === 'blocked') throw new Error(reasons[response.value.reason]);
  return response.value.itemKey;
}
/** A local summary is not a claim that DSH can call any particular installation. */
export function skillInstallationSummary(items: Item[]): string {
  if (!items.length) return '未安装';
  if (items.some(item => item.state !== 'present')) return '安装待核验';
  if (items.every(item => item.installationState === 'disabled')) return '已停用';
  return '已安装';
}
export function SkillPrimaryActions({ items, revision, verified, select, useSkill, onManage, label = '使用技能', summaryVisible = false, onUsed }: {
  items: Item[]; revision: string; verified: boolean; select?: SelectSkillUse; useSkill?: SkillUseServices;
  onManage?(): void; label?: string; summaryVisible?: boolean; onUsed?(): void;
}) {
  const identity = items[0];
  const potentiallyUsable = verified && items.some(item => item.state === 'present' && item.installationState !== 'disabled');
  return <div className="astron-sh-primary-actions">
    {potentiallyUsable && identity && select ? <UseSkillButton key={JSON.stringify([revision, identity.namespace, identity.slug])} services={useSkill} revision={revision} label={label} onUsed={onUsed} onHandoff={onUsed}
      resolve={signal => selectSkillItem(select, revision, identity.namespace, identity.slug, signal)} /> : (!summaryVisible || !verified || !select) && <span className="astron-sh-use-reason">{verified ? skillInstallationSummary(items) === '已停用' ? '技能已停用' : '当前安装待核验' : '安装状态待核验'}</span>}
    {onManage && <Button size="sm" variant={potentiallyUsable && select ? 'outline' : 'default'} onClick={onManage}>管理安装</Button>}
  </div>;
}
