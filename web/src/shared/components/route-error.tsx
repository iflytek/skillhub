import { useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '@/shared/ui/button'

interface RouteErrorProps {
  error: Error
  reset: () => void
}

/**
 * Recoverable route error UI (replaces TanStack default «Something went wrong»).
 */
export function RouteError({ error, reset }: RouteErrorProps) {
  const { t } = useTranslation()

  useEffect(() => {
    console.error('[RouteError]', error)
  }, [error])

  return (
    <div className="flex min-h-[40vh] flex-col items-center justify-center gap-4 px-6 text-center">
      <h1 className="text-xl font-semibold text-foreground">{t('routeError.title')}</h1>
      <p className="max-w-md text-sm text-muted-foreground">{t('routeError.description')}</p>
      <Button type="button" onClick={() => reset()}>
        {t('routeError.reset')}
      </Button>
    </div>
  )
}
