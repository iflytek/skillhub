import { Link, useNavigate, useSearch } from '@tanstack/react-router'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ArrowRight, Eye, EyeOff, LockKeyhole, UserRound } from 'lucide-react'
import { getSessionBootstrapRuntimeConfig } from '@/api/client'
import { AuthShell } from '@/features/auth/auth-shell'
import { AuthMethodButtonList } from '@/features/auth/login-button'
import { SessionBootstrapEntry } from '@/features/auth/session-bootstrap-entry'
import { useAuthMethods } from '@/features/auth/use-auth-methods'
import { useLocalAuthCapabilities } from '@/features/auth/use-local-auth-capabilities'
import { usePasswordLogin } from '@/features/auth/use-password-login'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { resolveAuthReturnTo } from '@/shared/lib/auth-route'

/**
 * Authentication entry page.
 *
 * It combines password login, OAuth entry points, and optional session-bootstrap support while
 * preserving the route the user originally intended to visit.
 */
export function LoginPage() {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const search = useSearch({ from: '/login' })
  const loginMutation = usePasswordLogin()
  const bootstrapConfig = getSessionBootstrapRuntimeConfig()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [loginMode, setLoginMode] = useState<'personal' | 'organization'>('personal')
  const [fieldErrors, setFieldErrors] = useState<{ username?: string, password?: string }>({})
  const isChinese = i18n.resolvedLanguage?.split('-')[0] === 'zh'
  const returnTo = resolveAuthReturnTo(search.returnTo)
  const { data: authMethods, isLoading: authMethodsLoading } = useAuthMethods(returnTo)
  const { data: localCapabilities, isError: localCapabilitiesError } = useLocalAuthCapabilities()
  const passwordEnabled = localCapabilities?.passwordLoginEnabled === true
  const effectiveLoginMode = passwordEnabled ? loginMode : 'organization'
  const disabledMessage = search.reason === 'accountDisabled' ? t('apiError.auth.accountDisabled') : null
  const bootstrapMethod = authMethods?.find((method) => method.methodType === 'SESSION_BOOTSTRAP')
  const hasOrganizationMethod = bootstrapConfig.enabled
  const hasExternalMethods = authMethods?.some((method) => method.methodType === 'OAUTH_REDIRECT')

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const trimmedUsername = username.trim()
    const nextFieldErrors: { username?: string, password?: string } = {}

    if (!trimmedUsername) {
      nextFieldErrors.username = t('login.usernameRequired')
    }
    if (!password) {
      nextFieldErrors.password = t('login.passwordRequired')
    }
    if (nextFieldErrors.username || nextFieldErrors.password) {
      setFieldErrors(nextFieldErrors)
      return
    }

    setFieldErrors({})
    try {
      await loginMutation.mutateAsync({ username: trimmedUsername, password })
      await navigate({ to: returnTo })
    } catch {
      // mutation state drives the error UI
    }
  }

  return (
    <AuthShell>
      <div className="flex flex-col gap-5 animate-fade-up xl:min-h-[calc(100svh-12rem)]">
        <div className="space-y-2">
          <p className="text-sm font-medium text-muted-foreground">{t('login.eyebrow')}</p>
          <h1 className="text-3xl font-bold tracking-tight text-foreground sm:text-4xl">{t('login.title')}</h1>
          <p className="text-sm text-muted-foreground">
            {t('login.subtitle')}
          </p>
        </div>

        {disabledMessage ? (
          <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
            {disabledMessage}
          </div>
        ) : null}

        {hasOrganizationMethod && passwordEnabled ? (
          <div role="group" aria-label={t('login.loginMode')} className="grid grid-cols-2 rounded-lg border border-slate-200 bg-slate-50 p-1 dark:border-slate-700 dark:bg-slate-900">
            <button type="button" aria-pressed={loginMode === 'personal'} onClick={() => setLoginMode('personal')} className={`h-11 rounded-md text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-sky-500 xl:h-10 ${loginMode === 'personal' ? 'bg-sky-100 text-sky-900 dark:bg-sky-900 dark:text-sky-100' : 'text-muted-foreground hover:text-foreground'}`}>
              {t('login.tabPersonal')}
            </button>
            <button type="button" aria-pressed={loginMode === 'organization'} onClick={() => setLoginMode('organization')} className={`h-11 rounded-md text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-sky-500 xl:h-10 ${loginMode === 'organization' ? 'bg-sky-100 text-sky-900 dark:bg-sky-900 dark:text-sky-100' : 'text-muted-foreground hover:text-foreground'}`}>
              {t('login.tabEnterprise')}
            </button>
          </div>
        ) : null}

        {passwordEnabled ? <div hidden={effectiveLoginMode !== 'personal'}>
        <form className="space-y-4" onSubmit={handleSubmit}>
              <div className="space-y-2">
                <label className="text-sm font-medium" htmlFor="username">{t('login.username')}</label>
                <div className="relative">
                  <UserRound aria-hidden="true" className="absolute left-3 top-1/2 h-5 w-5 -translate-y-1/2 text-muted-foreground" />
                  <Input
                    id="username"
                    autoComplete="username"
                    value={username}
                    onChange={(event) => {
                      setUsername(event.target.value)
                      if (fieldErrors.username) {
                        setFieldErrors((current) => ({ ...current, username: undefined }))
                      }
                    }}
                    placeholder={t('login.usernamePlaceholder')}
                    aria-invalid={fieldErrors.username ? 'true' : 'false'}
                    className="h-11 pl-10 xl:h-10"
                  />
                </div>
                {fieldErrors.username ? (
                  <p className="text-sm text-red-600">{fieldErrors.username}</p>
                ) : null}
              </div>
              <div className="space-y-2">
                <div className="flex items-center justify-between gap-3">
                  <label className="text-sm font-medium" htmlFor="password">{t('login.password')}</label>
                  <Link to="/reset-password" className="text-sm font-medium text-sky-700 hover:underline dark:text-sky-300">
                    {t('login.forgotPassword')}
                  </Link>
                </div>
                <div className="relative">
                  <LockKeyhole aria-hidden="true" className="absolute left-3 top-1/2 h-5 w-5 -translate-y-1/2 text-muted-foreground" />
                  <Input
                    id="password"
                    type={showPassword ? 'text' : 'password'}
                    autoComplete="current-password"
                    value={password}
                    onChange={(event) => {
                      setPassword(event.target.value)
                      if (fieldErrors.password) {
                        setFieldErrors((current) => ({ ...current, password: undefined }))
                      }
                    }}
                    placeholder={t('login.passwordPlaceholder')}
                    className="h-11 pl-10 pr-12 xl:h-10"
                    aria-invalid={fieldErrors.password ? 'true' : 'false'}
                  />
                  <button
                    type="button"
                    aria-label={showPassword ? t('login.hidePassword') : t('login.showPassword')}
                    aria-pressed={showPassword}
                    onClick={() => setShowPassword((current) => !current)}
                    className="absolute inset-y-0 right-0 flex w-12 items-center justify-center text-muted-foreground transition-colors hover:text-foreground"
                  >
                    {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </button>
                </div>
                {fieldErrors.password ? (
                  <p className="text-sm text-red-600">{fieldErrors.password}</p>
                ) : null}
              </div>
              {loginMutation.error ? (
                <p className="text-sm text-red-600">{loginMutation.error.message}</p>
              ) : null}
              <Button className="h-11 w-full bg-[#315b86] text-white hover:bg-[#25496d] dark:bg-sky-300 dark:text-sky-950 dark:hover:bg-sky-200 xl:h-10" disabled={loginMutation.isPending} type="submit">
                {loginMutation.isPending ? t('login.submitting') : t('login.submit')}
                <ArrowRight aria-hidden="true" className="ml-2 h-4 w-4" />
              </Button>
        </form>
        </div> : null}

        {localCapabilitiesError ? (
          <p className="text-sm text-red-600">{t('systemConfig.capabilitiesUnavailable')}</p>
        ) : null}
        {localCapabilities && !passwordEnabled && !bootstrapConfig.enabled && !hasExternalMethods ? (
          <p className="text-sm text-muted-foreground">{t('systemConfig.passwordDisabled')}</p>
        ) : null}

        {bootstrapConfig.enabled ? (
          <div hidden={effectiveLoginMode !== 'organization'} className="space-y-2">
            <SessionBootstrapEntry
              methodDisplayName={bootstrapMethod?.displayName}
              onAuthenticated={() => navigate({ to: returnTo })}
              compact
            />
            <p className="text-sm text-muted-foreground">{t('login.orgLoginHint')}</p>
          </div>
        ) : null}

        {authMethodsLoading || hasExternalMethods ? (
          <section className="space-y-3" aria-label={t('login.otherMethods')}>
            <div className="flex items-center gap-3 text-xs text-muted-foreground before:h-px before:flex-1 before:bg-border after:h-px after:flex-1 after:bg-border">
              {t('login.otherMethods')}
            </div>
            <AuthMethodButtonList methods={authMethods} isLoading={authMethodsLoading} compact />
          </section>
        ) : null}

        {localCapabilities?.registrationAvailable ? <p className="text-center text-sm text-muted-foreground">
          {t('login.noAccount')}
          {' '}
          <Link to="/register" search={{ returnTo }} className="font-medium text-sky-700 hover:underline dark:text-sky-300">
            {t('login.register')}
          </Link>
        </p> : null}

        <p className="mt-auto text-center text-xs text-muted-foreground">
          {t('login.agreementPrefix')}
          {isChinese ? null : ' '}
          <Link to="/terms" className="text-primary hover:underline">
            {t('login.terms')}
          </Link>
          {isChinese ? null : ' '}
          {t('login.and')}
          {isChinese ? null : ' '}
          <Link to="/privacy" className="text-primary hover:underline">
            {t('login.privacy')}
          </Link>
        </p>
      </div>
    </AuthShell>
  )
}
