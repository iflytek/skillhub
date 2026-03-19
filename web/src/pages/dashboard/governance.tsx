import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@/features/auth/use-auth'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'
import { ConfirmDialog } from '@/shared/components/confirm-dialog'
import { Pagination } from '@/shared/components/pagination'
import { toast } from '@/shared/lib/toast'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/shared/ui/tabs'
import { GovernanceInbox } from '@/features/governance/governance-inbox'
import { GovernanceActivity } from '@/features/governance/governance-activity'
import { GovernanceNotifications } from '@/features/governance/governance-notifications'
import { getGovernanceTotalPages } from '@/features/governance/governance-pagination'
import {
  GOVERNANCE_PAGE_SIZE,
  useGovernanceActivity,
  useGovernanceInbox,
  useGovernanceNotifications,
  useRebuildSearchIndex,
  useGovernanceSummary,
  useMarkGovernanceNotificationRead,
} from '@/features/governance/use-governance'

type GovernanceInboxTab = 'ALL' | 'REVIEW' | 'PROMOTION' | 'REPORT'

/**
 * Dashboard page that aggregates governance summary counts, inbox queues, notifications, and
 * recent moderation activity.
 */
function SummaryCard({ label, value }: { label: string; value?: number }) {
  return (
    <Card className="p-5">
      <div className="text-sm text-muted-foreground">{label}</div>
      <div className="mt-3 text-3xl font-bold font-heading">{value ?? 0}</div>
    </Card>
  )
}

