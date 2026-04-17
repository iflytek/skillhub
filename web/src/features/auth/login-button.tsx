import { useTranslation } from 'react-i18next'
import { Button } from '@/shared/ui/button'
import { useAuthMethods } from './use-auth-methods'

interface LoginButtonProps {
  returnTo?: string
}

interface ProviderVisual {
  buttonClassName: string
  icon: JSX.Element
}

function getProviderVisual(providerId: string): ProviderVisual {
  if (providerId === 'oauth-google') {
    return {
      buttonClassName: 'border-[#4285F4]/50 hover:border-[#4285F4] hover:bg-[#4285F4]/5',
      icon: (
        <svg className="w-5 h-5 mr-3" viewBox="0 0 24 24" aria-hidden="true">
          <path fill="#4285F4" d="M21.8 12.2c0-.73-.07-1.45-.21-2.15H12v4.07h5.49a4.68 4.68 0 0 1-2.03 3.07v2.56h3.28c1.92-1.77 3.06-4.39 3.06-7.55Z" />
          <path fill="#34A853" d="M12 22c2.75 0 5.07-.91 6.76-2.45l-3.28-2.56c-.91.61-2.07.97-3.48.97-2.67 0-4.93-1.8-5.74-4.22H2.87v2.64A10 10 0 0 0 12 22Z" />
          <path fill="#FBBC05" d="M6.26 13.74a5.97 5.97 0 0 1 0-3.48V7.62H2.87a10 10 0 0 0 0 8.76l3.39-2.64Z" />
          <path fill="#EA4335" d="M12 6.04c1.49 0 2.83.51 3.88 1.52l2.91-2.91C17.07 3.04 14.75 2 12 2a10 10 0 0 0-9.13 5.62l3.39 2.64c.81-2.42 3.07-4.22 5.74-4.22Z" />
        </svg>
      ),
    }
  }

  return {
    buttonClassName: '',
    icon: (
      <svg className="w-5 h-5 mr-3" fill="currentColor" viewBox="0 0 24 24" aria-hidden="true">
        <path d="M10.5 2.5A8 8 0 0 0 2.5 10.5v3A8 8 0 0 0 10.5 21.5h3a8 8 0 0 0 8-8v-3a8 8 0 0 0-8-8h-3Zm0 2h3a6 6 0 0 1 6 6v3a6 6 0 0 1-6 6h-3a6 6 0 0 1-6-6v-3a6 6 0 0 1 6-6Zm1.5 3a1 1 0 0 0-1 1v3.586L9.707 10.793a1 1 0 1 0-1.414 1.414l3 3a1 1 0 0 0 1.414 0l3-3a1 1 0 1 0-1.414-1.414L13 12.086V8.5a1 1 0 0 0-1-1Z" />
      </svg>
    ),
  }
}

/**
 * Renders OAuth login buttons from the auth-method catalog returned by the backend.
 */
export function LoginButton({ returnTo }: LoginButtonProps) {
  const { t } = useTranslation()
  const { data, isLoading } = useAuthMethods(returnTo)

  const providers = (data ?? []).filter((method) => method.methodType === 'OAUTH_REDIRECT')

  if (isLoading) {
    return (
      <div className="space-y-3">
        <Button className="w-full h-12" disabled>
          <div className="w-5 h-5 rounded-full animate-shimmer mr-3" />
          {t('loginButton.loading')}
        </Button>
      </div>
    )
  }

  return (
    <div className="space-y-3">
      {providers.map((provider) => {
        const visual = getProviderVisual(provider.id)
        const hasGoogleContractDrift = provider.id === 'oauth-google' && !provider.actionUrl.includes('/oauth2/authorization/google')

        if (hasGoogleContractDrift) {
          console.error('oauth_google_contract_drift', {
            providerId: provider.id,
            actionUrl: provider.actionUrl,
          })
        }

        return (
          <Button
            key={provider.id}
            className={`w-full h-12 text-base ${visual.buttonClassName}`}
            data-provider-id={provider.id}
            variant="outline"
            onClick={() => {
              window.location.href = provider.actionUrl
            }}
          >
            {visual.icon}
            {hasGoogleContractDrift
              ? t('loginButton.providerUnavailable')
              : t('loginButton.loginWith', { name: provider.displayName })}
          </Button>
        )
      })}
    </div>
  )
}
