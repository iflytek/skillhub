import { useTranslation } from 'react-i18next'
import { ArrowUpRight, Bookmark, Download, User } from 'lucide-react'
import type { RecommendedSkill } from '@/api/recommendations'
import { SkillAvatar } from '@/features/skill/skill-avatar'
import { formatCompactCount } from '@/shared/lib/number-format'

/** Local editorial tint; marketplace cards keep their existing appearance. */
export function recommendationTone(seed: string) {
  let hash = 0
  for (const char of seed) hash = (Math.imul(hash, 31) + char.charCodeAt(0)) | 0
  return (hash >>> 0) % 8
}

export function RecommendationSkillCard({ skill, onClick }: { skill: RecommendedSkill; onClick: () => void }) {
  const { t } = useTranslation()
  const version = skill.publishedVersion ?? skill.headlineVersion
  return <article className="recommendation-skill-card" data-tone={recommendationTone(`${skill.namespace}/${skill.slug}`)} role="link" tabIndex={0} aria-label={skill.displayName} onClick={onClick} onKeyDown={event => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); onClick() } }}>
    <div className="recommendation-skill-identity"><SkillAvatar name={skill.displayName} url={skill.avatarUrl} seed={`${skill.namespace}/${skill.slug}`} size="h-11 w-11 text-xl" /><div><h3>{skill.displayName}</h3><span>{skill.namespace} / {skill.slug}</span></div><ArrowUpRight aria-hidden="true" className="h-4 w-4 shrink-0" /></div>
    <div className="recommendation-skill-body"><p className="recommendation-skill-summary">{skill.summary}</p>{skill.labels?.length ? <ul className="recommendation-skill-tags">{skill.labels.map(label => <li key={label.slug}>{label.displayName}</li>)}</ul> : null}
      <div className="recommendation-skill-meta">{version && <span className="font-mono">v{version.version}</span>}<span aria-label={t('skillCard.downloads', { value: formatCompactCount(skill.downloadCount) })}><Download aria-hidden="true" />{formatCompactCount(skill.downloadCount)}</span><span aria-label={t('skillCard.stars', { count: skill.starCount })}><Bookmark aria-hidden="true" />{formatCompactCount(skill.starCount)}</span></div>
      {skill.ownerDisplayName && <div className="recommendation-skill-owner"><User aria-hidden="true" />{skill.ownerDisplayName}</div>}
    </div>
  </article>
}
