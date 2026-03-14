import { Link, useNavigate, useSearch } from '@tanstack/react-router'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { getDirectAuthRuntimeConfig } from '@/api/client'
import { LoginButton } from '@/features/auth/login-button'
import { SessionBootstrapEntry } from '@/features/auth/session-bootstrap-entry'
import { useAuthMethods } from '@/features/auth/use-auth-methods'
import { usePasswordLogin } from '@/features/auth/use-password-login'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/shared/ui/tabs'

export function LoginPage() {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const search = useSearch({ from: '/login' })
  const loginMutation = usePasswordLogin()
  const directAuthConfig = getDirectAuthRuntimeConfig()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const isChinese = i18n.resolvedLanguage?.split('-')[0] === 'zh'
  const { data: authMethods } = useAuthMethods(search.returnTo)

  const returnTo = search.returnTo && search.returnTo.startsWith('/') ? search.returnTo : '/dashboard'
  const directMethod = directAuthConfig.provider
    ? authMethods?.find((method) =>
      method.methodType === 'DIRECT_PASSWORD' && method.provider === directAuthConfig.provider)
    : undefined
  const bootstrapMethod = authMethods?.find((method) => method.methodType === 'SESSION_BOOTSTRAP')

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    try {
      await loginMutation.mutateAsync({ username, password })
      await navigate({ to: returnTo })
    } catch {
      // mutation state drives the error UI
    }
  }

  return (
    <div className="flex min-h-[70vh] items-center justify-center">
      <div className="w-full max-w-md space-y-8 animate-fade-up">
        <div className="text-center space-y-3">
          <div className="inline-flex w-16 h-16 rounded-2xl bg-gradient-to-br from-primary to-primary/70 items-center justify-center shadow-glow mb-4">
            <span className="text-primary-foreground font-bold text-2xl">S</span>
          </div>
          <h1 className="text-4xl font-bold font-heading text-foreground">{t('login.title')}</h1>
          <p className="text-muted-foreground text-lg">
            {t('login.subtitle')}
          </p>
        </div>

        <div className="glass-strong p-8 rounded-2xl">
          <div className="space-y-6">
            <SessionBootstrapEntry
              methodDisplayName={bootstrapMethod?.displayName}
              onAuthenticated={() => navigate({ to: returnTo })}
            />

            <Tabs defaultValue="password" className="space-y-6">
              <TabsList className="grid w-full grid-cols-2">
                <TabsTrigger value="password">{t('login.tabPassword')}</TabsTrigger>
                <TabsTrigger value="oauth">{t('login.tabOAuth')}</TabsTrigger>
              </TabsList>

              <TabsContent value="password">
                <form className="space-y-4" onSubmit={handleSubmit}>
                  {directAuthConfig.enabled ? (
                    <p className="text-sm text-muted-foreground">
                      {t('login.passwordCompatHint', {
                        name: directMethod?.displayName ?? directAuthConfig.provider,
                      })}
                    </p>
                  ) : null}
                  <div className="space-y-2">
                    <label className="text-sm font-medium" htmlFor="username">{t('login.username')}</label>
                    <Input
                      id="username"
                      autoComplete="username"
                      value={username}
                      onChange={(event) => setUsername(event.target.value)}
                      placeholder={t('login.usernamePlaceholder')}
                    />
                  </div>
                  <div className="space-y-2">
                    <label className="text-sm font-medium" htmlFor="password">{t('login.password')}</label>
                    <Input
                      id="password"
                      type="password"
                      autoComplete="current-password"
                      value={password}
                      onChange={(event) => setPassword(event.target.value)}
                      placeholder={t('login.passwordPlaceholder')}
                    />
                  </div>
                  {loginMutation.error ? (
                    <p className="text-sm text-red-600">{loginMutation.error.message}</p>
                  ) : null}
                  <Button className="w-full" disabled={loginMutation.isPending} type="submit">
                    {loginMutation.isPending ? t('login.submitting') : t('login.submit')}
                  </Button>
                  <p className="text-center text-sm text-muted-foreground">
                    {t('login.noAccount')}
                    {' '}
                    <Link
                      to="/register"
                      search={{ returnTo }}
                      className="font-medium text-primary hover:underline"
                    >
                      {t('login.register')}
                    </Link>
                  </p>
                </form>
              </TabsContent>

              <TabsContent value="oauth" className="space-y-4">
                <p className="text-sm text-muted-foreground">
                  {t('login.oauthHint')}
                </p>
                <LoginButton returnTo={returnTo} />
              </TabsContent>
            </Tabs>
          </div>
        </div>

        <p className="text-center text-xs text-muted-foreground">
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
    </div>
  )
}
