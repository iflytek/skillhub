import { useEffect, useState } from 'react'
import * as Popover from '@radix-ui/react-popover'
import { Bookmark, Check, ChevronDown, GitBranch, Layers, LayoutGrid, SlidersHorizontal } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import type { LabelItem, SkillSource } from '@/api/types'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { Dialog, DialogClose, DialogContent, DialogDescription, DialogTitle, DialogTrigger } from '@/shared/ui/dialog'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'
import { getPortalContainer } from '@/shared/lib/portal-container'
import { cn } from '@/shared/lib/utils'
import { SearchBar } from './search-bar'

interface MarketSearchControlsProps {
  query: string
  onQueryChange: (value: string) => void
  onSearch: (value: string) => void
  source: SkillSource
  onSourceChange: (value: SkillSource) => void
  labels: LabelItem[]
  selectedLabel: string
  onLabelChange: (value: string) => void
  starredOnly: boolean
  onStarredToggle: () => void
  sort: string
  onSortChange: (value: string) => void
  namespace: string
  onNamespaceClear: () => void
  resultCount: number
  isUpdating: boolean
}

export function MarketSearchControls(props: MarketSearchControlsProps) {
  const { t } = useTranslation()
  const [desktopOpen, setDesktopOpen] = useState(false)
  const [mobileOpen, setMobileOpen] = useState(false)
  const [labelSearch, setLabelSearch] = useState('')
  const [draftLabel, setDraftLabel] = useState(props.selectedLabel)
  const selected = props.labels.find(label => label.slug === props.selectedLabel)
  const searchTerm = labelSearch.trim().toLocaleLowerCase()
  const matchingLabels = props.labels.filter(label => `${label.displayName} ${label.slug}`.toLocaleLowerCase().includes(searchTerm))
  const desktopLabels = searchTerm ? matchingLabels : props.labels.slice(6)

  useEffect(() => {
    if ((!desktopOpen && !mobileOpen) || typeof window.matchMedia !== 'function') return
    const breakpoint = window.matchMedia('(min-width: 640px)')
    const closePanels = () => { setDesktopOpen(false); setMobileOpen(false) }
    breakpoint.addEventListener('change', closePanels)
    return () => breakpoint.removeEventListener('change', closePanels)
  }, [desktopOpen, mobileOpen])

  const favoritesToggle = (mobile: boolean) => (
    <Button size="sm" variant="outline" aria-pressed={props.starredOnly}
      data-analytics-id="search.starred.filter" title={t('search.market.onlyFavorites')} onClick={props.onStarredToggle}
      className={cn(
        'shrink-0 gap-1.5 rounded-full px-3 font-normal text-[hsl(var(--text-secondary))] hover:bg-accent/50',
        mobile ? 'absolute right-0 top-1/2 h-7 w-[84px] -translate-y-1/2 text-xs sm:hidden' : 'ml-auto hidden h-8 w-28 text-[13px] sm:inline-flex',
        props.starredOnly && 'border-primary/15 bg-accent font-medium text-accent-foreground hover:bg-accent',
      )}>
      <Bookmark aria-hidden="true" fill={props.starredOnly ? 'currentColor' : 'none'} className={cn('h-4 w-4 shrink-0', !props.starredOnly && 'text-muted-foreground')} />
      <span className="min-w-0 truncate">{t(mobile ? 'search.market.favorites' : 'search.market.onlyFavorites')}</span>
    </Button>
  )

  const labelOptions = (mobile: boolean) => (
    <>
      <Input className={mobile ? undefined : 'h-8 text-sm'} aria-label={t('search.market.findLabel')} placeholder={t('search.market.findLabel')} value={labelSearch} onChange={event => setLabelSearch(event.target.value)} />
      <div className={cn('overflow-y-auto overscroll-contain', mobile ? 'max-h-72 space-y-1' : 'max-h-60 space-y-0.5')} role="group" aria-label={t('search.market.allLabels')}>
        {(mobile ? [{ slug: '', displayName: t('search.market.all') }, ...matchingLabels] : desktopLabels).map(label => {
          const active = (mobile ? draftLabel : props.selectedLabel) === label.slug
          return (
            <button key={label.slug} type="button" aria-pressed={active}
              className={cn('flex w-full items-center justify-between gap-3 rounded-md text-left text-sm hover:bg-accent focus-visible:outline focus-visible:outline-2 focus-visible:outline-ring', mobile ? 'min-h-9 px-3 py-2' : 'h-8 px-2 py-1', active && 'bg-accent text-accent-foreground')}
              onClick={() => {
                if (mobile) setDraftLabel(label.slug)
                else { props.onLabelChange(label.slug); setDesktopOpen(false) }
              }}>
              <span className={cn('min-w-0', mobile ? 'break-words' : 'truncate')} title={label.displayName}>{label.displayName}</span>
              {active && <Check aria-hidden="true" className="h-4 w-4 shrink-0" />}
            </button>
          )
        })}
        {(mobile ? matchingLabels : desktopLabels).length === 0 && <p className="px-3 py-4 text-sm text-muted-foreground">{t('search.market.noLabels')}</p>}
      </div>
    </>
  )

  return (
    <div className="space-y-4 sm:space-y-6">
      <section aria-label={t('search.market.filters')} className="rounded-lg border border-border bg-card p-4 sm:p-6">
        <h1 className="sr-only">{t('nav.marketplace')}</h1>
        <SearchBar variant="market" analyticsId="search.query.submit" value={props.query} onChange={props.onQueryChange} onSearch={props.onSearch}
          placeholder={t('search.market.placeholder')} isSearching={props.isUpdating} />

        <div className="mt-7 flex flex-col gap-2 sm:flex-row sm:items-center sm:gap-5">
          {!props.starredOnly ? (
            <>
              <span className="text-xs text-muted-foreground" id="market-source-label">{t('search.source.label')}</span>
              <div role="group" aria-labelledby="market-source-label" className="grid grid-cols-3 gap-1 rounded-lg bg-secondary/60 p-1 sm:w-[348px]">
                {(['platform', 'github', 'all'] as const).map(value => {
                  const Icon = value === 'platform' ? LayoutGrid : value === 'github' ? GitBranch : Layers
                  return (
                    <Button key={value} size="default" variant="ghost" data-analytics-id="search.source.filter" aria-pressed={props.source === value}
                      title={t(`search.source.${value}Hint`)} onClick={() => props.onSourceChange(value)}
                      className={cn('h-8 min-w-0 gap-1.5 px-1 text-xs sm:text-[13px]', props.source === value && 'border border-border bg-card shadow-sm hover:bg-card')}>
                      <Icon aria-hidden="true" className="h-4 w-4 shrink-0" />
                      <span className="truncate">{t(`search.source.${value}`)}</span>
                    </Button>
                  )
                })}
              </div>
            </>
          ) : <span className="flex min-h-16 items-center text-sm text-muted-foreground sm:min-h-10">{t('search.market.favoriteScope')}</span>}
          {favoritesToggle(false)}
        </div>

        <div className="mt-3.5 flex flex-col gap-3 border-t border-border pt-2.5 sm:flex-row sm:items-center sm:gap-5">
          <div className="relative flex h-4 items-center justify-between sm:h-auto">
            <span className="text-xs text-muted-foreground">{t('search.market.labels')}</span>
            {favoritesToggle(true)}
          </div>
          <div className="flex min-w-0 flex-wrap items-center gap-2">
            <Button size="sm" variant={!props.selectedLabel ? 'soft' : 'outline'} aria-pressed={!props.selectedLabel} disabled={props.starredOnly} onClick={() => props.onLabelChange('')}>{t('search.market.all')}</Button>
            {!props.starredOnly && props.labels.slice(0, 6).map((label, index) => (
              <Button key={label.slug} size="sm" variant={props.selectedLabel === label.slug ? 'soft' : 'outline'}
                className={cn('max-w-40', index > 0 && 'hidden sm:inline-flex')}
                data-analytics-id="search.label.filter" aria-pressed={props.selectedLabel === label.slug}
                title={label.displayName} onClick={() => props.onLabelChange(label.slug)}>
                <span className="truncate">{label.displayName}</span>
              </Button>
            ))}

            {!props.starredOnly && <>
              {props.labels.length > 6 && <Popover.Root open={desktopOpen} onOpenChange={open => { setLabelSearch(''); setDesktopOpen(open) }}>
                <Popover.Trigger asChild>
                  <Button size="sm" variant="outline" className="hidden max-w-48 gap-1.5 sm:inline-flex">
                    <span className="truncate">{selected && !props.labels.slice(0, 6).some(label => label.slug === selected.slug) ? selected.displayName : t('search.market.moreLabels')}</span>
                    <ChevronDown aria-hidden="true" className="h-4 w-4 shrink-0" />
                  </Button>
                </Popover.Trigger>
                <Popover.Portal container={getPortalContainer()}>
                  <Popover.Content align="start" sideOffset={8} collisionPadding={16} aria-label={t('search.market.allLabels')}
                    className="z-50 w-[280px] max-w-[calc(100vw-2rem)] space-y-2 rounded-lg border border-border bg-popover p-3 text-popover-foreground shadow-lg">
                    {labelOptions(false)}
                  </Popover.Content>
                </Popover.Portal>
              </Popover.Root>}
              <Dialog open={mobileOpen} onOpenChange={open => { setDraftLabel(props.selectedLabel); setLabelSearch(''); setMobileOpen(open) }}>
                <DialogTrigger asChild>
                  <Button size="sm" variant="outline" className="max-w-40 gap-1.5 sm:hidden">
                    <SlidersHorizontal aria-hidden="true" className="h-4 w-4 shrink-0" />
                    <span className="truncate">{selected && selected.slug !== props.labels[0]?.slug ? selected.displayName : t('search.market.allLabels')}</span>
                  </Button>
                </DialogTrigger>
                <DialogContent className="bottom-0 left-0 top-auto max-h-[85dvh] w-full translate-x-0 translate-y-0 gap-4 rounded-b-none rounded-t-2xl p-4 sm:max-w-md">
                  <DialogTitle>{t('search.market.allLabels')}</DialogTitle>
                  <DialogDescription>{t('search.market.labelHelp')}</DialogDescription>
                  {labelOptions(true)}
                  <div className="flex justify-end gap-2 border-t border-border pt-4">
                    <DialogClose asChild><Button variant="outline">{t('search.market.cancel')}</Button></DialogClose>
                    <Button onClick={() => { if (draftLabel !== props.selectedLabel) props.onLabelChange(draftLabel); setMobileOpen(false) }}>{t('search.market.apply')}</Button>
                  </div>
                </DialogContent>
              </Dialog>
            </>}
          </div>
        </div>
        {props.namespace && <Button size="sm" variant="soft" className="mt-3 max-w-full" data-analytics-id="search.namespace.clear" onClick={props.onNamespaceClear}><span className="truncate">{t('search.namespaceFilter', { namespace: props.namespace })}</span></Button>}
      </section>

      <div className="flex min-w-0 flex-wrap items-center justify-between gap-3">
        <div className="flex min-w-0 flex-wrap items-center gap-3">
          <span className="text-base font-semibold">{t('search.results', { count: props.resultCount })}</span>
          <span className="hidden text-xs text-muted-foreground sm:inline">{props.starredOnly ? t('search.market.favoriteScope') : t('search.market.currentSource', { source: t(`search.source.${props.source}`) })}</span>
        </div>
        <div className="flex items-center gap-3">
          <span className="text-xs text-muted-foreground">{t('search.market.sort')}</span>
          <Select value={props.sort} onValueChange={props.onSortChange}>
            <SelectTrigger className="h-8 w-32 text-sm" aria-label={t('search.sort.label')}><SelectValue /></SelectTrigger>
            <SelectContent density="compact" align="end" sideOffset={6} className="w-[var(--radix-select-trigger-width)] min-w-0 [&_[role=option]]:pl-9 [&_[role=option]>span:first-child]:left-2.5">
              <SelectItem value="relevance" data-analytics-id="search.sort.relevance">{t('search.sort.relevance')}</SelectItem>
              <SelectItem value="downloads" data-analytics-id="search.sort.downloads">{t('search.sort.downloads')}</SelectItem>
              <SelectItem value="newest" data-analytics-id="search.sort.newest">{t('search.sort.newest')}</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </div>
    </div>
  )
}
