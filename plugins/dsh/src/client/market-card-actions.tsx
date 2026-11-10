import type { InstalledSkillList } from '../host/types.js';
import type { InstalledServices } from './installed-skills.js';
import { skillInstallationSummary, SkillPrimaryActions } from './skill-primary-actions.js';
import { Icon } from './skill-card.js';
import { Button } from './ui.js';

export function MarketCardActions({ records, services, sourceRevision, verified, onInstall, onManage, label, presentation = 'action' }: {
  records: InstalledSkillList['items']; services?: InstalledServices; sourceRevision: string; verified: boolean;
  onInstall(): void; onManage?(): void; label?: string; presentation?: 'action' | 'status';
}) {
  if (!records.length) return <Button size="sm" variant="outline" disabled={!verified || !services} onClick={onInstall}><Icon name="download" />安装</Button>;
  if (presentation === 'status') {
    const summary = verified ? skillInstallationSummary(records) : '安装待核验';
    return <span className="astron-sh-market-state" data-state={summary === '已安装' ? 'installed' : summary === '已停用' ? 'disabled' : 'unverified'}><Icon name={summary === '已安装' ? 'check' : summary === '已停用' ? 'eye-off' : 'warning'} />{summary}</span>;
  }
  const usable = verified && services?.management?.selectSkillUse && records.some(item => item.state === 'present' && item.installationState !== 'disabled');
  if (!usable && onManage) return <Button size="sm" variant="outline" onClick={onManage}>查看详情</Button>;
  return <SkillPrimaryActions items={records} revision={sourceRevision} verified={verified} select={services?.management?.selectSkillUse} useSkill={services?.skillUse} label={label ?? '使用'} summaryVisible={false} />;
}
