import { useCallback, useEffect } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useNavigate, useSearch } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Settings2, SlidersHorizontal } from 'lucide-react'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'
import { Button } from '@/shared/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/shared/ui/dialog'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/shared/ui/tabs'
import { GithubSyncCollectionDrawer } from '@/features/github-sync/github-sync-collection-drawer'
import { GithubSyncOverview } from '@/features/github-sync/github-sync-overview'
import { GithubSyncRepositories } from '@/features/github-sync/github-sync-repositories'
import { GithubSyncRunDetail } from '@/features/github-sync/github-sync-run-detail'
import { GithubSyncRunsSection } from '@/features/github-sync/github-sync-runs-section'
import { GithubSyncSettingsDrawer } from '@/features/github-sync/github-sync-settings-drawer'
import { GithubSyncSourceDetail } from '@/features/github-sync/github-sync-source-detail'
import { DEEP_DISCOVERY_QUERY_KEY, useDeepDiscoveryVisible } from '@/features/github-sync/use-github-deep-discovery'
import '@/features/github-sync/github-sync-colors.css'

export function GithubSyncPage() {
  const { t } = useTranslation()
  const search = useSearch({ from: '/dashboard/github-sync' })
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const visible = useDeepDiscoveryVisible()
  const tab = search.sourceId || search.repositoryId ? 'repositories' : search.tab ?? 'overview'
  const drawer = search.drawer

  const updateSearch = useCallback((changes: Partial<typeof search>) => {
    void navigate({ to: '/dashboard/github-sync', search: { ...search, ...changes } })
  }, [navigate, search])

  useEffect(() => {
    if (!visible) {
      void queryClient.cancelQueries({ queryKey: ['github-sync'] })
      void queryClient.cancelQueries({ queryKey: DEEP_DISCOVERY_QUERY_KEY })
    }
  }, [queryClient, visible])

  if (search.runId) return <div className="github-sync-surface"><GithubSyncRunDetail runId={search.runId} visible={visible} onBack={() => updateSearch({ runId: undefined })} /></div>

  return <div className="github-sync-surface space-y-5 animate-fade-up">
    <div className="sync-hero flex flex-wrap items-start justify-between gap-4">
      <div><DashboardPageHeader title={t('githubSync.unifiedTitle')} subtitle={t('githubSync.unifiedSubtitle')} /><span className="mt-2 inline-flex rounded border px-2 py-0.5 text-xs text-muted-foreground">@github</span></div>
      <div className="flex flex-wrap gap-2">
        <Button data-analytics-id="github.collection.open" size="sm" variant="outline" onClick={() => updateSearch({ drawer: 'collection' })}><SlidersHorizontal className="h-4 w-4" />{t('githubSync.collectionConfig')}</Button>
        <Button data-analytics-id="github.settings.open" size="sm" variant="outline" onClick={() => updateSearch({ drawer: 'settings' })}><Settings2 className="h-4 w-4" />{t('githubSync.systemSettings')}</Button>
      </div>
    </div>
    <Tabs value={tab} onValueChange={(value) => updateSearch({ tab: value as 'overview' | 'repositories' | 'runs', sourceId: undefined, repositoryId: undefined })} className="space-y-4">
      <TabsList className="sync-tabs flex w-full justify-start gap-4 overflow-x-auto sm:gap-6">
        <TabsTrigger data-analytics-id="github.overview.tab" value="overview">{t('githubSync.overview')}</TabsTrigger>
        <TabsTrigger data-analytics-id="github.repositories.tab" value="repositories">{t('githubSync.repositoriesTab')}</TabsTrigger>
        <TabsTrigger data-analytics-id="github.runs.tab" value="runs">{t('githubSync.runs')}</TabsTrigger>
      </TabsList>
      <TabsContent value="overview">
        <GithubSyncOverview visible={visible && tab === 'overview'} onCollection={() => updateSearch({ drawer: 'collection' })} onSettings={() => updateSearch({ drawer: 'settings' })} onRuns={() => updateSearch({ tab: 'runs' })} onRun={(runId) => updateSearch({ runId })} />
      </TabsContent>
      <TabsContent value="repositories">
        <GithubSyncRepositories visible={visible && tab === 'repositories'} search={search} onChange={updateSearch} />
      </TabsContent>
      <TabsContent value="runs">
        <GithubSyncRunsSection visible={visible && tab === 'runs'} search={search} onChange={updateSearch} />
      </TabsContent>
    </Tabs>
    {drawer === 'collection' ? <GithubSyncCollectionDrawer visible={visible} onClose={() => updateSearch({ drawer: undefined })} onRepository={(repositoryId) => updateSearch({ drawer: undefined, tab: 'repositories', repositoryId })} /> : null}
    {drawer === 'settings' ? <GithubSyncSettingsDrawer visible={visible} onClose={() => updateSearch({ drawer: undefined })} /> : null}
    {search.sourceId ? <Dialog open onOpenChange={(open) => { if (!open) updateSearch({ sourceId: undefined }) }}>
      <DialogContent closeAnalyticsId="github.source.close" className="github-sync-surface !left-auto !right-0 !top-0 !h-dvh !max-h-dvh !grid-rows-[auto_minmax(0,1fr)] !gap-0 !overflow-hidden w-[min(100vw,40rem)] !translate-x-0 !translate-y-0 !rounded-none p-0">
        <DialogHeader className="border-b px-5 py-4 text-left"><DialogTitle>{t('githubSync.skillPaths')}</DialogTitle><DialogDescription>{t('githubSync.sourceDetailHelp')}</DialogDescription></DialogHeader>
        <div className="min-h-0 overflow-y-auto px-5 py-5"><GithubSyncSourceDetail sourceId={search.sourceId} visible={visible} /></div>
      </DialogContent>
    </Dialog> : null}
  </div>
}
