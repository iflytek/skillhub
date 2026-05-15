import { useTranslation } from 'react-i18next'
import { getSsoRuntimeConfig } from '@/api/client'
import { Button } from '@/shared/ui/button'

/**
 * Optional login entry that redirects the browser to the enterprise SSO login
 * page, which follows the CAS protocol to authenticate and redirect back.
 */
export function SsoLoginEntry() {
  const { t } = useTranslation()
  const config = getSsoRuntimeConfig()

  if (!config.enabled) {
    return null
  }

  return (
    <Button
      className="w-full"
      type="button"
      variant="outline"
      onClick={() => {
        const searchParams = new URLSearchParams(window.location.search)
        const returnTo = searchParams.get('returnTo')
        const loginUrl = returnTo
          ? '/api/v1/auth/sso/login?returnTo=' + encodeURIComponent(returnTo)
          : '/api/v1/auth/sso/login'
        window.location.href = loginUrl
      }}
    >
      {t('login.ssoLogin')}
    </Button>
  )
}
