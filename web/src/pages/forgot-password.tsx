import { Link } from '@tanstack/react-router'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation } from '@tanstack/react-query'
import { authApi } from '@/api/client'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'

export function ForgotPasswordPage() {
  const { t } = useTranslation()
  const [identifier, setIdentifier] = useState('')
  const [submitted, setSubmitted] = useState(false)

  const requestMutation = useMutation({
    mutationFn: (identifier: string) => authApi.requestPasswordReset(identifier),
    onSuccess: () => {
      setSubmitted(true)
    },
  })

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const trimmedIdentifier = identifier.trim()
    if (!trimmedIdentifier) {
      return
    }
    await requestMutation.mutateAsync(trimmedIdentifier)
  }

  if (submitted) {
    return (
      <div className="mx-auto flex min-h-[70vh] max-w-2xl items-center justify-center">
        <Card className="w-full border-slate-200 bg-white/95 shadow-xl">
          <CardHeader className="space-y-3 text-center">
            <CardTitle>{t('forgotPassword.successTitle')}</CardTitle>
            <CardDescription>{t('forgotPassword.successMessage')}</CardDescription>
          </CardHeader>
          <CardContent>
            <Link
              to="/login"
              search={{ returnTo: '' }}
              className="block w-full text-center font-medium text-primary hover:underline"
            >
              {t('forgotPassword.backToLogin')}
            </Link>
          </CardContent>
        </Card>
      </div>
    )
  }

  return (
    <div className="mx-auto flex min-h-[70vh] max-w-2xl items-center justify-center">
      <Card className="w-full border-slate-200 bg-white/95 shadow-xl">
        <CardHeader className="space-y-3 text-center">
          <CardTitle>{t('forgotPassword.title')}</CardTitle>
          <CardDescription>{t('forgotPassword.subtitle')}</CardDescription>
        </CardHeader>
        <CardContent>
          <form className="space-y-4" onSubmit={handleSubmit}>
            <div className="space-y-2">
              <label className="text-sm font-medium" htmlFor="identifier">
                {t('forgotPassword.identifierLabel')}
              </label>
              <Input
                id="identifier"
                autoComplete="username"
                value={identifier}
                onChange={(event) => setIdentifier(event.target.value)}
                placeholder={t('forgotPassword.identifierPlaceholder')}
              />
            </div>
            {requestMutation.error ? (
              <p className="text-sm text-red-600">{requestMutation.error.message}</p>
            ) : null}
            <Button className="w-full" disabled={requestMutation.isPending} type="submit">
              {requestMutation.isPending ? t('forgotPassword.submitting') : t('forgotPassword.submit')}
            </Button>
            <p className="text-center text-sm text-muted-foreground">
              {t('forgotPassword.rememberPassword')}
              {' '}
              <Link
                to="/login"
                search={{ returnTo: '' }}
                className="font-medium text-primary hover:underline"
              >
                {t('forgotPassword.backToLogin')}
              </Link>
            </p>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}

