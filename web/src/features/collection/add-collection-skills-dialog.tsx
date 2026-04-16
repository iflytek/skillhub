import { useMemo, useState } from 'react'
import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { EmptyState } from '@/shared/components/empty-state'
import { Pagination } from '@/shared/components/pagination'
import { useVisibleLabels } from '@/shared/hooks/use-label-queries'
import { useBulkAddCollectionSkills, useCollectionAddCandidates } from '@/shared/hooks/use-collection-queries'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/shared/ui/dialog'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'

const PAGE_SIZE = 12

interface AddCollectionSkillsDialogProps {
  collectionId: string
  collectionSkillIds: number[]
  children: React.ReactNode
}

export function AddCollectionSkillsDialog({
  collectionId,
  collectionSkillIds,
  children,
}: AddCollectionSkillsDialogProps) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const [q, setQ] = useState('')
  const [label, setLabel] = useState('')
  const [sort, setSort] = useState('newest')
  const [page, setPage] = useState(0)
  const [selectedSkillIds, setSelectedSkillIds] = useState<number[]>([])
  const [recentlyAddedIds, setRecentlyAddedIds] = useState<number[]>([])

  const labelsQuery = useVisibleLabels(open)
  const candidatesQuery = useCollectionAddCandidates({
    collectionId,
    q,
    label,
    sort,
    page,
    size: PAGE_SIZE,
    collectionSkillIds,
  })
  const bulkAddMutation = useBulkAddCollectionSkills()

  const mergedAlreadyInCollectionIds = useMemo(() => {
    const base = new Set(collectionSkillIds)
    for (const skillId of recentlyAddedIds) {
      base.add(skillId)
    }
    return base
  }, [collectionSkillIds, recentlyAddedIds])

  const candidates = useMemo(
    () =>
      (candidatesQuery.data?.items ?? []).map((item) => ({
        ...item,
        alreadyInCollection: item.alreadyInCollection || mergedAlreadyInCollectionIds.has(item.id),
      })),
    [candidatesQuery.data?.items, mergedAlreadyInCollectionIds],
  )
  const addableCandidates = candidates.filter((candidate) => !candidate.alreadyInCollection)
  const selectedCount = selectedSkillIds.length
  const totalPages = Math.ceil((candidatesQuery.data?.total ?? 0) / (candidatesQuery.data?.size || PAGE_SIZE))

  const resetState = () => {
    setQ('')
    setLabel('')
    setSort('newest')
    setPage(0)
    setSelectedSkillIds([])
    setRecentlyAddedIds([])
  }

  const closeDialog = () => {
    setOpen(false)
    resetState()
  }

  const toggleSelection = (skillId: number, disabled: boolean) => {
    if (disabled) {
      return
    }
    setSelectedSkillIds((previous) =>
      previous.includes(skillId)
        ? previous.filter((id) => id !== skillId)
        : [...previous, skillId],
    )
  }

  const handleSubmit = async () => {
    if (selectedSkillIds.length === 0) {
      return
    }

    const result = await bulkAddMutation.mutateAsync({
      id: collectionId,
      skillIds: selectedSkillIds,
    })

    const settledIds = [...result.addedIds, ...result.alreadyInCollectionIds]
    if (settledIds.length > 0) {
      setRecentlyAddedIds((previous) => Array.from(new Set([...previous, ...settledIds])))
    }
    setSelectedSkillIds((previous) => previous.filter((id) => !settledIds.includes(id)))
  }

  return (
    <Dialog open={open} onOpenChange={(nextOpen) => (nextOpen ? setOpen(true) : closeDialog())}>
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent className="w-[min(calc(100vw-2rem),56rem)]" data-testid="add-collection-skills-dialog">
        <DialogHeader className="text-left sm:text-left">
          <DialogTitle className="text-left">{t('collections.addSkillDialogTitle')}</DialogTitle>
          <DialogDescription className="text-left">
            {t('collections.addSkillDialogDescription')}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          <div className="grid gap-3 md:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="collection-skill-search-input">{t('search.placeholder')}</Label>
              <Input
                id="collection-skill-search-input"
                value={q}
                onChange={(event) => {
                  setQ(event.target.value)
                  setPage(0)
                }}
                placeholder={t('search.placeholder')}
                aria-label={t('search.placeholder')}
              />
            </div>

            <div className="space-y-2">
              <Label>{t('search.filters.label')}</Label>
              <div className="flex flex-wrap gap-2">
                <Button
                  type="button"
                  size="sm"
                  variant={label === '' ? 'default' : 'outline'}
                  onClick={() => {
                    setLabel('')
                    setPage(0)
                  }}
                >
                  {t('search.sort.relevance')}
                </Button>
                {labelsQuery.data?.map((item) => (
                  <Button
                    key={item.slug}
                    type="button"
                    size="sm"
                    variant={label === item.slug ? 'default' : 'outline'}
                    onClick={() => {
                      setLabel(item.slug)
                      setPage(0)
                    }}
                  >
                    {item.displayName}
                  </Button>
                ))}
              </div>
            </div>
          </div>

          <div className="space-y-2">
            <Label>{t('search.sort.label')}</Label>
            <div className="flex flex-wrap gap-2">
              <Button
                type="button"
                size="sm"
                variant={sort === 'relevance' ? 'default' : 'outline'}
                onClick={() => {
                  setSort('relevance')
                  setPage(0)
                }}
              >
                {t('search.sort.relevance')}
              </Button>
              <Button
                type="button"
                size="sm"
                variant={sort === 'downloads' ? 'default' : 'outline'}
                onClick={() => {
                  setSort('downloads')
                  setPage(0)
                }}
              >
                {t('search.sort.downloads')}
              </Button>
              <Button
                type="button"
                size="sm"
                variant={sort === 'newest' ? 'default' : 'outline'}
                onClick={() => {
                  setSort('newest')
                  setPage(0)
                }}
              >
                {t('search.sort.newest')}
              </Button>
            </div>
          </div>

          {candidatesQuery.isLoading ? (
            <p className="text-sm text-muted-foreground">{t('collections.loading')}</p>
          ) : (
            <>
              {addableCandidates.length === 0 ? (
                <div data-testid="add-collection-skills-empty-state">
                  <EmptyState
                    title={t('collections.addDialogEmptyTitle')}
                    description={t('collections.addDialogEmptyDescription')}
                    action={(
                      <Link to="/search">
                        <Button type="button" variant="outline">
                          {t('collections.addDialogEmptyCta')}
                        </Button>
                      </Link>
                    )}
                  />
                </div>
              ) : null}

              {candidates.length > 0 ? (
                <div className="grid gap-3">
                  {candidates.map((candidate) => {
                    const isDisabled = candidate.alreadyInCollection
                    const isSelected = selectedSkillIds.includes(candidate.id)
                    return (
                      <Card
                        key={candidate.id}
                        className={`p-4 ${isSelected ? 'ring-2 ring-primary/40' : ''}`}
                      >
                        <div className="flex items-start justify-between gap-4">
                          <div className="space-y-2">
                            <div className="flex flex-wrap items-center gap-2">
                              <h3 className="text-sm font-semibold text-foreground">
                                {candidate.displayName}
                              </h3>
                              <span className="rounded-full border border-border px-2 py-0.5 text-xs text-muted-foreground">
                                @{candidate.namespace}
                              </span>
                              <span className="rounded-full border border-border px-2 py-0.5 text-xs text-muted-foreground">
                                {candidate.status}
                              </span>
                              {candidate.visibility ? (
                                <span className="rounded-full border border-border px-2 py-0.5 text-xs text-muted-foreground">
                                  {candidate.visibility}
                                </span>
                              ) : null}
                              {isDisabled ? (
                                <span className="rounded-full border border-border bg-secondary px-2 py-0.5 text-xs text-muted-foreground">
                                  {t('collections.alreadyInCollection')}
                                </span>
                              ) : null}
                            </div>
                            {candidate.summary ? (
                              <p className="text-sm text-muted-foreground line-clamp-2">
                                {candidate.summary}
                              </p>
                            ) : null}
                          </div>
                          <Button
                            type="button"
                            variant={isSelected ? 'default' : 'outline'}
                            disabled={isDisabled}
                            data-testid={`add-collection-skills-select-${candidate.id}`}
                            aria-label={isDisabled ? t('collections.alreadyInCollection') : candidate.displayName}
                            onClick={() => toggleSelection(candidate.id, isDisabled)}
                          >
                            {isDisabled ? t('collections.alreadyInCollection') : candidate.displayName}
                          </Button>
                        </div>
                      </Card>
                    )
                  })}
                </div>
              ) : null}

              {totalPages > 1 ? (
                <Pagination
                  page={page}
                  totalPages={totalPages}
                  onPageChange={setPage}
                />
              ) : null}
            </>
          )}
        </div>

        <DialogFooter className="sm:justify-end sm:space-x-3">
          <Button type="button" variant="outline" onClick={closeDialog}>
            {t('dialog.cancel')}
          </Button>
          <Button
            type="button"
            disabled={selectedCount === 0 || bulkAddMutation.isPending}
            onClick={() => {
              void handleSubmit()
            }}
          >
            {t('collections.addSelected', { count: selectedCount })}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
