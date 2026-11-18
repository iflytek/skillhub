import { useState } from 'react'
import { Archive, Download } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { buildApiUrl, WEB_API_PREFIX } from '@/api/client'
import type { components } from '@/api/generated/schema'
import { Button } from '@/shared/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/shared/ui/dialog'

type Snapshot = components['schemas']['GithubRepositorySnapshot']

export function GithubSnapshotDownload({ repositoryId, repositoryName, snapshot }: {
  repositoryId: string
  repositoryName: string
  snapshot: Snapshot
}) {
  const { t, i18n } = useTranslation()
  const [open, setOpen] = useState(false)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  const [downloadStarted, setDownloadStarted] = useState(false)

  async function download() {
    setError('')
    setDownloadStarted(false)
    setPending(true)
    try {
      const endpoint = `${WEB_API_PREFIX}/github-repositories/${repositoryId}/snapshots/${snapshot.id}/archive`
      const response = await fetch(buildApiUrl(endpoint), {
        headers: { 'Accept-Language': i18n.resolvedLanguage ?? i18n.language },
      })
      if (!response.ok || !response.headers.get('content-type')?.startsWith('application/zip')) {
        const failure = await response.json().catch(() => null)
        setError(typeof failure?.msg === 'string' ? failure.msg : t('githubCatalog.repositorySnapshotDownloadFailed'))
        return
      }
      const url = URL.createObjectURL(await response.blob())
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = `github-${repositoryId}-snapshot-${snapshot.id}.zip`
      try {
        anchor.click()
        setDownloadStarted(true)
      } finally {
        window.setTimeout(() => URL.revokeObjectURL(url), 1000)
      }
    } catch {
      setError(t('githubCatalog.repositorySnapshotDownloadFailed'))
    } finally {
      setPending(false)
    }
  }

  return <Dialog open={open} onOpenChange={next => {
    if (!pending) {
      setOpen(next)
      setError('')
      setDownloadStarted(false)
    }
  }}>
    <DialogTrigger asChild>
      <Button size="sm"><Download className="size-4" aria-hidden="true" />{t('githubCatalog.repositorySnapshotDownload')}</Button>
    </DialogTrigger>
    <DialogContent hideClose={pending} className="max-h-[calc(100dvh-2rem)] w-[min(calc(100vw-2rem),34rem)] gap-0 p-0">
      <DialogHeader className="border-b border-border bg-muted/25 px-5 py-4 pr-12 text-left">
        <DialogTitle>{t('githubCatalog.repositorySnapshotDownload')}</DialogTitle>
        <DialogDescription className="break-words [overflow-wrap:anywhere]">
          {repositoryName} · {t('githubCatalog.repositoryMemberCount', { count: snapshot.memberCount })} · <code>{snapshot.commit.slice(0, 10)}</code>
        </DialogDescription>
      </DialogHeader>
      <div className="space-y-3 px-5 py-4">
        <div className="flex items-start gap-3 rounded-lg border border-border bg-muted/25 p-3">
          <Archive className="mt-0.5 size-5 shrink-0 text-muted-foreground" aria-hidden="true" />
          <p className="text-sm text-muted-foreground">{t('githubCatalog.repositorySnapshotArchiveContents')}</p>
        </div>
        {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
        {downloadStarted && <p role="status" className="text-sm">{t('githubCatalog.repositorySnapshotDownloadStarted')}</p>}
      </div>
      <DialogFooter className="border-t border-border bg-muted/25 px-5 py-3 sm:justify-end">
        <Button className="w-full sm:w-auto" size="sm" disabled={pending} onClick={() => void download()}>
          <Download className="size-4" aria-hidden="true" />
          {t(pending ? 'githubCatalog.repositorySnapshotDownloadPreparing' : 'githubCatalog.repositorySnapshotDownload')}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
}
