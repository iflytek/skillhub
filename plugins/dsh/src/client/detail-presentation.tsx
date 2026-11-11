import type { ReactNode } from 'react';
import type { SkillDetail } from '../host/types.js';
import { SkillMetrics } from './skill-card.js';
import { SkillWebsiteLink } from './skill-website-link.js';

export function DetailMeta({ detail, websiteEnabled, children }: { detail?: SkillDetail; websiteEnabled: boolean; children?: ReactNode }) {
  return <div className="astron-sh-detail-meta-row">{detail && <SkillMetrics item={detail} />}{children}<SkillWebsiteLink label="官网" webUrl={detail?.webUrl} enabled={websiteEnabled} /></div>;
}

export function sourceHost(registry: string): string {
  try { return new URL(registry ?? '').host; } catch { return '技能来源'; }
}
