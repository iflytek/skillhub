import { startTransition, useCallback, useEffect, useRef, useState } from 'react'
import { useQueryClient, type InfiniteData } from '@tanstack/react-query'
import { useNavigate, useSearch } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Check, Loader2 } from 'lucide-react'
import type { PagedResponse, SkillSource, SkillSummary } from '@/api/types'
import { useAuth } from '@/features/auth/use-auth'
import { MarketSearchControls } from '@/features/search/market-search-controls'
import { flattenMarketSkills, getMarketSkillsKey, useMarketSkills, type MarketSearchParams } from '@/features/search/use-market-skills'
import { clearMarketScroll, getMarketScroll, saveMarketScroll, useMarketScroll } from '@/features/search/market-scroll'
import { SkillCard } from '@/features/skill/skill-card'
import { SkeletonList } from '@/shared/components/skeleton-loader'
import { EmptyState } from '@/shared/components/empty-state'
import { useVisibleLabels } from '@/shared/hooks/use-label-queries'
import { useMyStars } from '@/shared/hooks/use-user-queries'
import { toRouterPath } from '@/shared/lib/base-path'
import { formatNamespaceSearchInput, normalizeSearchQuery, parseNamespaceSearchInput } from '@/shared/lib/search-query'
import { Button } from '@/shared/ui/button'

const PAGE_SIZE = 12
interface MarketFilters { q: string; namespace: string; source: SkillSource; label: string; sort: string; starredOnly: boolean }

function filterStarredSkills(skills: SkillSummary[], query: string, namespace: string): SkillSummary[] {
  const normalizedQuery = query.trim().toLowerCase()
  const normalizedNamespace = namespace.trim().toLowerCase()

  return skills.filter((skill) => {
    const matchesNamespace = !normalizedNamespace || skill.namespace.toLowerCase() === normalizedNamespace
    if (!matchesNamespace) {
      return false
    }
    if (!normalizedQuery) {
      return true
    }
    return [skill.displayName, skill.summary, skill.namespace, skill.slug]
        .filter(Boolean)
        .some((value) => value!.toLowerCase().includes(normalizedQuery))
  })
}

function sortStarredSkills(skills: SkillSummary[], sort: string): SkillSummary[] {
  const sorted = [...skills]
  if (sort === 'downloads') {
    return sorted.sort((left, right) => right.downloadCount - left.downloadCount)
  }
  if (sort === 'newest' || sort === 'relevance') {
    return sorted.sort((left, right) => new Date(right.updatedAt).getTime() - new Date(left.updatedAt).getTime())
  }
  return sorted
}

