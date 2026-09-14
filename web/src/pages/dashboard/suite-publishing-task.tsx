import { useNavigate, useParams } from '@tanstack/react-router'
import { ArrowLeft } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { SuiteBundleOperationDetail } from '@/features/suite/suite-bundle-operation-detail'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'
import { Button } from '@/shared/ui/button'

export function SuitePublishingTaskPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { operationId } = useParams({ from: '/dashboard/suites/publishing/$operationId' })

  return (
    <div className="space-y-8 animate-fade-up">
      <DashboardPageHeader
        title={t('suite.bundle.taskDetailTitle')}
        subtitle={t('suite.bundle.taskDetailDescription')}
        actions={(
          <Button
            variant="outline"
            onClick={() => navigate({ to: '/dashboard/suites', search: { tab: 'publishing' } })}
          >
            <ArrowLeft className="mr-2 h-4 w-4" />{t('suite.bundle.backToTasks')}
          </Button>
        )}
      />
      <SuiteBundleOperationDetail operationId={operationId} />
    </div>
  )
}
