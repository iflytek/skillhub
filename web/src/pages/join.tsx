import { useEffect, useRef, useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { namespaceApi } from '@/api/client'
import { useAuth } from '@/features/auth/use-auth'
import { ApiError } from '@/shared/lib/api-error'
import { captureInvitationFragment, clearInvitationToken, storedInvitationToken } from '@/shared/lib/namespace-invitation'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'

export function JoinPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { user } = useAuth()
  const enteredNamespace = useRef(false)
  const [token] = useState(() => {
    captureInvitationFragment()
    return storedInvitationToken()
  })
  const preview = useQuery({
    queryKey: ['namespace-invitation', token],
    queryFn: () => namespaceApi.previewInvitation(token!),
    enabled: !!token,
    retry: false,
  })
  const accept = useMutation({
    mutationFn: () => namespaceApi.acceptInvitation(token!),
    onSuccess: async (result) => {
      clearInvitationToken()
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['namespaces'] }),
        queryClient.invalidateQueries({ queryKey: ['namespace-invitation-history', result.namespaceSlug] }),
      ])
      await navigate({ to: '/space/$namespace', params: { namespace: result.namespaceSlug }, replace: true })
    },
  })
  const invalid = !token || (preview.error instanceof ApiError && preview.error.status === 404)
    || (accept.error instanceof ApiError && accept.error.status === 404)
  useEffect(() => { if (invalid) clearInvitationToken() }, [invalid])
  useEffect(() => {
    if (preview.data?.outcome !== 'ALREADY_MEMBER' || enteredNamespace.current) return
    enteredNamespace.current = true
    clearInvitationToken()
    void navigate({ to: '/space/$namespace', params: { namespace: preview.data.namespaceSlug }, replace: true })
  }, [navigate, preview.data])

  return (
    <div className="mx-auto max-w-xl px-4 py-16">
      <Card className="space-y-5 p-8">
        <h1 className="text-2xl font-semibold">{t('invitation.title')}</h1>
        {invalid ? <p role="alert">{t('invitation.invalid')}</p> : null}
        {!invalid && preview.isPending ? <p role="status">{t('invitation.loading')}</p> : null}
        {!invalid && preview.error ? (
          <div className="space-y-3">
            <p role="alert">{t('invitation.networkError')}</p>
            <Button data-analytics-id="namespace.join.retry" onClick={() => preview.refetch()}>{t('invitation.retry')}</Button>
          </div>
        ) : null}
        {!invalid && preview.data ? (
          <div className="space-y-4">
            <p>{t('invitation.namespace', { name: preview.data.namespaceDisplayName })}</p>
            <p>{t('invitation.account', { name: user?.displayName ?? user?.userId ?? '' })}</p>
            {preview.data.outcome === 'ALREADY_MEMBER' ? (
              <p role="status">{t('invitation.alreadyMember')}</p>
            ) : (
              <>
                <p>{t('invitation.permissions')}</p>
                <Button data-analytics-id="namespace.join.confirm" disabled={accept.isPending} onClick={() => accept.mutate()}>{t('invitation.confirm')}</Button>
                {accept.error && !(accept.error instanceof ApiError && accept.error.status === 404) ? (
                  <p role="alert">{t('invitation.networkError')}</p>
                ) : null}
              </>
            )}
          </div>
        ) : null}
      </Card>
    </div>
  )
}
