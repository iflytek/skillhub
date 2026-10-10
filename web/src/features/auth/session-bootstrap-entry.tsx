import { useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { ApiError, getSessionBootstrapRuntimeConfig } from '@/api/client'
import { Button } from '@/shared/ui/button'
import { useSessionBootstrap } from './use-session-bootstrap'

interface SessionBootstrapEntryProps {
  onAuthenticated: () => Promise<void>
  methodDisplayName?: string
  compact?: boolean
}

/**
 * Optional login entry that attempts to bootstrap a browser session from an upstream enterprise
 * identity before showing manual login choices.
 */
export function SessionBootstrapEntry({ onAuthenticated, methodDisplayName, compact = false }: SessionBootstrapEntryProps) {
  const { t } = useTranslation()
  const config = getSessionBootstrapRuntimeConfig()
  const bootstrapMutation = useSessionBootstrap()
  const attemptedRef = useRef(false)
  const providerName = methodDisplayName || t('login.enterpriseSsoTitle')

  useEffect(() => {
    // Auto-bootstrap should only be attempted once per page load; if it fails the page must remain
    // usable for normal login options.
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

  if (compact) {
    return (
      <div className="space-y-3">
        <Button
          className="h-11 w-full rounded-xl bg-[#315b86] text-white hover:bg-[#25496d] dark:bg-sky-300 dark:text-sky-950 dark:hover:bg-sky-200 xl:h-10"
          type="button"
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
          {bootstrapMutation.isPending
            ? t('login.enterpriseSsoSubmitting', { name: providerName })
            : t('login.enterpriseContinue')}
        </Button>

        {manualError ? (
          <p className="text-sm text-red-600">{manualError}</p>
        ) : null}
      </div>
    )
  }

  return (
    <div className="rounded-2xl border border-primary/20 bg-primary/5 p-4 space-y-3">
      <div className="space-y-1">
        <p className="text-sm font-medium text-foreground">
          {providerName}
        </p>
        <p className="text-sm text-muted-foreground">
          {config.auto
            ? t('login.enterpriseSsoAutoHint', { name: providerName })
            : t('login.enterpriseSsoHint', { name: providerName })}
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
        {bootstrapMutation.isPending
          ? t('login.enterpriseSsoSubmitting', { name: providerName })
          : t('login.enterpriseSsoAction', { name: providerName })}
      </Button>

      {manualError ? (
        <p className="text-sm text-red-600">{manualError}</p>
      ) : null}
    </div>
  )
}
