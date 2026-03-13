import { useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { ApiError, getSessionBootstrapRuntimeConfig } from '@/api/client'
import { Button } from '@/shared/ui/button'
import { useSessionBootstrap } from './use-session-bootstrap'

interface SessionBootstrapEntryProps {
  onAuthenticated: () => Promise<void>
}

export function SessionBootstrapEntry({ onAuthenticated }: SessionBootstrapEntryProps) {
  const { t } = useTranslation()
  const config = getSessionBootstrapRuntimeConfig()
  const bootstrapMutation = useSessionBootstrap()
  const attemptedRef = useRef(false)

  useEffect(() => {
    if (!config.enabled || !config.provider || !config.auto || attemptedRef.current) {
      return
    }
    attemptedRef.current = true
    void bootstrapMutation.mutateAsync(config.provider, {
      onSuccess: async () => {
        await onAuthenticated()
      },
      onError: () => {
        // Fallback to normal login options without surfacing a global auth error.
      },
    })
  }, [bootstrapMutation, config.auto, config.enabled, config.provider, onAuthenticated])

  if (!config.enabled || !config.provider) {
    return null
  }

  const manualError = bootstrapMutation.error instanceof ApiError
    && bootstrapMutation.error.status !== 401
    && bootstrapMutation.error.status !== 403
    ? bootstrapMutation.error.message
    : null

  return (
    <div className="rounded-2xl border border-primary/20 bg-primary/5 p-4 space-y-3">
      <div className="space-y-1">
        <p className="text-sm font-medium text-foreground">
          {t('login.enterpriseSsoTitle')}
        </p>
        <p className="text-sm text-muted-foreground">
          {config.auto ? t('login.enterpriseSsoAutoHint') : t('login.enterpriseSsoHint')}
        </p>
      </div>

      <Button
        className="w-full"
        type="button"
        variant="outline"
        disabled={bootstrapMutation.isPending}
        onClick={() => {
          void bootstrapMutation.mutateAsync(config.provider!, {
            onSuccess: async () => {
              await onAuthenticated()
            },
            onError: () => {
              // Keep the page usable for other login methods.
            },
          })
        }}
      >
        {bootstrapMutation.isPending ? t('login.enterpriseSsoSubmitting') : t('login.enterpriseSsoAction')}
      </Button>

      {manualError ? (
        <p className="text-sm text-red-600">{manualError}</p>
      ) : null}
    </div>
  )
}
