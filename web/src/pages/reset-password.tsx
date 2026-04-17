import { Link } from '@tanstack/react-router'
import { FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { authApi } from '@/api/client'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'

/**
 * Public page for verifying a reset code and setting a new password.
 */
export function ResetPasswordPage() {
  const { t } = useTranslation()
  const [email, setEmail] = useState('')
  const [code, setCode] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [isSendingCode, setIsSendingCode] = useState(false)
  const [isSuccess, setIsSuccess] = useState(false)
  const [codeSentMessage, setCodeSentMessage] = useState<string | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const emailPattern = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/

  async function handleSendCode() {
    const normalizedEmail = email.trim().toLowerCase()
    if (!normalizedEmail) {
      setErrorMessage(t('resetPassword.emailRequired'))
      return
    }
    if (!emailPattern.test(normalizedEmail)) {
      setErrorMessage(t('resetPassword.emailInvalid'))
      return
    }

    setIsSendingCode(true)
    setErrorMessage(null)
    setCodeSentMessage(null)
    try {
      await authApi.requestPasswordReset({ email: normalizedEmail })
      setCodeSentMessage(t('resetPassword.codeSentMessage'))
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : t('resetPassword.genericError'))
    } finally {
      setIsSendingCode(false)
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    const normalizedEmail = email.trim().toLowerCase()
    if (!normalizedEmail) {
      setErrorMessage(t('resetPassword.emailRequired'))
      return
    }
    if (!emailPattern.test(normalizedEmail)) {
      setErrorMessage(t('resetPassword.emailInvalid'))
      return
    }
    if (!code.trim()) {
      setErrorMessage(t('resetPassword.codeRequired'))
      return
    }
    if (!newPassword) {
      setErrorMessage(t('resetPassword.newPasswordRequired'))
      return
    }
    if (newPassword !== confirmPassword) {
      setErrorMessage(t('resetPassword.passwordMismatch'))
      return
    }

    setIsSubmitting(true)
    setErrorMessage(null)
    try {
      await authApi.confirmPasswordReset({
        email: normalizedEmail,
        code: code.trim(),
        newPassword,
      })
      setIsSuccess(true)
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : t('resetPassword.genericError'))
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="mx-auto flex min-h-[70vh] max-w-2xl items-center justify-center">
      <Card className="w-full border-slate-200 bg-white/95 shadow-xl">
        <CardHeader className="space-y-3 text-center">
          <CardTitle>{t('resetPassword.title')}</CardTitle>
          <CardDescription>{t('resetPassword.subtitle')}</CardDescription>
        </CardHeader>
        <CardContent>
          {isSuccess ? (
            <div className="space-y-4">
              <p className="rounded-md border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-700">
                {t('resetPassword.successMessage')}
              </p>
              <Link to="/login" search={{ returnTo: '' }} className="block text-center font-medium text-primary hover:underline">
                {t('resetPassword.backToLogin')}
              </Link>
            </div>
          ) : (
            <form className="space-y-4" onSubmit={handleSubmit}>
              <div className="space-y-2">
                <label className="text-sm font-medium" htmlFor="reset-password-email">
                  {t('resetPassword.email')}
                </label>
                <div className="flex flex-col gap-2 sm:flex-row">
                  <Input
                    id="reset-password-email"
                    type="email"
                    value={email}
                    onChange={(event) => setEmail(event.target.value)}
                    placeholder={t('resetPassword.emailPlaceholder')}
                    autoComplete="email"
                    required
                  />
                  <Button
                    className="sm:w-auto"
                    disabled={isSendingCode || isSubmitting}
                    type="button"
                    variant="outline"
                    onClick={handleSendCode}
                  >
                    {isSendingCode ? t('resetPassword.sendingCode') : t('resetPassword.sendCode')}
                  </Button>
                </div>
              </div>
              <div className="space-y-2">
                <label className="text-sm font-medium" htmlFor="reset-password-code">
                  {t('resetPassword.code')}
                </label>
                <Input
                  id="reset-password-code"
                  value={code}
                  onChange={(event) => setCode(event.target.value)}
                  placeholder={t('resetPassword.codePlaceholder')}
                  autoComplete="one-time-code"
                />
              </div>
              <div className="space-y-2">
                <label className="text-sm font-medium" htmlFor="reset-password-new-password">
                  {t('resetPassword.newPassword')}
                </label>
                <Input
                  id="reset-password-new-password"
                  type="password"
                  value={newPassword}
                  onChange={(event) => setNewPassword(event.target.value)}
                  placeholder={t('resetPassword.newPasswordPlaceholder')}
                  autoComplete="new-password"
                />
              </div>
              <div className="space-y-2">
                <label className="text-sm font-medium" htmlFor="reset-password-confirm-password">
                  {t('resetPassword.confirmPassword')}
                </label>
                <Input
                  id="reset-password-confirm-password"
                  type="password"
                  value={confirmPassword}
                  onChange={(event) => setConfirmPassword(event.target.value)}
                  placeholder={t('resetPassword.confirmPasswordPlaceholder')}
                  autoComplete="new-password"
                />
              </div>
              {errorMessage ? (
                <p className="text-sm text-red-600">{errorMessage}</p>
              ) : null}
              {codeSentMessage ? (
                <p className="text-sm text-emerald-700">{codeSentMessage}</p>
              ) : null}
              <Button className="w-full" disabled={isSubmitting} type="submit">
                {isSubmitting ? t('resetPassword.submitting') : t('resetPassword.submit')}
              </Button>
            </form>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
