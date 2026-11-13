import { useCallback, useEffect, useId, useLayoutEffect, useRef, useState } from 'react'
import { Link, useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { ArrowRight, ChevronLeft, ChevronRight, Pencil, Search, X } from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import { recommendationApi, recommendationDisplay, RECOMMENDATION_KEY, type RecommendationDisplay } from '@/api/recommendations'
import { RecommendationSkillCard } from './recommendation-skill-card'
import { RecommendationTopicPanel } from './recommendation-topic-panel'
import { recommendationEntryKey, useRecommendationEntry, type RecommendationEntry } from './recommendation-entry-state'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { APP_FRAME_CLASS_NAME } from '@/shared/lib/layout-frame'
import { RecommendationCover } from './recommendation-cover'
import './recommendations.css'

/** Compare real clamped offsets, including a partial final group. */
export function recommendationCarouselPage(left: number, cardWidth: number, visible: number, count: number, maxLeft: number) {
  const pages = Math.ceil(count / visible)
  if (pages <= 1 || maxLeft <= 0) return 0
  if (left >= maxLeft - 1) return pages - 1
  let nearest = 0
  let distance = Infinity
  for (let index = 0; index < pages; index++) {
    const next = Math.abs(left - Math.min(index * visible * (cardWidth + 16), maxLeft))
    if (next < distance) { nearest = index; distance = next }
  }
  return nearest
}

function RecommendationPreviewContent({ content, onTopic, onManageTopics, onManageFeatured, onEditTopic, skillHeading, compact = false, topicSource = 'public', entry }: {
  entry: RecommendationEntry; entryPath?: '/' | '/featured'; topicSource?: 'public' | 'draft'; skillHeading?: string; content: RecommendationDisplay; onTopic?: (id: string) => void; onManageTopics?: () => void; onManageFeatured?: () => void; onEditTopic?: (id: string) => void; compact?: boolean
}) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const carousel = useRef<HTMLDivElement>(null)
  const [page, setPage] = useState(entry.initial.page)
  const [search, setSearch] = useState(entry.initial.search)
  const [labelSlug, setLabelSlug] = useState(entry.initial.label)
  const searchId = useId()
  const panelId = useId()
  const [topicId, setTopicId] = useState(entry.initial.topicId)
  const topicSection = useRef<HTMLElement>(null)
  const carouselLeft = useRef(entry.initial.carouselLeft)
  const panelScroller = useRef<HTMLDivElement>(null)
  const panelScroll = useRef(entry.initial.panelScroll ?? 0)
  const restoring = useRef(!!entry.saved)
  const departed = useRef(false)
  const restoreFrame = useRef(0)
  const topics = content.topics ?? []
  const [visibleCount, setVisibleCount] = useState(3)
  const selectedTopic = topics.find(topic => topic.id === topicId)
  const expanded = !!selectedTopic
  const snapshot = useRef(entry.initial)
  snapshot.current = { topicId: selectedTopic?.id ?? null, search, label: labelSlug, page, carouselLeft: carouselLeft.current, scrollY: entry.initial.scrollY, panelScroll: panelScroll.current }
  const persist = entry.save
  const save = useCallback(() => { if (!restoring.current && !departed.current) persist({ ...snapshot.current, carouselLeft: carouselLeft.current, scrollY: window.scrollY, panelScroll: panelScroller.current?.scrollTop ?? panelScroll.current }) }, [persist])
  const onPanelScroll = useCallback((value: number) => { if (restoring.current || departed.current) return; panelScroll.current = value; save() }, [save])
  const saveBeforeSkill = useCallback(() => { save() }, [save])
  const restorePosition = useCallback(() => {
    if (!restoring.current || departed.current) return
    cancelAnimationFrame(restoreFrame.current)
    restoreFrame.current = requestAnimationFrame(() => {
      if (departed.current) return
      restoreFrame.current = requestAnimationFrame(() => {
      if (departed.current) return
      if (panelScroller.current) panelScroller.current.scrollTop = entry.initial.panelScroll ?? 0
      panelScroll.current = panelScroller.current?.scrollTop ?? 0
      window.scrollTo({ top: entry.initial.scrollY, behavior: 'instant' }); restoring.current = false
      persist({ ...snapshot.current, carouselLeft: carouselLeft.current, scrollY: window.scrollY, panelScroll: panelScroller.current?.scrollTop ?? panelScroll.current })
    }) })
  }, [entry.initial.scrollY, entry.initial.panelScroll, persist])
  useLayoutEffect(() => {
    if (!expanded && carousel.current) { carousel.current.scrollLeft = carouselLeft.current; carouselLeft.current = carousel.current.scrollLeft; restorePosition() }
  }, [expanded, restorePosition])
  useEffect(() => {
    if (topicId && !selectedTopic) setTopicId(null)
  }, [topicId, selectedTopic])
  useEffect(() => {
    const scroll = () => { if (!departed.current && (!entry.key || entry.router && recommendationEntryKey(entry.router.history.location) === entry.key)) save() }
    const unsubscribe = entry.router?.subscribe('onBeforeNavigate', event => {
      if (event.fromLocation && recommendationEntryKey(event.fromLocation) === entry.key && event.toLocation.href !== event.fromLocation.href) { save(); departed.current = true; cancelAnimationFrame(restoreFrame.current) }
    })
    window.addEventListener('scroll', scroll, { passive: true }); window.addEventListener('pagehide', scroll)
    return () => { unsubscribe?.(); window.removeEventListener('scroll', scroll); window.removeEventListener('pagehide', scroll); cancelAnimationFrame(restoreFrame.current) }
  }, [entry.key, entry.router, save])
  useEffect(() => { save() }, [topicId, search, labelSlug, page, save])
  const anchorSection = () => { const section = topicSection.current; if (section && section.getBoundingClientRect().top < 88) section.scrollIntoView({ block: 'start', behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth' }) }
  function collapseTopic() { const currentId = topicId; setTopicId(null); requestAnimationFrame(() => { anchorSection(); [...(topicSection.current?.querySelectorAll<HTMLButtonElement>('[data-topic-trigger]') ?? [])].find(button => button.dataset.topicTrigger === currentId)?.focus({ preventScroll: true }) }) }
  useEffect(() => {
    const element = carousel.current
    if (!element) return
    const observer = new ResizeObserver(() => {
      const card = element.firstElementChild
      if (!(card instanceof HTMLElement) || !card.offsetWidth) return
      const count = Math.max(1, Math.round((element.clientWidth + 16) / (card.offsetWidth + 16)))
      setVisibleCount(count)
      setPage(recommendationCarouselPage(element.scrollLeft, card.offsetWidth, count, topics.length, element.scrollWidth - element.clientWidth))
    })
    observer.observe(element)
    return () => observer.disconnect()
  }, [topics.length, expanded])
  const pages = Math.ceil(topics.length / visibleCount)
  const boundedPage = Math.min(page, Math.max(0, pages - 1))
  const featured = content.featuredSkills
  const labels = [...new Map(featured.flatMap(skill => skill.labels ?? []).map(label => [label.slug, label])).values()]
  const activeLabel = labels.some(label => label.slug === labelSlug) ? labelSlug : ''
  const keyword = search.trim().toLocaleLowerCase()
  const filtered = featured.filter(skill => (!activeLabel || skill.labels?.some(label => label.slug === activeLabel))
    && (!keyword || [skill.displayName, skill.summary, skill.namespace, skill.slug, ...(skill.labels ?? []).map(label => label.displayName)].some(text => text?.toLocaleLowerCase().includes(keyword))))
  const openSkill = (namespace: string, slug: string) => { saveBeforeSkill(); void navigate({ to: '/space/$namespace/$slug', params: { namespace, slug } }) }
  function changePage(next: number) {
    const bounded = Math.max(0, Math.min(next, pages - 1))
    setPage(bounded)
    const target = carousel.current?.children[bounded * visibleCount]
    if (target instanceof HTMLElement && carousel.current) {
      carousel.current.scrollTo({ left: target.offsetLeft - carousel.current.offsetLeft,
        behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth' })
    }
  }
  return <div className="recommendations space-y-8">
    {(topics.length > 0 || onManageTopics) && <section ref={topicSection} aria-label={t('recommendations.topics')} className="recommendation-topics-section space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3"><div><h2 className="text-xl font-semibold">{t('recommendations.topics')}</h2><p className="mt-1 text-sm text-muted-foreground">{t('recommendations.topicsHelp')}</p></div>
        <div className="flex items-center gap-2">{onManageTopics && <Button variant="outline" size="sm" onClick={onManageTopics}>{t('recommendations.manageTopics')}</Button>}{!expanded && pages > 1 && <><span className="text-xs tabular-nums text-muted-foreground" aria-live="polite">{boundedPage + 1} / {pages}</span><Button variant="outline" size="icon" aria-label={t('recommendations.previous')} disabled={boundedPage === 0} onClick={() => changePage(boundedPage - 1)}><ChevronLeft className="h-4 w-4" /></Button><Button variant="outline" size="icon" aria-label={t('recommendations.next')} disabled={boundedPage >= pages - 1} onClick={() => changePage(boundedPage + 1)}><ChevronRight className="h-4 w-4" /></Button></>}</div>
      </div>
      <div hidden={expanded} ref={carousel} onScroll={event => { if (expanded) return; const element = event.currentTarget; carouselLeft.current = element.scrollLeft; const card = element.firstElementChild; if (card instanceof HTMLElement) setPage(recommendationCarouselPage(element.scrollLeft, card.offsetWidth, visibleCount, topics.length, element.scrollWidth - element.clientWidth)) }} className="recommendation-carousel" tabIndex={0} aria-label={t('recommendations.topics')}>
        {topics.map((topic) => <article key={topic.id} className="recommendation-topic" data-topic-card={topic.id}>
          <RecommendationCover topicId={topic.id} coverUrl={topic.coverUrl} coverStyle={topic.coverStyle} title={topic.title} skillNames={topic.skills.map(skill => skill.displayName)} />
          <div className="space-y-3 p-5"><div className="flex items-start justify-between gap-2"><h3 className="text-lg font-semibold [overflow-wrap:anywhere]">{topic.title}</h3>{onEditTopic && <Button variant="ghost" size="xs" className="w-6 shrink-0 !px-0" onClick={() => onEditTopic(topic.id)} aria-label={t('recommendations.editNamed', { name: topic.title })}><Pencil className="h-3.5 w-3.5" /></Button>}</div><p className="recommendation-topic-description text-sm leading-5 text-muted-foreground [overflow-wrap:anywhere]">{topic.description}</p>
            <div className="flex items-center justify-between gap-2 pt-3"><span className="text-xs text-muted-foreground">{t('recommendations.skillCount', { count: topic.skillCount ?? 0 })}</span><Button data-topic-trigger={topic.id} size="sm" variant="ghost" aria-expanded={expanded && topicId === topic.id} aria-controls={panelId} onClick={() => { if (onTopic) { onTopic(topic.id); return } restoring.current = false; panelScroll.current = 0; setTopicId(topic.id); requestAnimationFrame(anchorSection) }}>{t('recommendations.browseTopic')}<ArrowRight className="h-4 w-4" /></Button></div>
          </div>
        </article>)}
      </div>
      {!expanded && pages > 1 && <div className="recommendation-page-dots" role="group" aria-label={t('recommendations.topicPages')}>
        {Array.from({ length: pages }, (_, index) => <button key={index} type="button" aria-label={t('recommendations.topicPage', { page: index + 1 })} aria-current={boundedPage === index ? 'true' : undefined} className={boundedPage === index ? 'is-current' : ''} onClick={() => changePage(index)}><span /></button>)}
      </div>}
      {selectedTopic && <RecommendationTopicPanel key={selectedTopic.id} topic={selectedTopic} id={panelId} draft={topicSource === 'draft'} restoring={restoring.current} onCollapse={collapseTopic} onReady={restorePosition} onSkillNavigate={saveBeforeSkill} scrollRef={panelScroller} onScroll={onPanelScroll} />}
    </section>}
    {(featured.length > 0 || onManageFeatured) && <section className="recommendation-discovery space-y-4" aria-label={skillHeading ?? t('recommendations.discover')}>
      <div className="flex flex-wrap items-end justify-between gap-3"><div><h2 className="text-xl font-semibold">{skillHeading ?? t('recommendations.discover')}</h2>{!skillHeading && <p className="mt-1 text-sm text-muted-foreground">{t('recommendations.discoveryHelp')}</p>}</div>
        <div className="flex max-w-full flex-wrap items-center gap-2">{onManageFeatured && <Button variant="outline" size="sm" onClick={onManageFeatured}>{t('recommendations.manageFeatured')}</Button>}
          <div className="recommendation-search"><label className="sr-only" htmlFor={searchId}>{t('recommendations.searchFeatured')}</label><Search aria-hidden="true" className="h-4 w-4" /><Input id={searchId} type="search" value={search} onChange={event => setSearch(event.target.value)} placeholder={t('recommendations.searchFeatured')} /></div>
        </div>
      </div>
      {labels.length > 0 && <div className="recommendation-labels" role="group" aria-label={t('recommendations.filterByLabel')}>
        <Button type="button" size="sm" variant={activeLabel ? 'secondary' : 'default'} aria-pressed={!activeLabel} onClick={() => setLabelSlug('')}>{t('recommendations.allLabels')}</Button>
        {labels.map(label => <Button key={label.slug} type="button" size="sm" variant={activeLabel === label.slug ? 'default' : 'secondary'} aria-pressed={activeLabel === label.slug} onClick={() => setLabelSlug(label.slug)}>{label.displayName}</Button>)}
      </div>}
      <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-muted-foreground"><span role="status">{t('recommendations.resultCount', { count: filtered.length })}</span>{!compact && <Link to="/featured" className="inline-flex items-center gap-1 text-sm font-medium hover:underline">{t('recommendations.viewAll')}<ArrowRight className="h-4 w-4" /></Link>}</div>
      {filtered.length ? <div className="recommendation-grid grid gap-4 sm:grid-cols-2 xl:grid-cols-3">{filtered.map(skill => <RecommendationSkillCard key={skill.id} skill={skill} onClick={() => openSkill(skill.namespace, skill.slug)} />)}</div> : <div className="recommendation-empty"><p>{t('recommendations.noFeaturedResults')}</p>{(keyword || activeLabel) && <Button type="button" variant="ghost" size="sm" onClick={() => { setSearch(''); setLabelSlug('') }}><X aria-hidden className="h-4 w-4" />{t('recommendations.clearFilters')}</Button>}</div>}
    </section>}
    {!topics.length && !featured.length && <p className="rounded-xl border border-dashed p-8 text-center text-sm text-muted-foreground">{t('recommendations.empty')}</p>}
  </div>
}

export function HomepageRecommendations() {
  const { t, i18n } = useTranslation()
  const query = useQuery({ queryKey: [...RECOMMENDATION_KEY, i18n.resolvedLanguage ?? i18n.language], queryFn: recommendationApi.get, staleTime: 30_000, retry: false, meta: { skipGlobalErrorHandler: true } })
  if (!query.data || !(query.data.topics?.length || query.data.featuredSkills?.length)) return null
  return <section className="relative z-10 bg-background py-12"><div className={APP_FRAME_CLASS_NAME}><div className="mb-8 flex flex-wrap items-end justify-between gap-4"><div><h2 className="text-3xl font-semibold">{t('recommendations.featured')}</h2><p className="mt-2 text-muted-foreground">{t('recommendations.homepageHelp')}</p></div><Link to="/featured" className="inline-flex items-center gap-2 text-sm font-medium hover:underline">{t('recommendations.viewAll')}<ArrowRight className="h-4 w-4" /></Link></div><RecommendationPreview compact entryPath="/" content={recommendationDisplay(query.data)} /></div></section>
}

export function RecommendationPreview(props: Omit<Parameters<typeof RecommendationPreviewContent>[0], 'entry'>) {
  const entry = useRecommendationEntry(props.topicSource !== 'draft', props.entryPath)
  return <RecommendationPreviewContent key={entry.key || 'local-preview'} {...props} entry={entry} />
}
