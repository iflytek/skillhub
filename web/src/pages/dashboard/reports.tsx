import { useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'
import { Card } from '@/shared/ui/card'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/shared/ui/tabs'
import { Button } from '@/shared/ui/button'
import { ConfirmDialog } from '@/shared/components/confirm-dialog'
import { useDismissSkillReport, useResolveSkillReport, useSkillReports } from '@/features/report/use-skill-reports'
import { toast } from '@/shared/lib/toast'

export function ReportsPage() {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const [pendingAction, setPendingAction] = useState<{ id: number; action: 'resolve' | 'dismiss'; skillLabel: string } | null>(null)
  const { data: pendingReports, isLoading: isPendingLoading } = useSkillReports('PENDING')
  const { data: resolvedReports, isLoading: isResolvedLoading } = useSkillReports('RESOLVED')
  const { data: dismissedReports, isLoading: isDismissedLoading } = useSkillReports('DISMISSED')
  const resolveMutation = useResolveSkillReport()
  const dismissMutation = useDismissSkillReport()

  const formatDate = (dateString: string) => new Date(dateString).toLocaleString(i18n.language)

  const handleOpenSkill = (namespace?: string, skillSlug?: string) => {
    if (!namespace || !skillSlug) {
      return
    }
    navigate({ to: `/space/${namespace}/${skillSlug}` })
  }

  const handleConfirm = async () => {
    if (!pendingAction) {
      return
    }
    try {
      if (pendingAction.action === 'resolve') {
        await resolveMutation.mutateAsync({ id: pendingAction.id })
        toast.success(t('reports.resolveSuccessTitle'), t('reports.resolveSuccessDescription', { skill: pendingAction.skillLabel }))
      } else {
        await dismissMutation.mutateAsync({ id: pendingAction.id })
        toast.success(t('reports.dismissSuccessTitle'), t('reports.dismissSuccessDescription', { skill: pendingAction.skillLabel }))
      }
      setPendingAction(null)
    } catch (error) {
      toast.error(
        pendingAction.action === 'resolve' ? t('reports.resolveErrorTitle') : t('reports.dismissErrorTitle'),
        error instanceof Error ? error.message : '',
      )
    }
  }

  const renderList = (reports: typeof pendingReports, isLoading: boolean, status: 'PENDING' | 'RESOLVED' | 'DISMISSED') => {
    if (isLoading) {
      return (
        <div className="space-y-3">
          {Array.from({ length: 3 }).map((_, index) => (
            <div key={index} className="h-24 animate-shimmer rounded-lg" />
          ))}
        </div>
      )
    }

    if (!reports || reports.length === 0) {
      return <Card className="p-12 text-center text-muted-foreground">{t('reports.empty')}</Card>
    }

    return (
      <div className="space-y-4">
        {reports.map((report) => {
          const skillLabel = report.skillDisplayName || report.skillSlug || `#${report.skillId}`
          return (
            <Card key={report.id} className="p-5 space-y-4">
              <div className="flex items-start justify-between gap-4">
                <div className="space-y-2 min-w-0">
                  <button
                    type="button"
                    className="text-left font-semibold font-heading text-foreground hover:text-primary transition-colors"
                    onClick={() => handleOpenSkill(report.namespace, report.skillSlug)}
                  >
                    {report.namespace && report.skillSlug ? `${report.namespace}/${report.skillSlug}` : skillLabel}
                  </button>
                  <div className="text-sm text-muted-foreground">{skillLabel}</div>
                  <div className="text-sm text-foreground">{report.reason}</div>
                  {report.details ? <div className="text-sm text-muted-foreground whitespace-pre-wrap">{report.details}</div> : null}
                </div>
                <div className="text-right text-xs text-muted-foreground space-y-1 shrink-0">
                  <div>{t('reports.reporter')}: {report.reporterId}</div>
                  <div>{formatDate(report.createdAt)}</div>
                  {report.handledAt ? <div>{formatDate(report.handledAt)}</div> : null}
                </div>
              </div>

              {status === 'PENDING' ? (
                <div className="flex items-center justify-end gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={resolveMutation.isPending || dismissMutation.isPending}
                    onClick={() => setPendingAction({ id: report.id, action: 'dismiss', skillLabel })}
                  >
                    {t('reports.dismiss')}
                  </Button>
                  <Button
                    size="sm"
                    disabled={resolveMutation.isPending || dismissMutation.isPending}
                    onClick={() => setPendingAction({ id: report.id, action: 'resolve', skillLabel })}
                  >
                    {t('reports.resolve')}
                  </Button>
                </div>
              ) : (
                <div className="text-sm text-muted-foreground">
                  {t('reports.handledBy')}: {report.handledBy || '—'}
                </div>
              )}
            </Card>
          )
        })}
      </div>
    )
  }

  return (
    <div className="space-y-8 animate-fade-up">
      <DashboardPageHeader title={t('reports.title')} subtitle={t('reports.subtitle')} />

      <Tabs defaultValue="PENDING">
        <TabsList>
          <TabsTrigger value="PENDING">{t('reports.tabPending')}</TabsTrigger>
          <TabsTrigger value="RESOLVED">{t('reports.tabResolved')}</TabsTrigger>
          <TabsTrigger value="DISMISSED">{t('reports.tabDismissed')}</TabsTrigger>
        </TabsList>

        <TabsContent value="PENDING" className="mt-6">
          {renderList(pendingReports, isPendingLoading, 'PENDING')}
        </TabsContent>
        <TabsContent value="RESOLVED" className="mt-6">
          {renderList(resolvedReports, isResolvedLoading, 'RESOLVED')}
        </TabsContent>
        <TabsContent value="DISMISSED" className="mt-6">
          {renderList(dismissedReports, isDismissedLoading, 'DISMISSED')}
        </TabsContent>
      </Tabs>

      <ConfirmDialog
        open={pendingAction !== null}
        onOpenChange={(open) => {
          if (!open) {
            setPendingAction(null)
          }
        }}
        title={pendingAction?.action === 'resolve' ? t('reports.resolveConfirmTitle') : t('reports.dismissConfirmTitle')}
        description={pendingAction?.action === 'resolve'
          ? t('reports.resolveConfirmDescription', { skill: pendingAction?.skillLabel ?? '' })
          : t('reports.dismissConfirmDescription', { skill: pendingAction?.skillLabel ?? '' })}
        confirmText={pendingAction?.action === 'resolve' ? t('reports.resolve') : t('reports.dismiss')}
        onConfirm={handleConfirm}
      />
    </div>
  )
}
