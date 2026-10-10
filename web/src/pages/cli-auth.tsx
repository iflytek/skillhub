import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Card } from '@/shared/ui/card'
import { Button } from '@/shared/ui/button'
import { getAppBaseUrl, getCurrentUser, tokenApi } from '@/api/client'
import type { User } from '@/api/types'
import { ORIGINAL_URL_SEARCH } from '@/app/router'
import { BASE_PATH } from '@/shared/lib/base-path'
import { resolvePublicRegistryUrl } from '@/shared/lib/registry-url'

// Parse the original URL params captured before TanStack Router rewrites
const ORIGINAL_PARAMS = new URLSearchParams(ORIGINAL_URL_SEARCH)

const LOOPBACK_HOSTS = new Set(['localhost', '127.0.0.1', '[::1]', '::1'])

export function resolveLoopbackRedirectUri(uri: string): URL | null {
  try {
    const url = new URL(uri)
    const isLoopbackHttp = url.protocol === 'http:' && LOOPBACK_HOSTS.has(url.hostname.toLowerCase())
    if (!isLoopbackHttp || url.username || url.password) {
      return null
    }
    url.hash = ''
    return url
  } catch {
    return null
  }
}

function decodeLabel(labelB64?: string, labelPlain?: string): string {
  if (labelB64) {
    try {
      // Base64-URL decode
      const base64 = labelB64.replace(/-/g, '+').replace(/_/g, '/')
      return atob(base64)
    } catch {
      // Fallback to plain label
    }
  }
  return labelPlain || 'CLI token'
}

export function resolveCliRegistryUrl(appBaseUrl: string | undefined, origin: string, basePath = BASE_PATH): string {
  return resolvePublicRegistryUrl(appBaseUrl, origin, basePath)
}

