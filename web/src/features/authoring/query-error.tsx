import { useTranslation } from 'react-i18next'
import { ApiError } from '@/shared/lib/api-error'
import { Button } from '@/shared/ui/button'

export function QueryError({ error, retry }: { error: unknown, retry: () => void }) {
  const { t } = useTranslation()
  const forbidden = error instanceof ApiError && (error.status === 401 || error.status === 403)
  return (
    <div role="alert" className="space-y-2 text-sm text-destructive">
      <p>{t(forbidden ? 'apiError.forbidden' : 'authoring.ui.loadFailed')}</p>
      <Button variant="outline" size="sm" onClick={retry}>{t('authoring.ui.retry')}</Button>
    </div>
  )
}
