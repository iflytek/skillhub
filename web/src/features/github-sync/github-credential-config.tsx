import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useGithubCredentialMutations, useGithubCredentials } from './use-github-sync-admin'
import { githubSyncStatusLabel } from './status-label'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { toast } from '@/shared/lib/toast'
import { Card } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import { Button } from '@/shared/ui/button'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/shared/ui/table'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/shared/ui/dialog'

export function GithubCredentialConfig() {
  const { t, i18n } = useTranslation()
  const credentials = useGithubCredentials()
  const mutations = useGithubCredentialMutations()
  const [open, setOpen] = useState(false)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [label, setLabel] = useState('')
  const [token, setToken] = useState('')

  const reset = () => {
    setEditingId(null)
    setLabel('')
    setToken('')
  }

  const save = async (event: FormEvent) => {
    event.preventDefault()
    try {
      if (editingId) {
        await mutations.replace.mutateAsync({ id: editingId, label: label.trim(), token })
      } else {
        await mutations.create.mutateAsync({ label: label.trim(), token })
      }
      reset()
      setOpen(false)
      toast.success(t('githubSync.saved'))
    } catch (error) {
      toast.error(t('githubSync.saveFailed'), error instanceof Error ? error.message : '')
    }
  }

  const setEnabled = async (id: string, enabled: boolean) => {
    try {
      await mutations.setEnabled.mutateAsync({ id, enabled })
      toast.success(t('githubSync.saved'))
    } catch (error) {
      toast.error(t('githubSync.saveFailed'), error instanceof Error ? error.message : '')
    }
  }

  const openCreate = () => {
    reset()
    setOpen(true)
  }

  const openReplace = (id: string, currentLabel: string) => {
    setEditingId(id)
    setLabel(currentLabel)
    setToken('')
    setOpen(true)
  }

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div><h2 className="font-semibold">{t('githubSync.credentials')}</h2><p className="text-sm text-muted-foreground">{t('githubSync.credentialHelp')}</p></div>
        <Button data-analytics-id="github.credential.create" size="sm" className="sync-action" onClick={openCreate}>{t('githubSync.addCredential')}</Button>
      </div>
      <Dialog open={open} onOpenChange={(nextOpen) => { setOpen(nextOpen); if (!nextOpen) reset() }}>
        <DialogContent closeAnalyticsId={editingId ? 'github.credential.replace.cancel' : 'github.credential.create.cancel'} className="github-sync-surface rounded-md">
          <DialogHeader><DialogTitle>{editingId ? t('githubSync.replaceCredential') : t('githubSync.addCredential')}</DialogTitle><DialogDescription>{t('githubSync.credentialHelp')}</DialogDescription></DialogHeader>
          <form data-analytics-id={editingId ? 'github.credential.replace.submit' : 'github.credential.create.submit'} onSubmit={save} className="space-y-3">
            <label className="block text-sm">{t('githubSync.credentialLabel')}
              <Input autoComplete="off" maxLength={100} value={label} onChange={(event) => setLabel(event.target.value)} required />
            </label>
            <label className="block text-sm">{t('githubSync.credentialToken')}
              <Input type="password" autoComplete="new-password" minLength={20} maxLength={512}
                value={token} onChange={(event) => setToken(event.target.value)} required />
            </label>
            <div className="flex justify-end gap-2"><Button data-analytics-id={editingId ? 'github.credential.replace.cancel' : 'github.credential.create.cancel'} type="button" size="sm" variant="outline" className="sync-action" onClick={() => setOpen(false)}>{t('githubSync.cancel')}</Button><Button type="submit" size="sm" className="sync-action" disabled={mutations.create.isPending || mutations.replace.isPending}>{editingId ? t('githubSync.replaceCredential') : t('githubSync.addCredential')}</Button></div>
          </form>
        </DialogContent>
      </Dialog>
      <Card className="sync-section overflow-x-auto">
        {credentials.isLoading ? <p className="p-6 text-sm text-muted-foreground">{t('githubSync.loading')}</p> : null}
        {credentials.error ? <p role="alert" className="p-6 text-sm text-destructive">{t('githubSync.loadFailed')}: {credentials.error.message}</p> : null}
        {credentials.data?.length === 0 ? <p className="p-6 text-sm text-muted-foreground">{t('githubSync.noCredentials')}</p> : null}
        {credentials.data?.length ? (
          <Table className="min-w-[960px] [&_td]:px-3 [&_th]:px-3">
            <TableHeader><TableRow>
              <TableHead>{t('githubSync.credentialLabel')}</TableHead>
              <TableHead>{t('githubSync.fingerprint')}</TableHead>
              <TableHead>{t('githubSync.sourceState')}</TableHead>
              <TableHead>{t('githubSync.keyVersion')}</TableHead>
              <TableHead>{t('githubSync.rateLimitedUntil')}</TableHead>
              <TableHead>{t('githubSync.observedQuota')}</TableHead>
              <TableHead>{t('githubSync.lastUsed')}</TableHead>
              <TableHead>{t('githubSync.details')}</TableHead>
            </TableRow></TableHeader>
            <TableBody>{credentials.data.map((credential) => (
              <TableRow key={credential.id}>
                <TableCell className="font-medium">{credential.label}</TableCell>
                <TableCell className="font-mono text-xs">{credential.fingerprint ?? '—'}</TableCell>
                <TableCell>{githubSyncStatusLabel(t, credential.status)}</TableCell>
                <TableCell>{credential.keyVersion ?? '—'}{credential.keyAvailable === false ? <span className="block text-xs text-destructive">{t('githubSync.keyUnavailable')}</span> : null}</TableCell>
                <TableCell>{formatLocalDateTime(credential.rateLimitedUntil, i18n.language)}</TableCell>
                <TableCell className="text-xs">{credential.quotas?.length ? credential.quotas.map((quota) => (
                  <div key={quota.resource}>{quota.resource}: {quota.remaining ?? '—'} · {formatLocalDateTime(quota.resetAt, i18n.language)}</div>
                )) : '—'}</TableCell>
                <TableCell>{formatLocalDateTime(credential.lastUsedAt, i18n.language)}</TableCell>
                <TableCell className="space-x-2 whitespace-nowrap">
                  <Button data-analytics-id="github.credential.replace" size="sm" variant="outline" className="sync-action" disabled={!credential.id} onClick={() => credential.id && openReplace(credential.id, credential.label ?? '')}>
                    {t('githubSync.replaceCredential')}
                  </Button>
                  <Button data-analytics-id={credential.status === 'ACTIVE' ? 'github.credential.disable' : 'github.credential.enable'} size="sm" variant="outline" className="sync-action" disabled={!credential.id || mutations.setEnabled.isPending || credential.status === 'INVALID' || credential.status !== 'ACTIVE' && credential.keyAvailable === false}
                    onClick={() => credential.id && setEnabled(credential.id, credential.status !== 'ACTIVE')}>
                    {credential.status === 'ACTIVE' ? t('githubSync.disable') : t('githubSync.enable')}
                  </Button>
                </TableCell>
              </TableRow>
            ))}</TableBody>
          </Table>
        ) : null}
      </Card>
    </div>
  )
}
