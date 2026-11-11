import { useState } from 'react'
import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'
import { Pagination } from '@/shared/components/pagination'
import { Button } from '@/shared/ui/button'
import { GovernanceInbox } from '@/features/governance/governance-inbox'
import { GovernanceNotifications } from '@/features/governance/governance-notifications'
import { toast } from '@/shared/lib/toast'
import { GovernanceActivity } from '@/features/governance/governance-activity'
import { getGovernanceTotalPages } from '@/features/governance/governance-pagination'
import { GOVERNANCE_PAGE_SIZE, useGovernanceActivity, useGovernanceInbox, useGovernanceNotifications, useMarkGovernanceNotificationRead } from '@/features/governance/use-governance'

/** Governance handles reports and promotions; review requests have one dedicated queue. */
export function GovernancePage() {
  const { t } = useTranslation()
  const [reportPage, setReportPage] = useState(0)
  const [promotionPage, setPromotionPage] = useState(0)
  const [activityPage, setActivityPage] = useState(0)
  const [notificationPage, setNotificationPage] = useState(0)
  const notifications = useGovernanceNotifications(notificationPage, GOVERNANCE_PAGE_SIZE)
  const markRead = useMarkGovernanceNotificationRead()
  const markNotificationRead = (id: number) => markRead.mutate(id, { onError: (error) => toast.error(t('reviewCenter.notificationReadFailed'), error instanceof Error ? error.message : '') })
  const reports = useGovernanceInbox('REPORT', reportPage, GOVERNANCE_PAGE_SIZE)
  const promotions = useGovernanceInbox('PROMOTION', promotionPage, GOVERNANCE_PAGE_SIZE)
  const activity = useGovernanceActivity(activityPage, GOVERNANCE_PAGE_SIZE)
  const section = (kind: 'REPORT' | 'PROMOTION', query: typeof reports, page: number, setPage: (page: number) => void) => {
    const totalPages = getGovernanceTotalPages(query.data?.total ?? 0, query.data?.size ?? GOVERNANCE_PAGE_SIZE)
    return <section className="space-y-4" aria-label={t(kind === 'REPORT' ? 'reviewCenter.governancePendingReports' : 'reviewCenter.governancePendingPromotions')}>
      <div className="flex flex-wrap items-center justify-between gap-3"><h2 className="text-xl font-semibold">{t(kind === 'REPORT' ? 'reviewCenter.governancePendingReports' : 'reviewCenter.governancePendingPromotions')}</h2><Link to={kind === 'REPORT' ? '/dashboard/reports' : '/dashboard/promotions'} className="text-sm font-medium underline-offset-4 hover:underline">{t('reviewCenter.viewAll')}</Link></div>
      {query.isError ? <div role="alert" className="space-y-3 rounded-lg border p-5"><p className="text-sm text-destructive">{t('reviewCenter.loadFailed')}</p><Button variant="outline" onClick={() => void query.refetch()}>{t('reviewCenter.retry')}</Button></div> : <GovernanceInbox items={query.data?.items} isLoading={query.isLoading} />}
      {totalPages > 1 && <Pagination analyticsId={kind === 'REPORT' ? 'governance.report.paginate' : 'governance.promotion.paginate'} page={page} totalPages={totalPages} onPageChange={setPage} />}
    </section>
  }
  return <div className="space-y-9">
    <DashboardPageHeader title={t('reviewCenter.governanceTitle')} subtitle={t('reviewCenter.governanceDescription')} actions={<Link to="/dashboard/reviews" className="inline-flex min-h-9 items-center rounded-lg border border-border/60 px-3 text-sm hover:bg-secondary">{t('reviewCenter.governanceReviewLink')}</Link>} />
    {section('REPORT', reports, reportPage, setReportPage)}
    {section('PROMOTION', promotions, promotionPage, setPromotionPage)}
    <details className="rounded-lg border border-border p-5">
      <summary className="cursor-pointer text-lg font-semibold">{t('reviewCenter.governanceNotifications')}</summary>
      <div className="mt-5 space-y-4">
        {notifications.isError ? <div role="alert" className="space-y-3"><p className="text-sm text-destructive">{t('reviewCenter.loadFailed')}</p><Button variant="outline" onClick={() => void notifications.refetch()}>{t('reviewCenter.retry')}</Button></div> : <GovernanceNotifications items={notifications.data?.items} isLoading={notifications.isLoading} onMarkRead={markNotificationRead} isMarkingRead={markRead.isPending} />}
        {getGovernanceTotalPages(notifications.data?.total ?? 0, notifications.data?.size ?? GOVERNANCE_PAGE_SIZE) > 1 && <Pagination analyticsId="governance.notification.paginate" page={notificationPage} totalPages={getGovernanceTotalPages(notifications.data?.total ?? 0, notifications.data?.size ?? GOVERNANCE_PAGE_SIZE)} onPageChange={setNotificationPage} />}
      </div>
    </details>
    <section className="space-y-4 border-t border-border pt-7"><h2 className="text-xl font-semibold">{t('reviewCenter.governanceActivity')}</h2>
      {activity.isError ? <div role="alert" className="space-y-3"><p className="text-sm text-destructive">{t('reviewCenter.loadFailed')}</p><Button variant="outline" onClick={() => void activity.refetch()}>{t('reviewCenter.retry')}</Button></div> : <GovernanceActivity items={activity.data?.items} isLoading={activity.isLoading} />}
      {getGovernanceTotalPages(activity.data?.total ?? 0, activity.data?.size ?? GOVERNANCE_PAGE_SIZE) > 1 && <Pagination analyticsId="governance.activity.paginate" page={activityPage} totalPages={getGovernanceTotalPages(activity.data?.total ?? 0, activity.data?.size ?? GOVERNANCE_PAGE_SIZE)} onPageChange={setActivityPage} />}
    </section>
  </div>
}
