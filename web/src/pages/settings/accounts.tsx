import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useConfirmAccountMerge, useInitiateAccountMerge, useVerifyAccountMerge } from '@/features/auth/use-account-merge'
import { truncateErrorMessage } from '@/shared/lib/error-display'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'

export function AccountSettingsPage() {
  const { t } = useTranslation()
  const [secondaryIdentifier, setSecondaryIdentifier] = useState('')
  const [mergeRequestId, setMergeRequestId] = useState('')
  const [verificationToken, setVerificationToken] = useState('')
  const [statusMessage, setStatusMessage] = useState('')

  const initiateMutation = useInitiateAccountMerge()
  const verifyMutation = useVerifyAccountMerge()
  const confirmMutation = useConfirmAccountMerge()

  async function handleInitiate(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setStatusMessage('')
    try {
      const result = await initiateMutation.mutateAsync({ secondaryIdentifier })
      setMergeRequestId(String(result.mergeRequestId))
      setVerificationToken(result.verificationToken)
      setStatusMessage(t('accounts.initiateSuccess', { secondaryUserId: result.secondaryUserId }))
    } catch (error) {
      setStatusMessage(
        truncateErrorMessage(error instanceof Error ? error.message : t('accounts.initiateError')) ?? t('accounts.initiateError'),
      )
    }
  }

  async function handleVerify(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setStatusMessage('')
    try {
      await verifyMutation.mutateAsync({
        mergeRequestId: Number(mergeRequestId),
        verificationToken,
      })
      setStatusMessage(t('accounts.verifySuccess'))
    } catch (error) {
      setStatusMessage(
        truncateErrorMessage(error instanceof Error ? error.message : t('accounts.verifyError')) ?? t('accounts.verifyError'),
      )
    }
  }

  async function handleConfirm() {
    setStatusMessage('')
    try {
      await confirmMutation.mutateAsync({ mergeRequestId: Number(mergeRequestId) })
      setStatusMessage(t('accounts.confirmSuccess'))
    } catch (error) {
      setStatusMessage(
        truncateErrorMessage(error instanceof Error ? error.message : t('accounts.confirmError')) ?? t('accounts.confirmError'),
      )
    }
  }

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <Card className="glass-strong">
        <CardHeader>
          <CardTitle>{t('accounts.initiateTitle')}</CardTitle>
          <CardDescription>{t('accounts.initiateDesc')}</CardDescription>
        </CardHeader>
        <CardContent>
          <form className="space-y-4" onSubmit={handleInitiate}>
            <div className="space-y-2">
              <label className="text-sm font-medium" htmlFor="secondary-identifier">{t('accounts.secondaryLabel')}</label>
              <Input
                id="secondary-identifier"
                value={secondaryIdentifier}
                onChange={(event) => setSecondaryIdentifier(event.target.value)}
                placeholder={t('accounts.secondaryPlaceholder')}
              />
            </div>
            <Button type="submit" disabled={initiateMutation.isPending}>
              {initiateMutation.isPending ? t('accounts.initiating') : t('accounts.initiate')}
            </Button>
          </form>
        </CardContent>
      </Card>

      <Card className="glass-strong">
        <CardHeader>
          <CardTitle>{t('accounts.verifyTitle')}</CardTitle>
          <CardDescription>{t('accounts.verifyDesc')}</CardDescription>
        </CardHeader>
        <CardContent>
          <form className="space-y-4" onSubmit={handleVerify}>
            <div className="space-y-2">
              <label className="text-sm font-medium" htmlFor="merge-request-id">{t('accounts.mergeRequestId')}</label>
              <Input
                id="merge-request-id"
                value={mergeRequestId}
                onChange={(event) => setMergeRequestId(event.target.value)}
              />
            </div>
            <div className="space-y-2">
              <label className="text-sm font-medium" htmlFor="merge-token">{t('accounts.verificationToken')}</label>
              <Input
                id="merge-token"
                value={verificationToken}
                onChange={(event) => setVerificationToken(event.target.value)}
              />
            </div>
            <Button type="submit" disabled={verifyMutation.isPending}>
              {verifyMutation.isPending ? t('accounts.verifying') : t('accounts.verify')}
            </Button>
          </form>
          <div className="mt-4">
            <Button type="button" onClick={handleConfirm} disabled={confirmMutation.isPending || !mergeRequestId}>
              {confirmMutation.isPending ? t('accounts.confirming') : t('accounts.confirm')}
            </Button>
          </div>
          {statusMessage ? <p className="mt-4 text-sm text-muted-foreground">{statusMessage}</p> : null}
        </CardContent>
      </Card>
    </div>
  )
}
