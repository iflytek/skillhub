import { Link, useNavigate, useSearch } from '@tanstack/react-router'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { LoginButton } from '@/features/auth/login-button'
import { useLocalRegister } from '@/features/auth/use-local-auth'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/shared/ui/tabs'

export function RegisterPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const search = useSearch({ from: '/register' })
  const registerMutation = useLocalRegister()
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  const returnTo = search.returnTo && search.returnTo.startsWith('/') ? search.returnTo : '/dashboard'

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    try {
      await registerMutation.mutateAsync({ username, email, password })
      await navigate({ to: returnTo })
    } catch {
      // mutation state drives the error UI
    }
  }

  return (
    <div className="mx-auto flex min-h-[70vh] max-w-2xl items-center justify-center">
      <Card className="w-full border-slate-200 bg-white/95 shadow-xl">
        <CardHeader className="space-y-3 text-center">
          <CardTitle>{t('register.title')}</CardTitle>
          <CardDescription>{t('register.subtitle')}</CardDescription>
        </CardHeader>
        <CardContent>
          <Tabs defaultValue="local" className="space-y-6">
            <TabsList className="grid w-full grid-cols-2">
              <TabsTrigger value="local">{t('register.tabLocal')}</TabsTrigger>
              <TabsTrigger value="oauth">{t('register.tabOAuth')}</TabsTrigger>
            </TabsList>

            <TabsContent value="local">
              <form className="space-y-4" onSubmit={handleSubmit}>
                <div className="space-y-2">
                  <label className="text-sm font-medium" htmlFor="register-username">{t('register.username')}</label>
                  <Input
                    id="register-username"
                    autoComplete="username"
                    value={username}
                    onChange={(event) => setUsername(event.target.value)}
                    placeholder={t('register.usernamePlaceholder')}
                  />
                </div>
                <div className="space-y-2">
                  <label className="text-sm font-medium" htmlFor="register-email">{t('register.email')}</label>
                  <Input
                    id="register-email"
                    type="email"
                    autoComplete="email"
                    value={email}
                    onChange={(event) => setEmail(event.target.value)}
                    placeholder={t('register.emailPlaceholder')}
                  />
                </div>
                <div className="space-y-2">
                  <label className="text-sm font-medium" htmlFor="register-password">{t('register.password')}</label>
                  <Input
                    id="register-password"
                    type="password"
                    autoComplete="new-password"
                    value={password}
                    onChange={(event) => setPassword(event.target.value)}
                    placeholder={t('register.passwordPlaceholder')}
                  />
                </div>
                {registerMutation.error ? (
                  <p className="text-sm text-red-600">{registerMutation.error.message}</p>
                ) : null}
                <Button className="w-full" disabled={registerMutation.isPending} type="submit">
                  {registerMutation.isPending ? t('register.submitting') : t('register.submit')}
                </Button>
                <p className="text-center text-sm text-muted-foreground">
                  {t('register.hasAccount')}
                  {' '}
                  <Link
                    to="/login"
                    search={{ returnTo }}
                    className="font-medium text-primary hover:underline"
                  >
                    {t('register.login')}
                  </Link>
                </p>
              </form>
            </TabsContent>

            <TabsContent value="oauth" className="space-y-4">
              <p className="text-sm text-muted-foreground">
                {t('register.oauthHint')}
              </p>
              <LoginButton returnTo={returnTo} />
            </TabsContent>
          </Tabs>
        </CardContent>
      </Card>
    </div>
  )
}