export function SearchPage() {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const searchParams = useSearch({ from: '/search' })
  const { isAuthenticated, user, isLoading: isAuthLoading } = useAuth()
  const q = normalizeSearchQuery(searchParams.q || '')
  const namespace = (searchParams.namespace || '').replace(/^@/, '')
  const source = searchParams.source ?? (namespace ? 'all' : 'platform')
  const selectedLabel = searchParams.label || ''
  const sort = searchParams.sort || 'newest'
  const starredOnly = searchParams.starredOnly ?? false
  const language = i18n.resolvedLanguage || i18n.language || 'en'
  const viewer = user?.userId ?? null
  const params: MarketSearchParams = { q, namespace: namespace || undefined, source, label: selectedLabel || undefined, sort, size: PAGE_SIZE }
  const queryKey = getMarketSkillsKey(params, viewer, language)
  const scrollKey = JSON.stringify([queryKey, starredOnly])
  const [queryInput, setQueryInput] = useState(formatNamespaceSearchInput(namespace, q))
  const [favoriteWindow, setFavoriteWindow] = useState(() => ({ key: scrollKey, count: getMarketScroll(scrollKey)?.favoritesVisible ?? PAGE_SIZE }))
  const sentinel = useRef<HTMLDivElement>(null)
  useEffect(() => { setQueryInput(formatNamespaceSearchInput(namespace, q)) }, [namespace, q])

  const query = useMarketSkills(params, viewer, language, !starredOnly && !isAuthLoading)
  const { data: labels } = useVisibleLabels()
  const favorites = useMyStars(starredOnly && isAuthenticated)
  const filteredFavorites = starredOnly ? sortStarredSkills(filterStarredSkills(favorites.data ?? [], q, namespace), sort) : []
  const favoritesVisible = favoriteWindow.key === scrollKey ? favoriteWindow.count : PAGE_SIZE
  const displayItems = starredOnly ? filteredFavorites.slice(0, favoritesVisible) : flattenMarketSkills(query.data?.pages)
  const resultCount = starredOnly ? filteredFavorites.length : query.data?.pages[0]?.total ?? 0
  const isPageLoading = !!isAuthLoading || (starredOnly ? favorites.isLoading : query.isLoading)
  const isFetchingMore = !starredOnly && query.isFetchingNextPage
  const isUpdatingResults = starredOnly ? favorites.isFetching && !favorites.isLoading : query.isFetching && !query.isLoading && !query.isFetchingNextPage
  const hasMore = starredOnly ? favoritesVisible < filteredFavorites.length : !!query.hasNextPage
  const loadFailed = starredOnly ? favorites.isError : query.isError
  const restoring = useMarketScroll(scrollKey, !isPageLoading && displayItems.length > 0)

  const applyFilters = (next: MarketFilters, replace = false) => {
    const nextParams: MarketSearchParams = { q: next.q, namespace: next.namespace || undefined, source: next.source, label: next.label || undefined, sort: next.sort, size: PAGE_SIZE }
    const nextKey = getMarketSkillsKey(nextParams, viewer, language)
    const nextScrollKey = JSON.stringify([nextKey, next.starredOnly])
    clearMarketScroll(nextScrollKey)
    setFavoriteWindow({ key: nextScrollKey, count: PAGE_SIZE })
    queryClient.setQueryData<InfiniteData<PagedResponse<SkillSummary>, number>>(nextKey, old => old ? { pages: old.pages.slice(0, 1), pageParams: old.pageParams.slice(0, 1) } : undefined)
    if (JSON.stringify(nextKey) === JSON.stringify(queryKey) && !next.starredOnly) void query.refetch()
    if (typeof window !== 'undefined') window.scrollTo({ top: 0, behavior: 'auto' })
    navigate({ to: '/search', search: { ...next, page: 0 }, ...(replace ? { replace: true } : {}) })
  }
  const current: MarketFilters = { q, namespace, source, label: selectedLabel, sort, starredOnly }
  const handleSearch = (text: string) => {
    const parsed = parseNamespaceSearchInput(text)
    setQueryInput(text)
    startTransition(() => applyFilters({ ...current, q: parsed.query, namespace: parsed.namespace, source: searchParams.source ?? (parsed.namespace ? 'all' : 'platform') }, true))
  }
  const handleSourceChange = (value: SkillSource) => applyFilters({ ...current, source: value })
  const handleStarredToggle = () => {
    if (!isAuthenticated) {
      navigate({ to: '/login', search: { returnTo: toRouterPath(window.location.pathname, window.location.search, window.location.hash) } })
      return
    }
    applyFilters({ ...current, starredOnly: !starredOnly })
  }
  const fetchNextPage = query.fetchNextPage
  const loadMore = useCallback(() => {
    if (!hasMore || isFetchingMore || isUpdatingResults || restoring) return
    if (starredOnly) setFavoriteWindow({ key: scrollKey, count: favoritesVisible + PAGE_SIZE })
    else void fetchNextPage({ cancelRefetch: false })
  }, [hasMore, isFetchingMore, isUpdatingResults, restoring, starredOnly, scrollKey, favoritesVisible, fetchNextPage])
  useEffect(() => {
    if (!hasMore || isFetchingMore || isUpdatingResults || loadFailed || restoring || !sentinel.current || typeof IntersectionObserver === 'undefined') return
    const observer = new IntersectionObserver(entries => { if (entries.some(entry => entry.isIntersecting)) loadMore() }, { rootMargin: '320px' })
    observer.observe(sentinel.current)
    return () => observer.disconnect()
  }, [scrollKey, hasMore, isFetchingMore, isUpdatingResults, loadFailed, restoring, displayItems.length, loadMore])

  const retry = () => {
    if (starredOnly) void favorites.refetch()
    else if (query.isFetchNextPageError) void query.fetchNextPage({ cancelRefetch: false })
    else void query.refetch()
  }

  return (
    <div className="space-y-4 sm:space-y-6">
      <MarketSearchControls query={queryInput} onQueryChange={setQueryInput} onSearch={handleSearch}
        source={source} onSourceChange={handleSourceChange} labels={labels ?? []} selectedLabel={selectedLabel}
        onLabelChange={label => applyFilters({ ...current, label: label === selectedLabel ? '' : label })}
        starredOnly={starredOnly} onStarredToggle={handleStarredToggle} sort={sort} onSortChange={value => applyFilters({ ...current, sort: value })}
        namespace={namespace} onNamespaceClear={() => applyFilters({ ...current, namespace: '', source: searchParams.source ?? 'platform' })}
        resultCount={resultCount} isUpdating={isUpdatingResults} />
      {isPageLoading ? <div role="status" aria-label={t('common.loading')}><SkeletonList count={PAGE_SIZE} /></div> : loadFailed && displayItems.length === 0 ? (
        <div className="text-center" role="alert"><EmptyState title={t('search.source.loadFailed')} /><Button data-analytics-id="search.source.retry" variant="outline" onClick={retry}>{t('search.source.retry')}</Button></div>
      ) : displayItems.length > 0 ? (
        <>
          <div className="grid grid-cols-1 gap-5 md:grid-cols-2 lg:grid-cols-3" aria-busy={isFetchingMore}>
            {displayItems.map(skill => <SkillCard key={skill.id} variant="market" analyticsId="skill.card.open" skill={skill} highlightStarred onClick={() => {
              saveMarketScroll(scrollKey, favoritesVisible)
              navigate({ to: `/space/${skill.namespace}/${encodeURIComponent(skill.slug)}`, search: { returnTo: toRouterPath(window.location.pathname, window.location.search) } })
            }} />)}
          </div>
          <div ref={sentinel} className="flex min-h-16 flex-wrap items-center justify-center gap-3 py-4 text-sm text-muted-foreground">
            {isFetchingMore ? <span role="status" className="inline-flex items-center gap-2"><Loader2 aria-hidden="true" className="h-4 w-4 animate-spin motion-reduce:animate-none" />{t('search.market.loadingMore')}</span>
              : loadFailed ? <div role="alert" className="flex flex-wrap items-center justify-center gap-3"><span>{t('search.market.appendFailed')}</span><Button data-analytics-id="search.results.retry" variant="outline" size="sm" disabled={isUpdatingResults} onClick={retry}>{t('search.source.retry')}</Button></div>
              : hasMore ? <Button data-analytics-id="search.results.more" variant="ghost" size="sm" disabled={isUpdatingResults || restoring} onClick={loadMore}>{t('search.market.loadMore')}</Button>
              : <span role="status" className="inline-flex items-center gap-2"><Check aria-hidden="true" className="h-4 w-4" />{t('search.market.allLoaded', { count: displayItems.length })}</span>}
          </div>
        </>
      ) : <EmptyState title={starredOnly ? t('search.noStarredResults') : source === 'github' && !q && !namespace && !selectedLabel ? t('search.market.githubEmpty') : t('search.noResults')}
        description={starredOnly ? (q ? t('search.noStarredResultsFor', { q }) : t('search.noStarredSkills')) : q ? t('search.noResultsFor', { q }) : undefined}
        action={q || namespace || selectedLabel ? <Button onClick={() => { setQueryInput(''); applyFilters({ ...current, q: '', namespace: '', label: '' }) }}>{t('search.market.reset')}</Button>
          : !starredOnly && source === 'github' ? <Button onClick={() => handleSourceChange('platform')}>{t('search.market.viewPlatform')}</Button> : undefined} />}
    </div>
  )
}