export function CliAuthPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()

  const [user, setUser] = useState<User | null | undefined>(undefined)
  const [status, setStatus] = useState<'validating' | 'creating' | 'redirecting' | 'error'>('validating')
  const [errorMessage, setErrorMessage] = useState<string>('')
  const [token, setToken] = useState<string>('')

  // Use the captured original params from module load time
  const redirectUri = ORIGINAL_PARAMS.get('redirect_uri')?.trim() || undefined
  const state = ORIGINAL_PARAMS.get('state')?.trim() || undefined
  const labelB64 = ORIGINAL_PARAMS.get('label_b64')?.trim() || undefined
  const labelPlain = ORIGINAL_PARAMS.get('label')?.trim() || undefined
  const label = decodeLabel(labelB64, labelPlain)
  const redirectTarget = useMemo(
    () => redirectUri ? resolveLoopbackRedirectUri(redirectUri) : null,
    [redirectUri],
  )

  useEffect(() => {
    // Check authentication status
    getCurrentUser()
      .then((currentUser) => {
        setUser(currentUser)
      })
      .catch(() => {
        setUser(null)
      })
  }, [])

  useEffect(() => {
    // Once we know the user status, proceed with token creation
    if (user === undefined) {
      // Still loading
      return
    }

    if (user === null) {
      // Not authenticated - user needs to log in
      setStatus('error')
      setErrorMessage(t('cliAuth.notAuthenticated'))
      return
    }

    // Validate redirect_uri
    if (!redirectTarget) {
      setStatus('error')
      setErrorMessage(t('cliAuth.invalidRedirectUri'))
      return
    }

    // Validate state
    if (!state) {
      setStatus('error')
      // Special error message for Windows users with missing state
      if (redirectUri && typeof window !== 'undefined' && navigator.platform.includes('Win')) {
        setErrorMessage(t('cliAuth.windowsUrlBug'))
      } else {
        setErrorMessage(t('cliAuth.missingState'))
      }
      return
    }

    // Create token and redirect
    setStatus('creating')
    tokenApi
      .createToken({
        name: label,
        scopes: ['skill:read', 'skill:publish'],
      })
      .then((response) => {
        setToken(response.token)
        setStatus('redirecting')

        // Construct redirect URL with token in hash fragment
        const registryUrl = resolveCliRegistryUrl(getAppBaseUrl(), window.location.origin)
        const hashParams = new URLSearchParams()
        hashParams.set('token', response.token)
        hashParams.set('registry', registryUrl)
        hashParams.set('state', state)

        const redirectUrl = new URL(redirectTarget.href)
        redirectUrl.hash = hashParams.toString()

        // Redirect to CLI's loopback server
        window.location.assign(redirectUrl.href)
      })
      .catch((error) => {
        setStatus('error')
        setErrorMessage(error instanceof Error ? error.message : t('cliAuth.tokenCreationFailed'))
      })
  }, [user, redirectUri, redirectTarget, state, label, t])

  if (status === 'validating') {
    return (
      <div className="min-h-[70vh] flex items-center justify-center p-4">
        <Card className="w-full max-w-md p-8 space-y-6 text-center">
          <div className="inline-flex w-16 h-16 rounded-2xl bg-gradient-to-br from-primary to-accent items-center justify-center shadow-glow mb-2 mx-auto">
            <svg className="w-8 h-8 text-primary-foreground animate-spin" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
          </div>
          <h1 className="text-2xl font-bold font-heading">{t('cliAuth.validating')}</h1>
          <p className="text-muted-foreground">{t('cliAuth.pleaseWait')}</p>
        </Card>
      </div>
    )
  }

  if (status === 'creating') {
    return (
      <div className="min-h-[70vh] flex items-center justify-center p-4">
        <Card className="w-full max-w-md p-8 space-y-6 text-center">
          <div className="inline-flex w-16 h-16 rounded-2xl bg-gradient-to-br from-primary to-accent items-center justify-center shadow-glow mb-2 mx-auto">
            <svg className="w-8 h-8 text-primary-foreground animate-spin" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
          </div>
          <h1 className="text-2xl font-bold font-heading">{t('cliAuth.creatingToken')}</h1>
          <p className="text-muted-foreground">{t('cliAuth.almostThere')}</p>
        </Card>
      </div>
    )
  }

  if (status === 'redirecting') {
    return (
      <div className="min-h-[70vh] flex items-center justify-center p-4">
        <Card className="w-full max-w-md p-8 space-y-6 text-center">
          <div className="inline-flex w-16 h-16 rounded-2xl bg-gradient-to-br from-emerald-500 to-emerald-600 items-center justify-center shadow-glow mb-2 mx-auto">
            <svg className="w-8 h-8 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
            </svg>
          </div>
          <h1 className="text-2xl font-bold font-heading">{t('cliAuth.success')}</h1>
          <p className="text-muted-foreground">{t('cliAuth.redirecting')}</p>

          {token && (
            <div className="mt-6 p-4 bg-muted rounded-lg">
              <p className="text-sm text-muted-foreground mb-2">{t('cliAuth.fallbackInstructions')}</p>
              <code className="block p-3 bg-background rounded text-xs font-mono break-all">
                {token}
              </code>
            </div>
          )}
        </Card>
      </div>
    )
  }

  // Error state
  return (
    <div className="min-h-[70vh] flex items-center justify-center p-4 animate-fade-up">
      <Card className="w-full max-w-md p-8 space-y-6">
        <div className="text-center space-y-3">
          <div className="inline-flex w-16 h-16 rounded-2xl bg-gradient-to-br from-red-500 to-red-600 items-center justify-center shadow-glow mb-2 mx-auto">
            <svg className="w-8 h-8 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
            </svg>
          </div>
          <h1 className="text-2xl font-bold font-heading">{t('cliAuth.error')}</h1>
          <p className="text-muted-foreground">{errorMessage}</p>
        </div>

        {user === null && (
          <div className="space-y-4">
            <p className="text-sm text-center text-muted-foreground">
              {t('cliAuth.loginRequired')}
            </p>
            <Button
              className="w-full"
              onClick={() => {
                const returnTo = `/cli/auth?${ORIGINAL_PARAMS.toString()}`
                navigate({ to: '/login', search: { returnTo } })
              }}
            >
              {t('cliAuth.goToLogin')}
            </Button>
          </div>
        )}
      </Card>
    </div>
  )
}
