import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Loader2, RefreshCw } from 'lucide-react'
import { namespaceApi } from '@/api/client'
import type { NamespaceInvitationHistory } from '@/api/types'
import { useAuth } from '@/features/auth/use-auth'
import { Pagination } from '@/shared/components/pagination'
import { Button } from '@/shared/ui/button'

const HISTORY_PAGE_SIZE = 5

const statusKeys: Record<NamespaceInvitationHistory['status'], string> = {
  ACTIVE: 'invitation.historyActive', USED: 'invitation.historyUsed',
  REPLACED: 'invitation.historyReplaced', REVOKED: 'invitation.historyRevoked',
  EXPIRED: 'invitation.historyExpired',
}

/** Mounted only within the OWNER/ADMIN invitation dialog; the API checks the same role. */
export function InvitationHistory({ slug }: { slug: string }) {
  const { t, i18n } = useTranslation()
  const { user } = useAuth()
  const [page, setPage] = useState(0)
  const history = useQuery({
    queryKey: ['namespace-invitation-history', slug, user?.userId, page, HISTORY_PAGE_SIZE],
    enabled: !!user,
    staleTime: 0,
    retry: false,
    queryFn: () => namespaceApi.invitationHistory(slug, page, HISTORY_PAGE_SIZE),
  })
  const formatDate = (value: string) => new Intl.DateTimeFormat(i18n?.language ?? 'zh', {
    dateStyle: 'short', timeStyle: 'short',
  }).format(new Date(value))
  const identity = (name: string | null | undefined, id: string | null | undefined) => {
    const shortId = id && id.length > 18 ? `${id.slice(0, 8)}…${id.slice(-6)}` : id
    const label = name && id ? `${name} (${id})` : name || id || t('invitation.historyUnknownUser')
    return <span className="block min-w-0 truncate" title={label} aria-label={label}>
      {name || shortId || t('invitation.historyUnknownUser')}
    </span>
  }
  const status = (item: NamespaceInvitationHistory) => (
    <span className="inline-flex flex-wrap items-center gap-1">
      <span className="rounded-full bg-secondary px-2 py-0.5 text-[11px] font-medium">{t(statusKeys[item.status])}</span>
      {item.legacy ? <span className="text-[11px] text-muted-foreground">{t('invitation.historyLegacy')}</span> : null}
    </span>
  )
  const joined = (item: NamespaceInvitationHistory) => item.consumedAt ? (
    <>{identity(item.consumedByName, item.consumedBy)}<time className="mt-0.5 block text-[11px] text-muted-foreground" dateTime={item.consumedAt}>{formatDate(item.consumedAt)}</time></>
  ) : <span className="text-muted-foreground">{t('invitation.historyNoJoin')}</span>

  return (
    <section className="min-w-0 space-y-3 border-t pt-4" aria-label={t('invitation.historyTitle')}>
      <div className="flex items-center justify-between gap-2">
        <h3 className="text-sm font-semibold">{t('invitation.historyTitle')}</h3>
        <Button data-analytics-id="namespace.invitation.refresh" variant="ghost" size="sm" className="gap-1.5 px-2.5" disabled={history.isFetching} onClick={() => void history.refetch()}>
          <RefreshCw className="h-3.5 w-3.5" aria-hidden="true" />{t('invitation.historyRefresh')}
        </Button>
      </div>
      <p className="text-xs leading-relaxed text-muted-foreground">{t('invitation.historyDescription')}</p>
      {history.isPending ? <p role="status" className="flex items-center gap-2 py-4 text-sm text-muted-foreground"><Loader2 aria-hidden="true" className="h-4 w-4 animate-spin" />{t('invitation.historyLoading')}</p> : history.isError ? (
        <p role="alert" className="rounded-lg border border-destructive/20 bg-destructive/5 p-2 text-sm text-destructive">{t('invitation.historyError')}</p>
      ) : history.data?.items.length ? (
        <>
          <div className="max-h-60 overflow-auto rounded-lg border">
            <table className="hidden w-full table-fixed text-left text-xs sm:table">
              <caption className="sr-only">{t('invitation.historyTitle')}</caption>
              <thead className="sticky top-0 bg-secondary text-muted-foreground"><tr>
                <th scope="col" className="w-[26%] p-2 font-medium">{t('invitation.historyCreator')}</th>
                <th scope="col" className="w-[27%] p-2 font-medium">{t('invitation.historyCreatedAt')} / {t('invitation.historyExpiresAt')}</th>
                <th scope="col" className="w-[20%] p-2 font-medium">{t('invitation.historyStatus')}</th>
                <th scope="col" className="p-2 font-medium">{t('invitation.historyJoined')}</th>
              </tr></thead>
              <tbody>{history.data.items.map((item) => <tr key={item.invitationId} className="border-t align-top">
                <td className="p-2">{identity(item.issuedByName, item.issuedBy)}</td>
                <td className="space-y-0.5 p-2 text-[11px]"><time className="block" dateTime={item.issuedAt}>{formatDate(item.issuedAt)}</time><time className="block text-muted-foreground" dateTime={item.expiresAt}>{formatDate(item.expiresAt)}</time></td>
                <td className="p-2">{status(item)}</td>
                <td className="p-2">{joined(item)}</td>
              </tr>)}</tbody>
            </table>
            <ul className="divide-y sm:hidden">{history.data.items.map((item) => <li key={item.invitationId} className="space-y-1.5 p-2 text-xs">
              <div className="flex items-start justify-between gap-2"><div className="min-w-0 flex-1">{t('invitation.historyCreator')}: {identity(item.issuedByName, item.issuedBy)}</div>{status(item)}</div>
              <dl className="grid grid-cols-[auto_minmax(0,1fr)] gap-x-3 gap-y-1">
                <dt className="text-muted-foreground">{t('invitation.historyCreatedAt')}</dt><dd><time dateTime={item.issuedAt}>{formatDate(item.issuedAt)}</time></dd>
                <dt className="text-muted-foreground">{t('invitation.historyExpiresAt')}</dt><dd><time dateTime={item.expiresAt}>{formatDate(item.expiresAt)}</time></dd>
                <dt className="text-muted-foreground">{t('invitation.historyJoined')}</dt><dd className="min-w-0">{joined(item)}</dd>
              </dl>
            </li>)}</ul>
          </div>
          {history.data.totalPages > 1 ? <Pagination analyticsId="namespace.invitation.paginate" compact page={page} totalPages={history.data.totalPages} onPageChange={setPage} /> : null}
        </>
      ) : <p className="rounded-lg border border-dashed p-4 text-center text-sm text-muted-foreground">{t('invitation.historyEmpty')}</p>}
    </section>
  )
}
