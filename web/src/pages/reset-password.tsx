import { Link, useNavigate, useSearch } from '@tanstack/react-router'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation } from '@tanstack/react-query'
import { Eye, EyeOff } from 'lucide-react'
import { authApi } from '@/api/client'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'

export function ResetPasswordPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const search = useSearch({ from: '/reset-password' })
  const token = search.token || ''
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [showConfirmPassword, setShowConfirmPassword] = useState(false)
  const [fieldErrors, setFieldErrors] = useState<{ newPassword?: string, confirmPassword?: string }>({})

  const resetMutation = useMutation({
    mutationFn: ({ token, newPassword }: { token: string, newPassword: string }) =>
      authApi.confirmPasswordReset(token, newPassword),
    onSuccess: () => {
      setTimeout(() => {
        navigate({ to: '/login', search: { returnTo: '' } })
      }, 2000)
    },
  })

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const nextFieldErrors: { newPassword?: string, confirmPassword?: string } = {}

    if (!newPassword) {
      nextFieldErrors.newPassword = t('resetPassword.passwordRequired')
    }
    if (!confirmPassword) {
      nextFieldErrors.confirmPassword = t('resetPassword.confirmPasswordRequired')
    }
    if (newPassword && confirmPassword && newPassword !== confirmPassword) {
      nextFieldErrors.confirmPassword = t('resetPassword.passwordMismatch')
    }
    if (nextFieldErrors.newPassword || nextFieldErrors.confirmPassword) {
      setFieldErrors(nextFieldErrors)
      return
    }

    setFieldErrors({})
    try {
      await resetMutation.mutateAsync({ token, newPassword })
    } catch {
      // mutation state drives the error UI
    }
  }

  if (!token) {
    return (
      <div className="mx-auto flex min-h-[70vh] max-w-2xl items-center justify-center">
        <Card className="w-full border-slate-200 bg-white/95 shadow-xl">
          <CardHeader className="space-y-3 text-center">
            <CardTitle>{t('resetPassword.invalidTitle')}</CardTitle>
            <CardDescription>{t('resetPassword.invalidMessage')}</CardDescription>
          </CardHeader>
          <CardContent>
            <Link
              to="/forgot-password"
              className="block w-full text-center font-medium text-primary hover:underline"
            >
              {t('resetPassword.requestNew')}
            </Link>
          </CardContent>
        </Card>
      </div>
    )
  }

  if (resetMutation.isSuccess) {
    return (
      <div className="mx-auto flex min-h-[70vh] max-w-2xl items-center justify-center">
        <Card className="w-full border-slate-200 bg-white/95 shadow-xl">
          <CardHeader className="space-y-3 text-center">
            <CardTitle>{t('resetPassword.successTitle')}</CardTitle>
            <CardDescription>{t('resetPassword.successMessage')}</CardDescription>
          </CardHeader>
        </Card>
      </div>
    )
  }

  return (
    <div className="mx-auto flex min-h-[70vh] max-w-2xl items-center justify-center">
      <Card className="w-full border-slate-200 bg-white/95 shadow-xl">
        <CardHeader className="space-y-3 text-center">
          <CardTitle>{t('resetPassword.title')}</CardTitle>
          <CardDescription>{t('resetPassword.subtitle')}</CardDescription>
        </CardHeader>
        <CardContent>
          <form className="space-y-4" onSubmit={handleSubmit}>
            <div className="space-y-2">
              <label className="text-sm font-medium" htmlFor="new-password">
                {t('resetPassword.newPassword')}
              </label>
              <div className="relative">
                <Input
                  id="new-password"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="new-password"
                  value={newPassword}
                  onChange={(event) => {
                    setNewPassword(event.target.value)
                    if (fieldErrors.newPassword) {
                      setFieldErrors((current) => ({ ...current, newPassword: undefined }))
                    }
                  }}
                  placeholder={t('resetPassword.newPasswordPlaceholder')}
                  className="pr-12"
                  aria-invalid={fieldErrors.newPassword ? 'true' : 'false'}
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
              {fieldErrors.newPassword ? (
                <p className="text-sm text-red-600">{fieldErrors.newPassword}</p>
              ) : null}
            </div>
            <div className="space-y-2">
              <label className="text-sm font-medium" htmlFor="confirm-password">
                {t('resetPassword.confirmPassword')}
              </label>
              <div className="relative">
                <Input
                  id="confirm-password"
                  type={showConfirmPassword ? 'text' : 'password'}
                  autoComplete="new-password"
                  value={confirmPassword}
                  onChange={(event) => {
                    setConfirmPassword(event.target.value)
                    if (fieldErrors.confirmPassword) {
                      setFieldErrors((current) => ({ ...current, confirmPassword: undefined }))
                    }
                  }}
                  placeholder={t('resetPassword.confirmPasswordPlaceholder')}
                  className="pr-12"
                  aria-invalid={fieldErrors.confirmPassword ? 'true' : 'false'}
                />
                <button
                  type="button"
                  aria-label={showConfirmPassword ? t('login.hidePassword') : t('login.showPassword')}
                  aria-pressed={showConfirmPassword}
                  onClick={() => setShowConfirmPassword((current) => !current)}
                  className="absolute inset-y-0 right-0 flex w-12 items-center justify-center text-muted-foreground transition-colors hover:text-foreground"
                >
                  {showConfirmPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
              {fieldErrors.confirmPassword ? (
                <p className="text-sm text-red-600">{fieldErrors.confirmPassword}</p>
              ) : null}
            </div>
            {resetMutation.error ? (
              <p className="text-sm text-red-600">{resetMutation.error.message}</p>
            ) : null}
            <Button className="w-full" disabled={resetMutation.isPending} type="submit">
              {resetMutation.isPending ? t('resetPassword.submitting') : t('resetPassword.submit')}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}



