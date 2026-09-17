import { useNavigate, useParams, useSearch } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { SuiteBundleOperationDetail } from '@/features/suite/suite-bundle-operation-detail'
import { SuiteWorkspaceHeader } from '@/features/suite/suite-workspace-header'

export function SuitePublishingTaskPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { operationId } = useParams({ from: '/dashboard/suites/publishing/$operationId' })
  const search = useSearch({ from: '/dashboard/suites/publishing/$operationId' })
  const returnsToSuite = Boolean(search.suiteNamespace && search.suiteSlug)

  const goBack = () => {
    if (search.suiteNamespace && search.suiteSlug) {
      navigate({
        to: `/dashboard/suites/${search.suiteNamespace}/${encodeURIComponent(search.suiteSlug)}`,
        search: { version: search.suiteVersion, tab: 'publishing' },
      })
      return
    }
    navigate({ to: '/dashboard/suites' })
  }

  return (
    <div className="space-y-5 animate-fade-up">
      <SuiteWorkspaceHeader
        title={t('suite.bundle.taskDetailTitle')}
        description={t('suite.bundle.taskDetailDescription')}
        eyebrow={t('suite.myTitle')}
        backLabel={t(returnsToSuite ? 'suite.bundle.backToSuite' : 'suite.management.backToSuites')}
        onBack={goBack}
      />
      <SuiteBundleOperationDetail operationId={operationId} />
    </div>
  )
}
