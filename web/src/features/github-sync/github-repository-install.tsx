import { useId, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Check, ChevronDown, Copy, Package, Terminal } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { buildApiUrl, fetchJson, WEB_API_PREFIX } from '@/api/client'
import type { components } from '@/api/generated/schema'
import { useAuth } from '@/features/auth/use-auth'
import { Button } from '@/shared/ui/button'
import { copyToClipboard } from '@/shared/lib/clipboard'
import { ApiError } from '@/shared/lib/api-error'

export function GithubRepositoryInstall({ repositoryId }: { repositoryId: string }) {
  const { t } = useTranslation()
  const { user, isLoading: authLoading } = useAuth()
  const [open, setOpen] = useState(false)
  const [copied, setCopied] = useState(false)
  const [copyFailed, setCopyFailed] = useState(false)
  const [commandAction, setCommandAction] = useState<'install' | 'check' | 'upgrade'>('install')
  const id = useId()
  const plan = useQuery({
    queryKey: ['skills', 'github-repository-install', user?.userId, repositoryId],
    queryFn: ({ signal }) => fetchJson<components['schemas']['GithubRepositoryInstallPlanResponse']>(
      `${WEB_API_PREFIX}/github-repositories/${repositoryId}/install-plan`, { signal }),
    enabled: open && !authLoading,
    gcTime: 0,
    retry: false,
  })
  const unavailable = plan.error instanceof ApiError && plan.error.status === 404
  const registry = new URL(buildApiUrl('/'), window.location.origin).href.replace(/\/$/, '')
  // Do not emit shell metacharacters from runtime configuration into copied commands.
  const command = /^[a-zA-Z0-9:/._~-]+$/.test(registry)
    ? `skillhub repository ${commandAction} ${repositoryId}${commandAction === 'install' ? ' --scope project' : ''} --registry "${registry}"` : ''

  return <section className="rounded-xl border border-border bg-card" aria-label={t('githubCatalog.installTitle')}>
    <div className="flex flex-wrap items-center justify-between gap-3 px-4 py-3">
      <div className="flex min-w-0 items-start gap-3">
        <Terminal className="mt-0.5 size-4 shrink-0 text-muted-foreground" aria-hidden="true" />
        <div className="space-y-1">
          <h2 className="text-sm font-semibold">{t('githubCatalog.installTitle')}</h2>
          <p className="text-xs text-muted-foreground">{t('githubCatalog.installSubtitle')}</p>
        </div>
      </div>
      <Button size="sm" variant={open ? 'outline' : 'default'} aria-expanded={open} aria-controls={id} onClick={() => {
        setOpen(!open)
        setCopied(false)
        setCopyFailed(false)
      }}>
        {t(open ? 'githubCatalog.installCollapse' : 'githubCatalog.installOpen')}
        <ChevronDown className={`size-4 ${open ? 'rotate-180' : ''}`} aria-hidden="true" />
      </Button>
    </div>
    {open && <div id={id} className="space-y-4 border-t border-border p-4">
      <p className="text-sm text-muted-foreground">{t('githubCatalog.installExplanation')}</p>
      {plan.isLoading || authLoading ? <p role="status" className="text-sm text-muted-foreground">{t('githubCatalog.installLoading')}</p> : null}
      {plan.isError && <div role="alert" className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-border bg-muted/30 p-3 text-sm">
        <p>{t(unavailable ? 'githubCatalog.installEmpty' : 'githubCatalog.installFailed')}</p>
        <Button variant="outline" size="sm" onClick={() => void plan.refetch()}>{t('githubCatalog.repositoryRetry')}</Button>
      </div>}
      {plan.isSuccess && <>
        <div className="space-y-2">
          <h3 className="text-xs font-medium">{t('githubCatalog.installMembers', { count: plan.data.members.length })}</h3>
          <ul className="max-h-60 divide-y divide-border overflow-y-auto rounded-lg border border-border" aria-label={t('githubCatalog.installVersions')}>
            {plan.data.members.map(member => <li key={member.skillId} className="flex min-w-0 items-start gap-2 px-3 py-2 text-sm">
              <Package className="mt-0.5 size-4 shrink-0 text-muted-foreground" aria-hidden="true" />
              <div className="min-w-0 flex-1">
                <p className="break-words font-medium [overflow-wrap:anywhere]">{member.displayName}</p>
                <p className="break-all text-xs text-muted-foreground">@{member.namespace}/{member.slug}</p>
              </div>
              <code className="max-w-[40%] break-all text-xs text-muted-foreground">{member.version}</code>
            </li>)}
          </ul>
        </div>
        <div className="space-y-2">
          <h3 className="text-xs font-medium">{t('githubCatalog.installCommand')}</h3>
          <div role="group" aria-label={t('githubCatalog.repositoryCommandAction')} className="flex flex-wrap gap-2">
            {(['install', 'check', 'upgrade'] as const).map(action => <Button key={action} size="sm"
              variant={commandAction === action ? 'secondary' : 'outline'} aria-pressed={commandAction === action}
              onClick={() => { setCommandAction(action); setCopied(false); setCopyFailed(false) }}>
              {t(`githubCatalog.repositoryCommandActions.${action}`)}
            </Button>)}
          </div>
          {command ? <div className="flex min-w-0 flex-wrap items-start gap-2 rounded-lg border border-border bg-muted/30 p-3">
            <code className="min-w-0 flex-1 break-all text-xs leading-5">{command}</code>
            <Button variant="outline" size="sm" onClick={() => {
              setCopyFailed(false)
              void copyToClipboard(command).then(() => setCopied(true)).catch(() => setCopyFailed(true))
            }}>
              {copied ? <Check className="size-3.5" aria-hidden="true" /> : <Copy className="size-3.5" aria-hidden="true" />}
              {t(copied ? 'copyButton.copied' : 'copyButton.copy')}
            </Button>
          </div> : <p role="alert" className="text-sm text-destructive">{t('githubCatalog.installRegistryInvalid')}</p>}
          {copied && <p role="status" className="sr-only">{t('copyButton.copied')}</p>}
          {copyFailed && <p role="alert" className="text-xs text-destructive">{t('githubCatalog.installCopyFailed')}</p>}
          <p className="text-xs leading-5 text-muted-foreground">{t('githubCatalog.installLocalHint')}</p>
          {commandAction !== 'install' && <p className="text-xs leading-5 text-muted-foreground">{t('githubCatalog.repositoryUpdateHint')}</p>}
        </div>
      </>}
    </div>}
  </section>
}
