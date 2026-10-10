import { useTranslation } from 'react-i18next'
import type React from 'react'
import { Button } from '@/shared/ui/button'
import { withBasePath } from '@/shared/lib/base-path'
import { cn } from '@/shared/lib/utils'
import type { AuthMethod } from '@/api/types'
import { useAuthMethods } from './use-auth-methods'

interface LoginButtonProps {
  returnTo?: string
  compact?: boolean
  emptyFallback?: React.ReactNode
}

/**
 * Returns the appropriate icon for a given OAuth provider.
 */
function OAuthIcon({ provider, compact = false }: { provider: string, compact?: boolean }) {
  const normalizedProvider = provider.toLowerCase()
  return (
    <img
      src={withBasePath(`/${normalizedProvider}-logo.svg`)}
      alt={provider}
      className={cn(compact ? 'h-4 w-4' : 'h-5 w-5 mr-3')}
    />
  )
}

interface AuthMethodButtonListProps {
  methods?: AuthMethod[]
  isLoading?: boolean
  compact?: boolean
  emptyFallback?: React.ReactNode
}

/**
 * Renders OAuth redirect entries advertised by the backend.
 */
export function AuthMethodButtonList({
  methods,
  isLoading = false,
  compact = false,
  emptyFallback = null,
}: AuthMethodButtonListProps) {
  const { t } = useTranslation()
  const providers = (methods ?? []).filter((method) => method.methodType === 'OAUTH_REDIRECT')

  if (isLoading) {
    return (
      <div className={cn(compact ? 'grid grid-cols-2 gap-2 sm:grid-cols-4' : 'space-y-3')}>
        <Button className="h-12" disabled variant="outline">
          <div className={cn('rounded-full animate-shimmer', compact ? 'h-4 w-4' : 'mr-3 h-5 w-5')} />
          {compact ? t('loginButton.loadingShort') : t('loginButton.loading')}
        </Button>
      </div>
    )
  }

  if (providers.length === 0) {
    return <>{emptyFallback}</>
  }

  return (
    <div className={cn(compact ? 'grid grid-cols-2 gap-2 sm:grid-cols-4' : 'space-y-3')}>
      {providers.map((provider) => (
        <Button
          key={provider.id}
          className={cn(
            compact ? 'h-11 justify-center px-2 text-sm xl:h-10' : 'w-full h-12 text-base',
          )}
          variant="outline"
          onClick={() => {
            window.location.href = withBasePath(provider.actionUrl)
          }}
        >
          <OAuthIcon provider={provider.provider} compact={compact} />
          <span className={compact ? 'truncate' : undefined}>
            {compact ? provider.displayName : t('loginButton.loginWith', { name: provider.displayName })}
          </span>
        </Button>
      ))}
    </div>
  )
}

/**
 * Renders OAuth login buttons from the auth-method catalog returned by the backend.
 */
export function LoginButton({ returnTo, compact = false, emptyFallback }: LoginButtonProps) {
  const { data, isLoading } = useAuthMethods(returnTo)
  return (
    <AuthMethodButtonList
      methods={data}
      isLoading={isLoading}
      compact={compact}
      emptyFallback={emptyFallback}
    />
  )
}
