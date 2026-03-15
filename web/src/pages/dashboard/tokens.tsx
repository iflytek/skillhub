import { useTranslation } from 'react-i18next'
import { TokenList } from '@/features/token/token-list'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'

export function TokensPage() {
  const { t } = useTranslation()
  return (
    <div className="space-y-8 animate-fade-up">
      <DashboardPageHeader
        title={t('tokens.pageTitle')}
        subtitle={t('tokens.pageSubtitle')}
      />
      <TokenList />
    </div>
  )
}
