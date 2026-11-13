import { useLayoutEffect, useRef, type RefObject } from 'react'
import { Link } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { ArrowLeft, ArrowRight, User } from 'lucide-react'
import { recommendationApi, recommendationSkills, RECOMMENDATION_KEY, type RecommendationDisplay, type RecommendedSkill } from '@/api/recommendations'
import { Button } from '@/shared/ui/button'
import { SkillAvatar } from '@/features/skill/skill-avatar'
import { RecommendationCover, recommendationCoverStyle } from './recommendation-cover'
import { recommendationTone } from './recommendation-skill-card'

type Topic = RecommendationDisplay['topics'][number]
function Collection({ skills, onSkillNavigate }: { skills: RecommendedSkill[]; onSkillNavigate: () => void }) {
  const { t } = useTranslation()
  return <div className="recommendation-collection-list">{skills.map((skill, index) => {
    const version = skill.publishedVersion ?? skill.headlineVersion
    return <article key={skill.id} className="recommendation-collection-card" data-tone={recommendationTone(`${skill.namespace}/${skill.slug}`)} data-collection-skill={skill.id}>
      <div className="recommendation-collection-identity"><SkillAvatar name={skill.displayName} url={skill.avatarUrl} seed={`${skill.namespace}/${skill.slug}`} size="h-12 w-12 text-2xl" /><div><h4>{skill.displayName}</h4><p>@{skill.namespace}{version && <span>v{version.version}</span>}</p></div><span className="recommendation-collection-index" aria-hidden="true">{String(index + 1).padStart(2, '0')}</span></div>
      <div className="recommendation-collection-purpose"><span className="text-xs text-muted-foreground">{t('recommendations.skillPurpose')}</span><p>{skill.summary}</p>{skill.labels?.length ? <ul className="recommendation-skill-tags">{skill.labels.map(label => <li key={label.slug}>{label.displayName}</li>)}</ul> : null}<div className="recommendation-collection-actions"><span>{skill.ownerDisplayName && <><User aria-hidden="true" className="h-3.5 w-3.5" />{skill.ownerDisplayName}</>}</span><Link to="/space/$namespace/$slug" params={{ namespace: skill.namespace, slug: skill.slug }} onClick={onSkillNavigate}>{t('recommendations.viewSkill')}<ArrowRight aria-hidden="true" className="h-4 w-4" /></Link></div></div>
    </article>
  })}</div>
}

/** Replaces the carousel inside its section; all skills come from the current topic. */
export function RecommendationTopicPanel({ topic, id, draft = false, restoring, onCollapse, onReady, onSkillNavigate, scrollRef, onScroll }: { scrollRef: RefObject<HTMLDivElement | null>; onScroll: (scrollTop: number) => void; topic: Topic; id: string; draft?: boolean; restoring: boolean; onCollapse: () => void; onReady: () => void; onSkillNavigate: () => void }) {
  const { t, i18n } = useTranslation()
  const panel = useRef<HTMLDivElement>(null)
  const back = useRef<HTMLButtonElement>(null)
  const animateOnMount = useRef(!restoring).current
  const query = useQuery({ queryKey: [...RECOMMENDATION_KEY, 'topic', topic.id, i18n.resolvedLanguage ?? i18n.language], queryFn: () => recommendationApi.topic(topic.id), enabled: !draft, retry: false, refetchOnWindowFocus: false, staleTime: 0, refetchOnMount: 'always', meta: { skipGlobalErrorHandler: true } })
  const detail = draft ? topic : !query.isFetching && !query.isError ? query.data : undefined
  const members = draft ? topic.skills : recommendationSkills(query.data && detail ? query.data.skills : undefined)
  const title = detail?.title ?? topic.title
  const description = detail?.description ?? topic.description
  const coverUrl = detail ? detail.coverUrl : topic.coverUrl
  const coverStyle = detail ? detail.coverStyle : topic.coverStyle
  const count = detail ? members.length : topic.skillCount
  const ready = draft || !query.isPending && !query.isFetching
  useLayoutEffect(() => { if (ready) onReady() }, [ready, onReady])
  useLayoutEffect(() => {
    if (animateOnMount) back.current?.focus({ preventScroll: true })
    if (!animateOnMount || !panel.current?.animate || window.matchMedia('(prefers-reduced-motion: reduce)').matches) return
    const animation = panel.current.animate([{ opacity: .4, transform: 'translateY(8px)' }, { opacity: 1, transform: 'none' }], { duration: 220, easing: 'cubic-bezier(.22,1,.36,1)' })
    return () => animation.cancel()
  }, [animateOnMount])
  return <div ref={panel} id={id} className="recommendation-topic-panel" role="region" aria-label={title}>
    <header className="recommendation-topic-panel-toolbar"><Button ref={back} variant="ghost" onClick={onCollapse}><ArrowLeft aria-hidden="true" className="h-4 w-4" />{t('recommendations.collapseTopic')}</Button><span className="text-xs text-muted-foreground">{t('recommendations.skillCount', { count })}</span></header>
    <div ref={scrollRef} className="recommendation-topic-panel-scroll" tabIndex={0} onScroll={event => onScroll(event.currentTarget.scrollTop)}>
    <section className="recommendation-topic-panel-hero" data-cover-style={recommendationCoverStyle(topic.id, coverStyle)} data-custom-cover={!!coverUrl || undefined}><div><span className="text-xs">{t('recommendations.topicWindowKicker')}</span><h3 className="recommendation-topic-panel-title">{title}</h3><p>{description}</p><div className="recommendation-topic-panel-members">{members.slice(0, 4).map(skill => <SkillAvatar key={skill.id} name={skill.displayName} url={skill.avatarUrl} seed={`${skill.namespace}/${skill.slug}`} size="h-7 w-7 text-xs" />)}<span>{t('recommendations.skillCount', { count })}</span></div></div><RecommendationCover topicId={topic.id} title={title} skillNames={members.map(skill => skill.displayName)} coverUrl={coverUrl} coverStyle={coverStyle} className="recommendation-topic-panel-cover" /></section>
    <section className="recommendation-topic-panel-collection"><div className="mb-5 flex items-end justify-between gap-3"><div><h4 className="text-xl font-semibold">{t('recommendations.collectionSkills')}</h4><p className="mt-1 text-sm text-muted-foreground">{t('recommendations.collectionHelp')}</p></div><span className="shrink-0 text-xs text-muted-foreground">{t('recommendations.skillCount', { count })}</span></div>
      {!draft && (query.isPending || query.isFetching) && <div className="recommendation-topic-panel-state" role="status">{t('common.loading')}</div>}
      {!draft && query.isError && <div className="recommendation-topic-panel-state" role="alert"><h4>{t('recommendations.topicUnavailable')}</h4><p>{t('recommendations.loadFailed')}</p><Button variant="outline" onClick={() => void query.refetch()}>{t('recommendations.retry')}</Button></div>}
      {detail && (members.length ? <Collection skills={members} onSkillNavigate={onSkillNavigate} /> : <div className="recommendation-topic-panel-state">{t('recommendations.topicEmpty')}</div>)}
    </section></div><footer className="recommendation-topic-panel-footer"><span>{t('recommendations.topicPublicOnly')}</span><Button variant="outline" onClick={onCollapse}>{t('recommendations.collapseTopic')}</Button></footer>
  </div>
}
