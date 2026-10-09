import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useApprovePromotion, usePromotionList, useRejectPromotion } from '@/features/promotion/use-promotion-list'
import { useAdminPromotionRevocationHistory, useApprovePromotionRevocation, usePendingPromotionRevocations, useRejectPromotionRevocation } from '@/features/promotion/use-promotion-revocations'
import { ConfirmDialog } from '@/shared/components/confirm-dialog'
import { toast } from '@/shared/lib/toast'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'
import { Pagination } from '@/shared/components/pagination'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { formatCompactCount } from '@/shared/lib/number-format'
import { cn } from '@/shared/lib/utils'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/ui/table'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/shared/ui/tabs'
import type { PagedResponse, PromotionTask } from '@/api/types'
import type { PromotionSortDirection, PromotionStatus } from '@/features/promotion/use-promotion-list'

type HistoryPromotionStatus = Extract<PromotionStatus, 'APPROVED' | 'REJECTED'>
type PromotionPage = PagedResponse<PromotionTask>

const PAGE_SIZE = 20

function formatFileSize(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`
  }
  const units = ['KB', 'MB', 'GB']
  let value = bytes / 1024
  let unitIndex = 0
  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024
    unitIndex += 1
  }
  return `${value.toFixed(value >= 10 ? 0 : 1)} ${units[unitIndex]}`
}

function formatUserName(displayName: string | null | undefined, userId: string | null | undefined, fallback: string) {
  return displayName || userId || fallback
}

function sourceCoordinate(item: PromotionTask) {
  return `@${item.sourceNamespace}/${item.sourceSkillSlug}`
}

function promotionCoordinate(item: PromotionTask) {
  return `${sourceCoordinate(item)} -> @${item.targetNamespace}`
}

function SorterGlyph({ direction }: { direction: PromotionSortDirection }) {
  return (
    <span aria-hidden="true" className="flex h-4 w-3 flex-col items-center justify-center gap-0.5">
      <span
        className={cn(
          'h-0 w-0 border-x-[4px] border-b-[5px] border-x-transparent',
          direction === 'ASC' ? 'border-b-foreground' : 'border-b-muted-foreground/45'
        )}
      />
      <span
        className={cn(
          'h-0 w-0 border-x-[4px] border-t-[5px] border-x-transparent',
          direction === 'DESC' ? 'border-t-foreground' : 'border-t-muted-foreground/45'
        )}
      />
    </span>
  )
}

function PromotionPagination({ data, onPageChange }: { data: PromotionPage; onPageChange: (page: number) => void }) {
  const totalPages = data.size > 0 ? Math.ceil(data.total / data.size) : 0
  if (totalPages <= 1) {
    return null
  }
  return <Pagination page={data.page} totalPages={totalPages} onPageChange={onPageChange} />
}

function useClampPromotionPage(data: PromotionPage | undefined, page: number, onPageChange: (page: number) => void) {
  useEffect(() => {
    if (!data) {
      return
    }
    const totalPages = data.size > 0 ? Math.ceil(data.total / data.size) : 0
    if (page > 0 && page >= totalPages) {
      onPageChange(Math.max(0, totalPages - 1))
    }
  }, [data, onPageChange, page])
}

function PendingPromotionCard({
  item,
  comment,
  isMutating,
  onCommentChange,
  onApprove,
  onReject,
}: {
  item: PromotionTask
  comment: string
  isMutating: boolean
  onCommentChange: (value: string) => void
  onApprove: () => void
  onReject: () => void
}) {
  const { t, i18n } = useTranslation()
  const submitter = formatUserName(item.submittedByName, item.submittedBy, t('promotions.emptyValue'))
  return (
    <Card className="space-y-4 p-5">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
        <div className="min-w-0 space-y-1">
          <span className="text-xs font-semibold text-primary">{t(item.requestKind === 'UPDATE' ? 'promotions.kindUpdate' : 'promotions.kindInitial')}</span>
          <h3 className="break-words font-heading text-base font-semibold text-foreground">{item.sourceSkillDisplayName}</h3>
          <p className="break-words text-sm text-muted-foreground [overflow-wrap:anywhere]">{promotionCoordinate(item)}</p>
        </div>
        <div className="shrink-0 text-sm text-muted-foreground">{formatLocalDateTime(item.submittedAt, i18n.language)}</div>
      </div>
      {item.sourceSkillSummary ? (
        <p className="text-sm leading-6 text-muted-foreground break-words [overflow-wrap:anywhere] line-clamp-2">
          {item.sourceSkillSummary}
        </p>
      ) : null}
      <div className="grid gap-2 text-sm text-muted-foreground sm:grid-cols-2 lg:grid-cols-3">
        <span>{t('promotions.versionTag', { version: item.sourceVersion })}</span>
        {item.requestKind === 'UPDATE' && (
          <span>{t('promotions.targetVersionTag', { version: item.targetCurrentVersion ?? t('promotions.emptyValue') })}</span>
        )}
        <span>{t('promotions.submitterTag', { user: submitter })}</span>
        <span>{t('promotions.fileCountTag', { count: item.sourceVersionFileCount })}</span>
        <span>{t('promotions.packageSizeTag', { size: formatFileSize(item.sourceVersionTotalSize) })}</span>
        <span>{t('promotions.downloadCountTag', { value: formatCompactCount(item.sourceSkillDownloadCount) })}</span>
        <span>{t('promotions.starCountTag', { value: formatCompactCount(item.sourceSkillStarCount) })}</span>
      </div>
      {item.requestKind === 'UPDATE' && (
        <p className="rounded-lg bg-muted/40 px-3 py-2 text-sm text-muted-foreground">
          {t('promotions.updateReviewHint')}
        </p>
      )}
      <Input
        placeholder={t('promotions.commentPlaceholder')}
        value={comment}
        onChange={(event) => onCommentChange(event.target.value)}
      />
      <div className="flex flex-wrap gap-3">
        <Button onClick={onApprove} disabled={isMutating}>
          {t('promotions.approve')}
        </Button>
        <Button variant="destructive" onClick={onReject} disabled={isMutating}>
          {t('promotions.reject')}
        </Button>
      </div>
    </Card>
  )
}

function PendingPromotionList({ page, onPageChange }: { page: number; onPageChange: (page: number) => void }) {
  const { t } = useTranslation()
  const { data, isLoading } = usePromotionList({ status: 'PENDING', page, size: PAGE_SIZE })
  const approveMutation = useApprovePromotion()
  const rejectMutation = useRejectPromotion()
  const [commentById, setCommentById] = useState<Record<number, string>>({})
  useClampPromotionPage(data, page, onPageChange)

  if (isLoading) {
    return <div className="h-32 animate-shimmer rounded-xl" />
  }

  if (!data || data.items.length === 0) {
    return <div className="rounded-xl border border-dashed border-border/70 p-10 text-center text-muted-foreground">{t('promotions.empty')}</div>
  }

  const isMutating = approveMutation.isPending || rejectMutation.isPending
  return (
    <div className="space-y-4">
      {data.items.map((item) => (
        <PendingPromotionCard
          key={item.id}
          item={item}
          comment={commentById[item.id] ?? ''}
          isMutating={isMutating}
          onCommentChange={(value) => setCommentById((prev) => ({ ...prev, [item.id]: value }))}
          onApprove={() => approveMutation.mutate({ id: item.id, comment: commentById[item.id] })}
          onReject={() => rejectMutation.mutate({ id: item.id, comment: commentById[item.id] })}
        />
      ))}
      <PromotionPagination data={data} onPageChange={onPageChange} />
    </div>
  )
}

function PendingRevocationList() {
  const { t, i18n } = useTranslation()
  const { data, isLoading, error } = usePendingPromotionRevocations()
  const approveMutation = useApprovePromotionRevocation()
  const rejectMutation = useRejectPromotionRevocation()
  const [commentById, setCommentById] = useState<Record<number, string>>({})
  const [approveId, setApproveId] = useState<number | null>(null)
  const target = data?.find((item) => item.id === approveId)

  if (isLoading) return <div className="h-32 animate-shimmer rounded-xl" />
  if (error) return <p className="text-sm text-destructive">{t('promotions.revocationLoadError')}</p>
  if (!data?.length) return <div className="rounded-xl border border-dashed border-border/70 p-10 text-center text-muted-foreground">{t('promotions.revocationEmpty')}</div>

  const handleApprove = async () => {
    if (!target) return
    try {
      await approveMutation.mutateAsync({ id: target.id, comment: commentById[target.id] ?? '' })
      setApproveId(null)
      toast.success(t('promotions.revocationApproved'))
    } catch (failure) {
      toast.error(t('promotions.revocationActionError'), failure instanceof Error ? failure.message : '')
    }
  }

  const handleReject = async (id: number) => {
    try {
      await rejectMutation.mutateAsync({ id, comment: commentById[id] ?? '' })
      toast.success(t('promotions.revocationRejected'))
    } catch (failure) {
      toast.error(t('promotions.revocationActionError'), failure instanceof Error ? failure.message : '')
    }
  }

  return (
    <div className="space-y-4">
      {data.map((item) => (
        <Card key={item.id} className="space-y-3 p-5">
          <div className="flex flex-wrap items-start justify-between gap-2">
            <div>
              <p className="font-semibold text-foreground">@global/{item.skillSlug}</p>
              <p className="text-sm text-muted-foreground">{t('promotions.revocationIds', { source: item.sourceSkillId, target: item.targetSkillId })}</p>
            </div>
            <span className="text-sm text-muted-foreground">{formatLocalDateTime(item.submittedAt, i18n.language)}</span>
          </div>
          <p className="text-sm text-muted-foreground">{t('promotions.submitterTag', { user: item.submittedBy })}</p>
          {item.reason && <p className="text-sm text-muted-foreground">{t('promotions.revocationReason', { reason: item.reason })}</p>}
          <Input
            aria-label={t('promotions.commentPlaceholder')}
            placeholder={t('promotions.commentPlaceholder')}
            value={commentById[item.id] ?? ''}
            onChange={(event) => setCommentById((previous) => ({ ...previous, [item.id]: event.target.value }))}
          />
          <div className="flex flex-wrap gap-3">
            <Button variant="destructive" onClick={() => setApproveId(item.id)} disabled={approveMutation.isPending || rejectMutation.isPending}>
              {t('promotions.approveRevocation')}
            </Button>
            <Button variant="outline" onClick={() => handleReject(item.id)} disabled={approveMutation.isPending || rejectMutation.isPending}>
              {t('promotions.rejectRevocation')}
            </Button>
          </div>
        </Card>
      ))}
      <ConfirmDialog
        open={approveId !== null}
        onOpenChange={(open) => { if (!open) setApproveId(null) }}
        title={t('promotions.revocationConfirmTitle')}
        description={t('promotions.revocationConfirmDescription', { slug: target?.skillSlug ?? '' })}
        confirmText={t('promotions.approveRevocation')}
        variant="destructive"
        onConfirm={handleApprove}
      />
    </div>
  )
}

function RevocationHistoryList() {
  const { t, i18n } = useTranslation()
  const [page, setPage] = useState(0)
  const { data, isLoading, error } = useAdminPromotionRevocationHistory(page, PAGE_SIZE)

  if (isLoading) return <div className="h-32 animate-shimmer rounded-xl" />
  if (error) return <p className="text-sm text-destructive">{t('promotions.revocationLoadError')}</p>
  if (!data?.items.length) return <div className="rounded-xl border border-dashed border-border/70 p-10 text-center text-muted-foreground">{t('promotions.revocationHistoryEmpty')}</div>

  const totalPages = data.size > 0 ? Math.ceil(data.total / data.size) : 0
  return (
    <div className="space-y-4">
      {data.items.map((item) => (
        <Card key={item.id} className="space-y-2 p-5 text-sm">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <p className="font-semibold text-foreground">@global/{item.skillSlug}</p>
            <span className="text-muted-foreground">{item.reviewedAt ? formatLocalDateTime(item.reviewedAt, i18n.language) : t('promotions.emptyValue')}</span>
          </div>
          <p className="text-muted-foreground">{t(`promotions.revocationStatus.${item.status}`)} · {t('promotions.revocationIds', { source: item.sourceSkillId, target: item.targetSkillId })}</p>
          <p className="text-muted-foreground">{t('promotions.revocationActors', { submitter: item.submittedBy, reviewer: item.reviewedBy ?? t('promotions.emptyValue') })}</p>
          {item.reason && <p className="break-words text-muted-foreground">{t('promotions.revocationReason', { reason: item.reason })}</p>}
          {item.reviewComment && <p className="break-words text-muted-foreground">{t('promotions.revocationReviewComment', { comment: item.reviewComment })}</p>}
        </Card>
      ))}
      {totalPages > 1 && <Pagination page={data.page} totalPages={totalPages} onPageChange={setPage} />}
    </div>
  )
}

function PromotionHistoryTable({
  status,
  sortDirection,
  page,
  onPageChange,
  onToggleSort,
}: {
  status: HistoryPromotionStatus
  sortDirection: PromotionSortDirection
  page: number
  onPageChange: (page: number) => void
  onToggleSort: () => void
}) {
  const { t, i18n } = useTranslation()
  const { data, isLoading } = usePromotionList({
    status,
    page,
    size: PAGE_SIZE,
    sortBy: 'reviewedAt',
    sortDirection,
  })
  const nextDirection = sortDirection === 'DESC' ? 'ASC' : 'DESC'
  const sortLabel = nextDirection === 'ASC' ? t('promotions.sortReviewedTimeAsc') : t('promotions.sortReviewedTimeDesc')
  useClampPromotionPage(data, page, onPageChange)

  if (isLoading) {
    return (
      <div className="space-y-3">
        {Array.from({ length: 4 }).map((_, index) => (
          <div key={index} className="h-16 animate-shimmer rounded-xl" />
        ))}
      </div>
    )
  }

  if (!data || data.items.length === 0) {
    return <div className="rounded-xl border border-dashed border-border/70 p-10 text-center text-muted-foreground">{t('promotions.empty')}</div>
  }

  return (
    <div className="space-y-4">
      <div className="overflow-hidden rounded-xl border border-border/60">
        <Table aria-label={t('promotions.historyTableLabel')}>
        <TableHeader>
          <TableRow className="bg-muted/35">
            <TableHead className="text-xs uppercase tracking-[0.18em] text-muted-foreground">{t('promotions.colSkill')}</TableHead>
            <TableHead className="text-xs uppercase tracking-[0.18em] text-muted-foreground">{t('promotions.colVersion')}</TableHead>
            <TableHead className="text-xs uppercase tracking-[0.18em] text-muted-foreground">{t('promotions.colSubmitter')}</TableHead>
            <TableHead className="text-xs uppercase tracking-[0.18em] text-muted-foreground">{t('promotions.colReviewer')}</TableHead>
            <TableHead aria-sort={sortDirection === 'DESC' ? 'descending' : 'ascending'} className="text-xs uppercase tracking-[0.18em] text-muted-foreground">
              <Button
                type="button"
                variant="ghost"
                size="sm"
                className="h-8 gap-2 px-0 text-xs uppercase tracking-[0.18em] text-muted-foreground hover:bg-transparent hover:text-foreground"
                aria-label={sortLabel}
                onClick={onToggleSort}
              >
                {t('promotions.colReviewedAt')}
                <SorterGlyph direction={sortDirection} />
              </Button>
            </TableHead>
            <TableHead className="text-xs uppercase tracking-[0.18em] text-muted-foreground">{t('promotions.colReviewComment')}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {data.items.map((item) => {
            const reviewCommentId = `promotion-review-comment-${item.id}`
            return (
              <TableRow key={item.id}>
                <TableCell>
                  <div className="min-w-0">
                    <div className="break-words font-medium text-foreground">{item.sourceSkillDisplayName}</div>
                    <div className="text-xs text-primary">{t(item.requestKind === 'UPDATE' ? 'promotions.kindUpdate' : 'promotions.kindInitial')}</div>
                    <div className="break-words text-xs text-muted-foreground [overflow-wrap:anywhere]">{sourceCoordinate(item)}</div>
                  </div>
                </TableCell>
                <TableCell>{t('promotions.versionTag', { version: item.sourceVersion })}</TableCell>
                <TableCell>{formatUserName(item.submittedByName, item.submittedBy, t('promotions.emptyValue'))}</TableCell>
                <TableCell>{formatUserName(item.reviewedByName, item.reviewedBy, t('promotions.emptyValue'))}</TableCell>
                <TableCell>{item.reviewedAt ? formatLocalDateTime(item.reviewedAt, i18n.language) : t('promotions.emptyValue')}</TableCell>
                <TableCell className="max-w-[18rem]">
                  {item.reviewComment ? (
                    <>
                      <p id={`${reviewCommentId}-visible`} aria-describedby={reviewCommentId} className="line-clamp-2 break-words text-sm text-muted-foreground [overflow-wrap:anywhere]">
                        {item.reviewComment}
                      </p>
                      <span id={reviewCommentId} className="sr-only">{item.reviewComment}</span>
                    </>
                  ) : (
                    t('promotions.emptyValue')
                  )}
                </TableCell>
              </TableRow>
            )
          })}
        </TableBody>
        </Table>
      </div>
      <PromotionPagination data={data} onPageChange={onPageChange} />
    </div>
  )
}

/**
 * Dashboard page for namespace promotion requests.
 */
export function PromotionsPage() {
  const { t } = useTranslation()
  const [pages, setPages] = useState<Record<PromotionStatus, number>>({
    PENDING: 0,
    APPROVED: 0,
    REJECTED: 0,
  })
  const [historySortDirection, setHistorySortDirection] = useState<Record<HistoryPromotionStatus, PromotionSortDirection>>({
    APPROVED: 'DESC',
    REJECTED: 'DESC',
  })

  function toggleHistorySort(status: HistoryPromotionStatus) {
    setHistorySortDirection((current) => ({
      ...current,
      [status]: current[status] === 'DESC' ? 'ASC' : 'DESC',
    }))
  }

  function changePage(status: PromotionStatus, page: number) {
    setPages((current) => ({ ...current, [status]: page }))
  }

  return (
    <div className="space-y-8 animate-fade-up">
      <DashboardPageHeader title={t('promotions.title')} subtitle={t('promotions.subtitle')} />
      <Tabs defaultValue="PENDING">
        <TabsList>
          <TabsTrigger value="PENDING">{t('promotions.tabPending')}</TabsTrigger>
          <TabsTrigger value="REVOCATIONS">{t('promotions.tabRevocations')}</TabsTrigger>
          <TabsTrigger value="REVOCATION_HISTORY">{t('promotions.tabRevocationHistory')}</TabsTrigger>
          <TabsTrigger value="APPROVED">{t('promotions.tabApproved')}</TabsTrigger>
          <TabsTrigger value="REJECTED">{t('promotions.tabRejected')}</TabsTrigger>
        </TabsList>
        <TabsContent value="PENDING" className="mt-6">
          <PendingPromotionList page={pages.PENDING} onPageChange={(page) => changePage('PENDING', page)} />
        </TabsContent>
        <TabsContent value="REVOCATIONS" className="mt-6">
          <PendingRevocationList />
        </TabsContent>
        <TabsContent value="REVOCATION_HISTORY" className="mt-6">
          <RevocationHistoryList />
        </TabsContent>
        <TabsContent value="APPROVED" className="mt-6">
          <PromotionHistoryTable
            status="APPROVED"
            sortDirection={historySortDirection.APPROVED}
            page={pages.APPROVED}
            onPageChange={(page) => changePage('APPROVED', page)}
            onToggleSort={() => toggleHistorySort('APPROVED')}
          />
        </TabsContent>
        <TabsContent value="REJECTED" className="mt-6">
          <PromotionHistoryTable
            status="REJECTED"
            sortDirection={historySortDirection.REJECTED}
            page={pages.REJECTED}
            onPageChange={(page) => changePage('REJECTED', page)}
            onToggleSort={() => toggleHistorySort('REJECTED')}
          />
        </TabsContent>
      </Tabs>
    </div>
  )
}
