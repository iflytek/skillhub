import { useNavigate } from '@tanstack/react-router'
import { FileCheck2 } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/shared/ui/tabs'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/ui/table'
import { useReviewList } from '@/features/review/use-review-list'
import { useAuth } from '@/features/auth/use-auth'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { ProfileReviewTable } from './profile-review-table'

/**
 * Dashboard review queue page. Each tab materializes one review status because
 * the moderation workflow treats pending, approved, and rejected queues as
 * distinct operator views rather than one filterable table.
 */
export function ReviewsPage() {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const { hasRole } = useAuth()

  const isSkillAdmin = hasRole('SKILL_ADMIN') || hasRole('SUPER_ADMIN')
  const isUserAdmin = hasRole('USER_ADMIN') || hasRole('SUPER_ADMIN')
  const showTypeTabs = isSkillAdmin && isUserAdmin

  // Determine default top-level tab
  const defaultType = isSkillAdmin ? 'skill' : 'profile'

  const { data: pendingReviews, isLoading: isPendingLoading } = useReviewList('PENDING')
  const { data: approvedReviews, isLoading: isApprovedLoading } = useReviewList('APPROVED')
  const { data: rejectedReviews, isLoading: isRejectedLoading } = useReviewList('REJECTED')

  const formatDate = (dateString: string) => formatLocalDateTime(dateString, i18n.language)

  const handleRowClick = (reviewId: number) => {
    navigate({ to: `/dashboard/reviews/${reviewId}` })
  }

  /**
   * Keeps the table rendering logic in one local helper so the per-status tabs
   * stay declarative while still allowing columns to differ by workflow state.
   */
  const renderReviewTable = (reviews: typeof pendingReviews, isLoading: boolean, status: string) => {
    if (isLoading) {
      return (
        <div className="space-y-3">
          {Array.from({ length: 4 }).map((_, index) => (
            <div key={index} className="h-16 animate-shimmer rounded-xl" />
          ))}
        </div>
      )
    }
    if (!reviews || reviews.length === 0) {
      return <div className="rounded-xl border border-dashed border-border/70 p-10 text-center text-muted-foreground">{t('reviews.empty')}</div>
    }
    return (
      <div className="overflow-hidden rounded-xl border border-border/60">
        <Table>
          <TableHeader>
            <TableRow className="bg-muted/35">
              <TableHead className="text-xs uppercase tracking-[0.18em] text-muted-foreground">{t('reviews.colSkill')}</TableHead>
              <TableHead className="text-xs uppercase tracking-[0.18em] text-muted-foreground">{t('reviews.colVersion')}</TableHead>
              <TableHead className="text-xs uppercase tracking-[0.18em] text-muted-foreground">{t('reviews.colSubmitter')}</TableHead>
              {status === 'PENDING' ? (
                <TableHead className="text-xs uppercase tracking-[0.18em] text-muted-foreground">{t('reviews.colSubmitTime')}</TableHead>
              ) : (
                <>
                  <TableHead className="text-xs uppercase tracking-[0.18em] text-muted-foreground">{t('reviews.colReviewer')}</TableHead>
                  <TableHead className="text-xs uppercase tracking-[0.18em] text-muted-foreground">{t('reviews.colReviewTime')}</TableHead>
                </>
              )}
            </TableRow>
          </TableHeader>
          <TableBody>
            {reviews.map((review) => (
              <TableRow
                key={review.id}
                className="cursor-pointer transition-colors hover:bg-muted/30"
                onClick={() => handleRowClick(review.id)}
              >
                <TableCell className="font-medium">{review.namespace}/{review.skillSlug}</TableCell>
                <TableCell>{review.version}</TableCell>
                <TableCell>{review.submittedByName || review.submittedBy}</TableCell>
                {status === 'PENDING' ? (
                  <TableCell>{formatDate(review.submittedAt)}</TableCell>
                ) : (
                  <>
                    <TableCell>{review.reviewedByName || review.reviewedBy || '-'}</TableCell>
                    <TableCell>{review.reviewedAt ? formatDate(review.reviewedAt) : '-'}</TableCell>
                  </>
                )}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
    )
  }

  function renderSkillReviewContent() {
    return (
      <Card className="glass-strong overflow-hidden border-border/60 shadow-sm hover:shadow-sm">
        <div className="h-1 bg-gradient-to-r from-slate-900 via-blue-700 to-sky-500" />
        <CardHeader className="pb-4">
          <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
            <div className="space-y-2">
              <div className="inline-flex items-center gap-2 rounded-full bg-secondary/80 px-3 py-1 text-xs font-semibold uppercase tracking-[0.18em] text-secondary-foreground">
                <FileCheck2 className="h-3.5 w-3.5" />
                {t('reviews.typeSkill')}
              </div>
              <CardTitle>{t('reviews.typeSkill')}</CardTitle>
              <CardDescription>{t('reviews.subtitle')}</CardDescription>
            </div>
          </div>
        </CardHeader>
        <CardContent className="space-y-6">
          <Tabs defaultValue="PENDING">
            <TabsList className="gap-4 rounded-xl border-b-0 bg-muted/70 p-1 shadow-none">
              <TabsTrigger
                value="PENDING"
                className="mb-0 rounded-lg border-b-0 px-4 py-2.5 data-[state=active]:bg-card data-[state=active]:text-foreground data-[state=active]:shadow-sm data-[state=inactive]:text-muted-foreground"
              >
                {t('reviews.tabPending')}
              </TabsTrigger>
              <TabsTrigger
                value="APPROVED"
                className="mb-0 rounded-lg border-b-0 px-4 py-2.5 data-[state=active]:bg-card data-[state=active]:text-foreground data-[state=active]:shadow-sm data-[state=inactive]:text-muted-foreground"
              >
                {t('reviews.tabApproved')}
              </TabsTrigger>
              <TabsTrigger
                value="REJECTED"
                className="mb-0 rounded-lg border-b-0 px-4 py-2.5 data-[state=active]:bg-card data-[state=active]:text-foreground data-[state=active]:shadow-sm data-[state=inactive]:text-muted-foreground"
              >
                {t('reviews.tabRejected')}
              </TabsTrigger>
            </TabsList>
            <TabsContent value="PENDING" className="mt-6">
              {renderReviewTable(pendingReviews, isPendingLoading, 'PENDING')}
            </TabsContent>
            <TabsContent value="APPROVED" className="mt-6">
              {renderReviewTable(approvedReviews, isApprovedLoading, 'APPROVED')}
            </TabsContent>
            <TabsContent value="REJECTED" className="mt-6">
              {renderReviewTable(rejectedReviews, isRejectedLoading, 'REJECTED')}
            </TabsContent>
          </Tabs>
        </CardContent>
      </Card>
    )
  }

  return (
    <div className="space-y-8 animate-fade-up">
      <DashboardPageHeader title={t('reviews.title')} subtitle={t('reviews.subtitle')} />

      {showTypeTabs ? (
        <Tabs defaultValue={defaultType}>
          <TabsList className="gap-2 rounded-2xl border-b-0 bg-muted/80 p-1 shadow-sm">
            <TabsTrigger
              value="skill"
              className="mb-0 rounded-xl border-b-0 px-5 py-3 text-base font-semibold data-[state=active]:bg-card data-[state=active]:text-foreground data-[state=active]:shadow-sm data-[state=inactive]:text-muted-foreground"
            >
              {t('reviews.typeSkill')}
            </TabsTrigger>
            <TabsTrigger
              value="profile"
              className="mb-0 rounded-xl border-b-0 px-5 py-3 text-base font-semibold data-[state=active]:bg-card data-[state=active]:text-foreground data-[state=active]:shadow-sm data-[state=inactive]:text-muted-foreground"
            >
              {t('reviews.typeProfile')}
            </TabsTrigger>
          </TabsList>
          <TabsContent value="skill" className="mt-6">
            {renderSkillReviewContent()}
          </TabsContent>
          <TabsContent value="profile" className="mt-6">
            <ProfileReviewTable />
          </TabsContent>
        </Tabs>
      ) : isSkillAdmin ? (
        renderSkillReviewContent()
      ) : isUserAdmin ? (
        <ProfileReviewTable />
      ) : null}
    </div>
  )
}
