import type { InstalledSkillList } from '../host/types.js';
import type { InstalledServices } from './installed-skills.js';
import { SkillPrimaryActions } from './skill-primary-actions.js';
import { Button } from './ui.js';

export function MarketCardActions({ records, services, sourceRevision, verified, onInstall, onManage, label }: {
  records: InstalledSkillList['items']; services?: InstalledServices; sourceRevision: string; verified: boolean;
  onInstall(): void; onManage?(): void; label?: string;
}) {
  if (!records.length) return <Button size="sm" variant="default" disabled={!verified || !services} onClick={onInstall}>安装</Button>;
  return <SkillPrimaryActions items={records} revision={sourceRevision} verified={verified} select={services?.management?.selectSkillUse} useSkill={services?.skillUse} label={label} summaryVisible={!label} onManage={onManage} />;
}
