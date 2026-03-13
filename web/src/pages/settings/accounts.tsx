import { useState } from 'react'
import { useConfirmAccountMerge, useInitiateAccountMerge, useVerifyAccountMerge } from '@/features/auth/use-account-merge'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'

export function AccountSettingsPage() {
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
      setStatusMessage(`已创建合并请求，secondary=${result.secondaryUserId}`)
    } catch (error) {
      setStatusMessage(error instanceof Error ? error.message : '发起合并失败')
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
      setStatusMessage('验证成功，确认后将执行正式合并')
    } catch (error) {
      setStatusMessage(error instanceof Error ? error.message : '验证合并失败')
    }
  }

  async function handleConfirm() {
    setStatusMessage('')
    try {
      await confirmMutation.mutateAsync({ mergeRequestId: Number(mergeRequestId) })
      setStatusMessage('账号合并已完成')
    } catch (error) {
      setStatusMessage(error instanceof Error ? error.message : '确认合并失败')
    }
  }

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <Card className="glass-strong">
        <CardHeader>
          <CardTitle>发起账号合并</CardTitle>
          <CardDescription>输入 secondary 账号标识。支持本地用户名，或 `provider:subject` 格式的外部身份。</CardDescription>
        </CardHeader>
        <CardContent>
          <form className="space-y-4" onSubmit={handleInitiate}>
            <div className="space-y-2">
              <label className="text-sm font-medium" htmlFor="secondary-identifier">Secondary 标识</label>
              <Input
                id="secondary-identifier"
                value={secondaryIdentifier}
                onChange={(event) => setSecondaryIdentifier(event.target.value)}
                placeholder="例如：other_user 或 github:123456"
              />
            </div>
            <Button type="submit" disabled={initiateMutation.isPending}>
              {initiateMutation.isPending ? '发起中...' : '发起合并'}
            </Button>
          </form>
        </CardContent>
      </Card>

      <Card className="glass-strong">
        <CardHeader>
          <CardTitle>验证并完成合并</CardTitle>
          <CardDescription>先完成 token 验证，再单独确认执行数据迁移。</CardDescription>
        </CardHeader>
        <CardContent>
          <form className="space-y-4" onSubmit={handleVerify}>
            <div className="space-y-2">
              <label className="text-sm font-medium" htmlFor="merge-request-id">Merge Request ID</label>
              <Input
                id="merge-request-id"
                value={mergeRequestId}
                onChange={(event) => setMergeRequestId(event.target.value)}
              />
            </div>
            <div className="space-y-2">
              <label className="text-sm font-medium" htmlFor="merge-token">Verification Token</label>
              <Input
                id="merge-token"
                value={verificationToken}
                onChange={(event) => setVerificationToken(event.target.value)}
              />
            </div>
            <Button type="submit" disabled={verifyMutation.isPending}>
              {verifyMutation.isPending ? '验证中...' : '完成合并'}
            </Button>
          </form>
          <div className="mt-4">
            <Button type="button" onClick={handleConfirm} disabled={confirmMutation.isPending || !mergeRequestId}>
              {confirmMutation.isPending ? '确认中...' : '确认并完成合并'}
            </Button>
          </div>
          {statusMessage ? <p className="mt-4 text-sm text-muted-foreground">{statusMessage}</p> : null}
        </CardContent>
      </Card>
    </div>
  )
}