export function GovernancePage() {
  const { t } = useTranslation()
  const { hasRole } = useAuth()
  const [inboxType, setInboxType] = useState<GovernanceInboxTab>('ALL')
  const [inboxPage, setInboxPage] = useState(0)
  const [activityPage, setActivityPage] = useState(0)
  const [notificationsPage, setNotificationsPage] = useState(0)
  const [rebuildDialogOpen, setRebuildDialogOpen] = useState(false)
  const { data: summary, isLoading: isSummaryLoading } = useGovernanceSummary()
  const { data: inboxPageData, isLoading: isInboxLoading } = useGovernanceInbox(
    inboxType === 'ALL' ? undefined : inboxType,
    inboxPage,
    GOVERNANCE_PAGE_SIZE,
  )
  const { data: activityPageData, isLoading: isActivityLoading } = useGovernanceActivity(activityPage, GOVERNANCE_PAGE_SIZE)
  const { data: notificationsPageData, isLoading: isNotificationsLoading } = useGovernanceNotifications(
    notificationsPage,
    GOVERNANCE_PAGE_SIZE,
  )
  const markReadMutation = useMarkGovernanceNotificationRead()
  const rebuildSearchIndexMutation = useRebuildSearchIndex()

  const canRebuildSearchIndex = hasRole('SUPER_ADMIN')
  const inboxTotalPages = getGovernanceTotalPages(inboxPageData?.total ?? 0, inboxPageData?.size ?? GOVERNANCE_PAGE_SIZE)
  const activityTotalPages = getGovernanceTotalPages(activityPageData?.total ?? 0, activityPageData?.size ?? GOVERNANCE_PAGE_SIZE)
  const notificationsTotalPages = getGovernanceTotalPages(notificationsPageData?.total ?? 0, notificationsPageData?.size ?? GOVERNANCE_PAGE_SIZE)

  const handleRebuildSearchIndex = async () => {
    try {
      await rebuildSearchIndexMutation.mutateAsync()
      toast.success(t('governance.searchRebuildSuccessTitle'), t('governance.searchRebuildSuccessDescription'))
    } catch (error) {
      toast.error(t('governance.searchRebuildErrorTitle'), error instanceof Error ? error.message : '')
      throw error
    }
  }

  return (
    <div className="space-y-8 animate-fade-up">
      <DashboardPageHeader title={t('governance.title')} subtitle={t('governance.subtitle')} />

      <div className="grid grid-cols-1 gap-4 md:grid-cols-4">
        <SummaryCard label={t('governance.pendingReviews')} value={isSummaryLoading ? undefined : summary?.pendingReviews} />
        <SummaryCard label={t('governance.pendingPromotions')} value={isSummaryLoading ? undefined : summary?.pendingPromotions} />
        <SummaryCard label={t('governance.pendingReports')} value={isSummaryLoading ? undefined : summary?.pendingReports} />
        <SummaryCard label={t('governance.unreadNotifications')} value={isSummaryLoading ? undefined : summary?.unreadNotifications} />
      </div>

      <Card className="p-5 space-y-5">
        <div>
          <h2 className="text-xl font-semibold font-heading">{t('governance.inboxTitle')}</h2>
          <p className="text-sm text-muted-foreground">{t('governance.inboxSubtitle')}</p>
        </div>

        <Tabs
          defaultValue="ALL"
          onValueChange={(value) => {
            setInboxType(value as GovernanceInboxTab)
            setInboxPage(0)
          }}
        >
          <TabsList>
            <TabsTrigger value="ALL">{t('governance.tabAll')}</TabsTrigger>
            <TabsTrigger value="REVIEW">{t('governance.tabReview')}</TabsTrigger>
            <TabsTrigger value="PROMOTION">{t('governance.tabPromotion')}</TabsTrigger>
            <TabsTrigger value="REPORT">{t('governance.tabReport')}</TabsTrigger>
          </TabsList>
          <TabsContent value="ALL" className="mt-6">
            <GovernanceInbox items={inboxPageData?.items} isLoading={isInboxLoading} />
            {inboxTotalPages > 1 ? <Pagination page={inboxPage} totalPages={inboxTotalPages} onPageChange={setInboxPage} /> : null}
          </TabsContent>
          <TabsContent value="REVIEW" className="mt-6">
            <GovernanceInbox items={inboxPageData?.items} isLoading={isInboxLoading} />
            {inboxTotalPages > 1 ? <Pagination page={inboxPage} totalPages={inboxTotalPages} onPageChange={setInboxPage} /> : null}
          </TabsContent>
          <TabsContent value="PROMOTION" className="mt-6">
            <GovernanceInbox items={inboxPageData?.items} isLoading={isInboxLoading} />
            {inboxTotalPages > 1 ? <Pagination page={inboxPage} totalPages={inboxTotalPages} onPageChange={setInboxPage} /> : null}
          </TabsContent>
          <TabsContent value="REPORT" className="mt-6">
            <GovernanceInbox items={inboxPageData?.items} isLoading={isInboxLoading} />
            {inboxTotalPages > 1 ? <Pagination page={inboxPage} totalPages={inboxTotalPages} onPageChange={setInboxPage} /> : null}
          </TabsContent>
        </Tabs>
      </Card>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <Card className="p-5 space-y-5">
          <div>
            <h2 className="text-xl font-semibold font-heading">{t('governance.notificationsTitle')}</h2>
            <p className="text-sm text-muted-foreground">{t('governance.notificationsSubtitle')}</p>
          </div>
          <GovernanceNotifications
            items={notificationsPageData?.items}
            isLoading={isNotificationsLoading}
            onMarkRead={(id) => markReadMutation.mutate(id)}
            isMarkingRead={markReadMutation.isPending}
          />
          {notificationsTotalPages > 1 ? (
            <Pagination page={notificationsPage} totalPages={notificationsTotalPages} onPageChange={setNotificationsPage} />
          ) : null}
        </Card>

        <Card className="p-5 space-y-5">
          <div>
            <h2 className="text-xl font-semibold font-heading">{t('governance.activityTitle')}</h2>
            <p className="text-sm text-muted-foreground">{t('governance.activitySubtitle')}</p>
          </div>
          <GovernanceActivity items={activityPageData?.items} isLoading={isActivityLoading} />
          {activityTotalPages > 1 ? <Pagination page={activityPage} totalPages={activityTotalPages} onPageChange={setActivityPage} /> : null}
        </Card>
      </div>

      {canRebuildSearchIndex ? (
        <>
          <Card className="p-5 space-y-4 border border-amber-500/20 bg-amber-500/5">
            <div className="space-y-1">
              <h2 className="text-xl font-semibold font-heading">{t('governance.searchMaintenanceTitle')}</h2>
              <p className="text-sm text-muted-foreground">{t('governance.searchMaintenanceDescription')}</p>
            </div>
            <div className="flex flex-wrap items-center gap-3">
              <Button
                type="button"
                variant="outline"
                onClick={() => setRebuildDialogOpen(true)}
                disabled={rebuildSearchIndexMutation.isPending}
              >
                {rebuildSearchIndexMutation.isPending ? t('governance.searchRebuildRunning') : t('governance.searchRebuildAction')}
              </Button>
              <span className="text-xs text-muted-foreground">{t('governance.searchMaintenanceHint')}</span>
            </div>
          </Card>

          <ConfirmDialog
            open={rebuildDialogOpen}
            onOpenChange={setRebuildDialogOpen}
            title={t('governance.searchRebuildConfirmTitle')}
            description={t('governance.searchRebuildConfirmDescription')}
            confirmText={t('governance.searchRebuildAction')}
            onConfirm={handleRebuildSearchIndex}
          />
        </>
      ) : null}
    </div>
  )
}
