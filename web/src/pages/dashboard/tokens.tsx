import { useTranslation } from 'react-i18next'
import { TokenList } from '@/features/token/token-list'

export function TokensPage() {
  const { t } = useTranslation()
  return (
    <div className="space-y-8 animate-fade-up">
      <div>
        <h1 className="text-4xl font-bold font-heading mb-2">{t('tokens.pageTitle')}</h1>
        <p className="text-muted-foreground text-lg">{t('tokens.pageSubtitle')}</p>
      </div>
      <TokenList />
    </div>
  )
}
