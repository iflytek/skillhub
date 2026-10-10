import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useCancelAccountMerge, useConfirmAccountMerge, useInitiateAccountMerge, useInspectAccountMerge, useVerifyAccountMerge } from '@/features/auth/use-account-merge'
import { truncateErrorMessage } from '@/shared/lib/error-display'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'

/**
 * Account linking settings page for the multi-step account merge workflow.
 * The route intentionally keeps all three steps visible because operators often
 * need to carry a request id between their two authenticated accounts.
 */
export function AccountSettingsPage() {
  const { t } = useTranslation()
  const [secondaryIdentifier, setSecondaryIdentifier] = useState('')
  const [mergeRequestId, setMergeRequestId] = useState('')
  const [statusMessage, setStatusMessage] = useState('')

  const initiateMutation = useInitiateAccountMerge()
  const inspectMutation = useInspectAccountMerge()
  const verifyMutation = useVerifyAccountMerge()
  const cancelMutation = useCancelAccountMerge()
  const confirmMutation = useConfirmAccountMerge()
  const validMergeRequestId = Number.isSafeInteger(Number(mergeRequestId)) && Number(mergeRequestId) > 0
  const approvalDetails = inspectMutation.data?.mergeRequestId === Number(mergeRequestId) ? inspectMutation.data : null

  /**
   * Starts the merge flow and surfaces the request id for the second account.
   */
  async function handleInitiate(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setStatusMessage('')
    try {
      const result = await initiateMutation.mutateAsync({ secondaryIdentifier })
      setMergeRequestId(String(result.mergeRequestId))
      inspectMutation.reset()
      setStatusMessage(t('accounts.initiateSuccess', { secondaryUserId: result.secondaryUserId }))
    } catch (error) {
      setStatusMessage(
        truncateErrorMessage(error instanceof Error ? error.message : t('accounts.initiateError')) ?? t('accounts.initiateError'),
      )
    }
  }

  /**
   * Loads the destination while signed in as the second account.
   */
  async function handleInspect() {
    setStatusMessage('')
    inspectMutation.reset()
    try {
      await inspectMutation.mutateAsync(Number(mergeRequestId))
    } catch (error) {
      setStatusMessage(
        truncateErrorMessage(error instanceof Error ? error.message : t('accounts.inspectError')) ?? t('accounts.inspectError'),
      )
    }
  }

  /**
   * Approves the merge only from the second account's authenticated session.
   */
  async function handleVerify(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setStatusMessage('')
    try {
      await verifyMutation.mutateAsync({
        mergeRequestId: Number(mergeRequestId),
      })
      inspectMutation.reset()
      setStatusMessage(t('accounts.verifySuccess'))
    } catch (error) {
      setStatusMessage(
        truncateErrorMessage(error instanceof Error ? error.message : t('accounts.verifyError')) ?? t('accounts.verifyError'),
      )
    }
  }

  /**
   * Finalizes the merge after verification has succeeded.
   */
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

  async function handleCancel() {
    setStatusMessage('')
    try {
      await cancelMutation.mutateAsync({ mergeRequestId: Number(mergeRequestId) })
      inspectMutation.reset()
      setStatusMessage(t('accounts.cancelSuccess'))
    } catch (error) {
      setStatusMessage(
        truncateErrorMessage(error instanceof Error ? error.message : t('accounts.cancelError')) ?? t('accounts.cancelError'),
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
                onChange={(event) => {
                  setMergeRequestId(event.target.value)
                  inspectMutation.reset()
                }}
                inputMode="numeric"
              />
            </div>
            <Button type="button" onClick={handleInspect} disabled={!validMergeRequestId || inspectMutation.isPending}>
              {t('accounts.inspect')}
            </Button>
            {approvalDetails ? (
              <p className="text-sm" role="status">
                {t('accounts.destinationWarning', {
                  name: approvalDetails.primaryDisplayName,
                  id: approvalDetails.primaryUserId,
                })}
              </p>
            ) : null}
            <Button type="submit" disabled={verifyMutation.isPending || !approvalDetails || !validMergeRequestId}>
              {verifyMutation.isPending ? t('accounts.verifying') : t('accounts.verify')}
            </Button>
          </form>
          <div className="mt-4">
            <p className="mb-2 text-sm text-muted-foreground">{t('accounts.confirmDesc')}</p>
            <Button type="button" onClick={handleConfirm} disabled={confirmMutation.isPending || !validMergeRequestId}>
              {confirmMutation.isPending ? t('accounts.confirming') : t('accounts.confirm')}
            </Button>
            <Button type="button" variant="outline" onClick={handleCancel} disabled={cancelMutation.isPending || !validMergeRequestId}>
              {t('accounts.cancel')}
            </Button>
          </div>
          {statusMessage ? <p className="mt-4 text-sm text-muted-foreground">{statusMessage}</p> : null}
        </CardContent>
      </Card>
    </div>
  )
}
