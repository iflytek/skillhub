import { Link, useParams } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { ArrowLeft } from 'lucide-react'
import { recommendationApi, recommendationDisplay, recommendationSkills, RECOMMENDATION_KEY } from '@/api/recommendations'
import { RecommendationCover } from '@/features/recommendation/recommendation-cover'
import { RecommendationPreview } from '@/features/recommendation/recommendation-preview'
import { Button } from '@/shared/ui/button'

export function FeaturedPage() {
  const { t, i18n } = useTranslation()
  const query = useQuery({ queryKey: [...RECOMMENDATION_KEY, i18n.resolvedLanguage ?? i18n.language], queryFn: recommendationApi.get, retry: false })
  return <div className="space-y-6"><Link to="/" className="inline-flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground"><ArrowLeft className="h-4 w-4" />{t('recommendations.home')}</Link><h1 className="text-3xl font-semibold">{t('recommendations.featured')}</h1>
    {query.isPending && <p role="status">{t('common.loading')}</p>}{query.isError && <p role="alert">{t('recommendations.loadFailed')}</p>}{query.data && <RecommendationPreview compact entryPath="/featured" content={recommendationDisplay(query.data)} />}</div>
}
export function RecommendationTopicPage() {
  const { t, i18n } = useTranslation()
  const { topicId } = useParams({ strict: false })
  const query = useQuery({ queryKey: [...RECOMMENDATION_KEY, 'topic', topicId, i18n.resolvedLanguage ?? i18n.language], queryFn: () => recommendationApi.topic(topicId ?? ''), enabled: !!topicId, retry: false })
  return <div className="space-y-6"><Link to="/featured" className="inline-flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground"><ArrowLeft className="h-4 w-4" />{t('recommendations.backToFeatured')}</Link>
    {query.isPending && <p role="status">{t('common.loading')}</p>}{query.isError && <div role="alert" className="space-y-3"><h1 className="text-xl font-semibold">{t('recommendations.topicUnavailable')}</h1><Button variant="outline" onClick={() => void query.refetch()}>{t('recommendations.retry')}</Button></div>}{query.data && <><div className="recommendation-topic-hero"><RecommendationCover topicId={topicId ?? ''} coverStyle={query.data.coverStyle} coverUrl={query.data.coverUrl} title={query.data.title} skillNames={recommendationSkills(query.data.skills).map(skill => skill.displayName)} /><div className="recommendation-topic-hero-copy"><span className="text-xs font-medium text-muted-foreground">{t('recommendations.topics')}</span><h1 className="text-3xl font-semibold [overflow-wrap:anywhere]">{query.data.title}</h1><p className="text-muted-foreground [overflow-wrap:anywhere]">{query.data.description}</p><span className="text-xs text-muted-foreground">{t('recommendations.skillCount', { count: query.data.skills?.length ?? 0 })}</span></div></div><RecommendationPreview compact skillHeading={t('recommendations.collectionSkills')} content={{ topics: [], featuredSkills: recommendationSkills(query.data.skills) }} /></>}</div>
}
