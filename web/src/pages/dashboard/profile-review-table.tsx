import { useState } from 'react'
import { Clock3, ShieldAlert } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { toast } from '@/shared/lib/toast'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/shared/ui/dialog'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/ui/table'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/shared/ui/tabs'
import { Textarea } from '@/shared/ui/textarea'
import {
  useApproveProfileReview,
  useProfileReviewList,
  useRejectProfileReview,
} from '@/features/review/use-profile-review-list'
import { EmptyState } from '@/shared/components/empty-state'

type ReviewStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

type ReviewItem = {
  id: number
  userId: string
  username: string
  currentDisplayName: string | null
  requestedDisplayName: string | null
  status: string
  machineResult: string | null
  reviewerId: string | null
  reviewerName: string | null
  reviewComment: string | null
  createdAt: string
  reviewedAt: string | null
}

const PAGE_SIZE = 10

export function ProfileReviewTable() {
  const { t, i18n } = useTranslation()
  const [selectedItem, setSelectedItem] = useState<ReviewItem | null>(null)
  const [approveDialogOpen, setApproveDialogOpen] = useState(false)
  const [rejectDialogOpen, setRejectDialogOpen] = useState(false)
  const [rejectComment, setRejectComment] = useState('')
  const [pages, setPages] = useState<Record<ReviewStatus, number>>({
    PENDING: 0,
    APPROVED: 0,
    REJECTED: 0,
  })
  const [activeStatus, setActiveStatus] = useState<ReviewStatus>('PENDING')

  const pendingQuery = useProfileReviewList('PENDING', pages.PENDING, PAGE_SIZE, activeStatus === 'PENDING')
  const approvedQuery = useProfileReviewList('APPROVED', pages.APPROVED, PAGE_SIZE, activeStatus === 'APPROVED')
  const rejectedQuery = useProfileReviewList('REJECTED', pages.REJECTED, PAGE_SIZE, activeStatus === 'REJECTED')

  const approveMutation = useApproveProfileReview()
  const rejectMutation = useRejectProfileReview()

  const formatDate = (dateString: string) => formatLocalDateTime(dateString, i18n.language)

  function handleApproveClick(item: ReviewItem) {
    setSelectedItem(item)
    setApproveDialogOpen(true)
  }

  function handleRejectClick(item: ReviewItem) {
    setSelectedItem(item)
    setRejectComment('')
    setRejectDialogOpen(true)
  }

  async function confirmApprove() {
    if (!selectedItem) return
    try {
      await approveMutation.mutateAsync(selectedItem.id)
      toast.success(t('profileReview.approveSuccess'))
    } catch {
      toast.error(t('profileReview.approveFailed'))
    } finally {
      setApproveDialogOpen(false)
      setSelectedItem(null)
    }
  }

  async function confirmReject() {
    if (!selectedItem || !rejectComment.trim()) return
    try {
      await rejectMutation.mutateAsync({ id: selectedItem.id, comment: rejectComment.trim() })
      toast.success(t('profileReview.rejectSuccess'))
    } catch {
      toast.error(t('profileReview.rejectFailed'))
    } finally {
      setRejectDialogOpen(false)
      setSelectedItem(null)
      setRejectComment('')
    }
  }

  function changePage(status: ReviewStatus, nextPage: number) {
    setPages((current) => ({
      ...current,
      [status]: nextPage,
    }))
  }

  function renderMachineResult(result: string | null) {
    if (!result || result === 'SKIPPED') return <span className="text-xs text-muted-foreground">-</span>
    if (result === 'PASS') {
      return (
        <span className="inline-flex items-center rounded-full bg-emerald-500/12 px-2.5 py-1 text-xs font-semibold text-emerald-700">
          PASS
        </span>
      )
    }
    if (result === 'FAIL') {
      return (
        <span className="inline-flex items-center rounded-full bg-rose-500/12 px-2.5 py-1 text-xs font-semibold text-rose-700">
          FAIL
        </span>
      )
    }
    return <span className="text-xs text-muted-foreground">{result}</span>
  }

  function renderPendingSummaryCard(count: number) {
    return (
      <div className="rounded-xl border border-border/60 bg-background/80 p-4 shadow-sm">
        <div className="flex items-center justify-between gap-3">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.18em] text-muted-foreground">
              {t('profileReview.tabPending')}
            </p>
            <p className="mt-2 text-3xl font-semibold text-foreground">{count}</p>
          </div>
          <div className="rounded-xl bg-amber-100 p-3 text-amber-700">
            <Clock3 className="h-5 w-5" />
          </div>
        </div>
      </div>
    )
  }

  function renderLoadingState() {
    return (
      <div className="space-y-3">
        {Array.from({ length: 4 }).map((_, index) => (
          <div key={index} className="h-16 animate-shimmer rounded-xl" />
        ))}
      </div>
    )
  }

  function renderPagination(status: ReviewStatus, totalElements: number, totalPages: number) {
    const currentPage = pages[status]

    return (
      <div className="flex flex-col gap-3 border-t border-border/60 px-6 py-4 text-sm text-muted-foreground md:flex-row md:items-center md:justify-between">
        <p>{t('profileReview.pageSummary', { total: totalElements, page: currentPage + 1 })}</p>
        <div className="flex items-center gap-2">
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={currentPage === 0}
            onClick={() => changePage(status, currentPage - 1)}
          >
            {t('profileReview.prevPage')}
          </Button>
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={currentPage >= totalPages - 1}
            onClick={() => changePage(status, currentPage + 1)}
          >
            {t('profileReview.nextPage')}
          </Button>
        </div>
      </div>
    )
  }

  function renderTable(
    status: ReviewStatus,
    items: ReviewItem[] | undefined,
    isLoading: boolean,
    totalElements: number,
    totalPages: number,
  ) {
    if (isLoading) {
      return renderLoadingState()
    }

    if (!items || items.length === 0) {
      return (
        <div className="rounded-xl border border-dashed border-border/70">
          <EmptyState
            title={t('profileReview.empty')}
            description={t('profileReview.queueSubtitle')}
          />
        </div>
      )
    }

    const isPending = status === 'PENDING'

    return (
      <div className="overflow-hidden rounded-xl border border-border/60">
        <Table>
          <TableHeader>
            <TableRow className="bg-muted/35">
              <TableHead className="text-xs uppercase tracking-[0.18em] text-muted-foreground">{t('profileReview.colUser')}</TableHead>
              <TableHead className="text-xs uppercase tracking-[0.18em] text-muted-foreground">{t('profileReview.colCurrentName')}</TableHead>
              <TableHead className="text-xs uppercase tracking-[0.18em] text-muted-foreground">{t('profileReview.colRequestedName')}</TableHead>
              <TableHead className="text-xs uppercase tracking-[0.18em] text-muted-foreground">{t('profileReview.colSubmittedAt')}</TableHead>
              <TableHead className="text-xs uppercase tracking-[0.18em] text-muted-foreground">{t('profileReview.colMachineResult')}</TableHead>
              <TableHead className="text-xs uppercase tracking-[0.18em] text-muted-foreground">
                {isPending ? t('profileReview.colActions') : t('profileReview.colReviewInfo')}
              </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {items.map((item) => (
              <TableRow key={item.id} className="transition-colors hover:bg-muted/20">
                <TableCell className="align-top">
                  <div className="space-y-1">
                    <p className="font-semibold text-foreground">{item.username || item.userId}</p>
                    <p className="text-xs text-muted-foreground">
                      {t('profileReview.userId')}: {item.userId}
                    </p>
                  </div>
                </TableCell>
                <TableCell className="align-top">
                  <span className="text-sm text-muted-foreground">{item.currentDisplayName || '—'}</span>
                </TableCell>
                <TableCell className="align-top">
                  <span className="font-semibold text-foreground">{item.requestedDisplayName || '—'}</span>
                </TableCell>
                <TableCell className="align-top text-sm text-muted-foreground">
                  {formatDate(item.createdAt)}
                </TableCell>
                <TableCell className="align-top">{renderMachineResult(item.machineResult)}</TableCell>
                <TableCell className="align-top">
                  {isPending ? (
                    <div className="flex flex-wrap gap-2">
                      <Button size="sm" className="min-w-[5rem]" onClick={() => handleApproveClick(item)}>
                        {t('profileReview.approve')}
                      </Button>
                      <Button
                        size="sm"
                        variant="outline"
                        className="min-w-[5rem]"
                        onClick={() => handleRejectClick(item)}
                      >
                        {t('profileReview.reject')}
                      </Button>
                    </div>
                  ) : (
                    <div className="space-y-1.5 text-sm">
                      <p className="font-medium text-foreground">
                        {t('profileReview.reviewer')}: {item.reviewerName || item.reviewerId || '—'}
                      </p>
                      <p className="text-muted-foreground">
                        {item.reviewedAt ? formatDate(item.reviewedAt) : '—'}
                      </p>
                      {item.reviewComment ? (
                        <p className="rounded-lg bg-muted/50 px-3 py-2 text-xs text-muted-foreground">
                          {t('profileReview.comment')}: {item.reviewComment}
                        </p>
                      ) : null}
                    </div>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
        {renderPagination(status, totalElements, totalPages)}
      </div>
    )
  }

  const pendingCount = pendingQuery.data?.totalElements ?? 0
  const approvedCount = approvedQuery.data?.totalElements ?? 0
  const rejectedCount = rejectedQuery.data?.totalElements ?? 0

  return (
    <>
      <div className="space-y-6">
        <Card className="glass-strong overflow-hidden border-border/60 shadow-sm hover:shadow-sm">
          <div className="h-1 bg-gradient-to-r from-slate-900 via-slate-700 to-emerald-500" />
          <CardHeader className="pb-4">
            <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
              <div className="space-y-2">
                <div className="inline-flex items-center gap-2 rounded-full bg-secondary/80 px-3 py-1 text-xs font-semibold uppercase tracking-[0.18em] text-secondary-foreground">
                  <ShieldAlert className="h-3.5 w-3.5" />
                  {t('reviews.typeProfile')}
                </div>
                <CardTitle>{t('profileReview.queueTitle')}</CardTitle>
                <CardDescription>{t('profileReview.queueSubtitle')}</CardDescription>
              </div>
              <p className="text-sm text-muted-foreground">
                {t('profileReview.totalItems', { total: pendingCount + approvedCount + rejectedCount })}
              </p>
            </div>
          </CardHeader>
          <CardContent className="space-y-6">
            <div className="max-w-xs">
              {renderPendingSummaryCard(pendingCount)}
            </div>

            <Tabs value={activeStatus} onValueChange={(v) => setActiveStatus(v as ReviewStatus)}>
              <TabsList className="gap-4 rounded-xl border-b-0 bg-muted/70 p-1 shadow-none">
                <TabsTrigger
                  value="PENDING"
                  className="mb-0 rounded-lg border-b-0 px-4 py-2.5 text-sm font-semibold data-[state=active]:bg-card data-[state=active]:text-foreground data-[state=active]:shadow-sm data-[state=inactive]:text-muted-foreground"
                >
                  {t('profileReview.tabPending')} ({pendingCount})
                </TabsTrigger>
                <TabsTrigger
                  value="APPROVED"
                  className="mb-0 rounded-lg border-b-0 px-4 py-2.5 text-sm font-semibold data-[state=active]:bg-card data-[state=active]:text-foreground data-[state=active]:shadow-sm data-[state=inactive]:text-muted-foreground"
                >
                  {t('profileReview.tabApproved')} ({approvedCount})
                </TabsTrigger>
                <TabsTrigger
                  value="REJECTED"
                  className="mb-0 rounded-lg border-b-0 px-4 py-2.5 text-sm font-semibold data-[state=active]:bg-card data-[state=active]:text-foreground data-[state=active]:shadow-sm data-[state=inactive]:text-muted-foreground"
                >
                  {t('profileReview.tabRejected')} ({rejectedCount})
                </TabsTrigger>
              </TabsList>

              <TabsContent value="PENDING" className="mt-6">
                {renderTable(
                  'PENDING',
                  pendingQuery.data?.items,
                  pendingQuery.isLoading,
                  pendingQuery.data?.totalElements ?? 0,
                  pendingQuery.data?.totalPages ?? 0,
                )}
              </TabsContent>
              <TabsContent value="APPROVED" className="mt-6">
                {renderTable(
                  'APPROVED',
                  approvedQuery.data?.items,
                  approvedQuery.isLoading,
                  approvedQuery.data?.totalElements ?? 0,
                  approvedQuery.data?.totalPages ?? 0,
                )}
              </TabsContent>
              <TabsContent value="REJECTED" className="mt-6">
                {renderTable(
                  'REJECTED',
                  rejectedQuery.data?.items,
                  rejectedQuery.isLoading,
                  rejectedQuery.data?.totalElements ?? 0,
                  rejectedQuery.data?.totalPages ?? 0,
                )}
              </TabsContent>
            </Tabs>
          </CardContent>
        </Card>
      </div>

      <Dialog open={approveDialogOpen} onOpenChange={setApproveDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('profileReview.confirmApproveTitle')}</DialogTitle>
            <DialogDescription>
              {t('profileReview.confirmApproveDesc', {
                user: selectedItem?.username || selectedItem?.userId,
                from: selectedItem?.currentDisplayName,
                to: selectedItem?.requestedDisplayName,
              })}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setApproveDialogOpen(false)}>
              {t('dialog.cancel')}
            </Button>
            <Button onClick={confirmApprove} disabled={approveMutation.isPending}>
              {t('dialog.confirm')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={rejectDialogOpen} onOpenChange={setRejectDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('profileReview.confirmRejectTitle')}</DialogTitle>
            <DialogDescription>
              {t('profileReview.confirmRejectDesc', {
                user: selectedItem?.username || selectedItem?.userId,
              })}
            </DialogDescription>
          </DialogHeader>
          <Textarea
            value={rejectComment}
            onChange={(e) => setRejectComment(e.target.value)}
            placeholder={t('profileReview.rejectPlaceholder')}
            rows={4}
          />
          <DialogFooter>
            <Button variant="outline" onClick={() => setRejectDialogOpen(false)}>
              {t('dialog.cancel')}
            </Button>
            <Button
              onClick={confirmReject}
              disabled={rejectMutation.isPending || !rejectComment.trim()}
            >
              {t('dialog.confirm')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}
