import { lazy, Suspense, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import type { GithubSyncReviewSource } from '@/api/types'
import { buildApiUrl, fetchText, WEB_API_PREFIX } from '@/api/client'
import { canPreviewFile } from '@/features/skill/file-type-utils'
import { useReviewFile } from './use-review-file'
import { Card } from '@/shared/ui/card'
import { Button } from '@/shared/ui/button'

const ReviewFileDiff = lazy(() => import('./review-file-diff'))
const MAX_INLINE_PREVIEW_BYTES = 256 * 1024
const MAX_DIFF_BYTES = 64 * 1024
const MAX_DIFF_LINES = 1000

function exceedsDiffLineLimit(value: string) {
  let lines = 1
  for (let index = 0; index < value.length; index += 1) {
    if (value.charCodeAt(index) === 10 && ++lines > MAX_DIFF_LINES) return true
  }
  return false
}

export function GithubSyncReviewDiff({ source, reviewId }: { source: GithubSyncReviewSource; reviewId: number }) {
  const { t } = useTranslation()
  const [selectedPath, setSelectedPath] = useState<string | null>(null)
  const [viewMode, setViewMode] = useState<'changes' | 'full'>('changes')
  const files = source.fileChanges
  const selected = files.find((file) => file.path === selectedPath)
    ?? files.find((file) => {
      const size = file.changeType === 'REMOVED' ? file.oldSize ?? 0 : file.newSize ?? 0
      return size <= MAX_INLINE_PREVIEW_BYTES && canPreviewFile(file.path, size).canPreview
    })
    ?? files[0] ?? null
  const selectedIndex = selected ? files.findIndex((file) => file.path === selected.path) : -1
  const selectedSize = selected?.changeType === 'REMOVED' ? selected.oldSize ?? 0 : selected?.newSize ?? 0
  const removedBaseUnavailable = selected?.changeType === 'REMOVED'
    && (selected.oldSize == null || selected.oldSize > 1024 * 1024)
  const previewCheck = selected ? canPreviewFile(selected.path, selectedSize) : null
  const canLoadText = Boolean(previewCheck?.canPreview && selectedSize <= MAX_INLINE_PREVIEW_BYTES
    && !removedBaseUnavailable)
  const canComputeDiff = Boolean(canLoadText && selected?.oldSha256 && selected?.newSha256
    && (selected.oldSize ?? Infinity) <= MAX_DIFF_BYTES && (selected.newSize ?? Infinity) <= MAX_DIFF_BYTES)
  const needsOldText = selected?.changeType === 'REMOVED' || (viewMode === 'changes' && canComputeDiff)
  const oldText = useQuery({
    queryKey: ['reviews', reviewId, 'sync-base-file-preview', selected?.oldSha256, selected?.path],
    queryFn: ({ signal }) => fetchText(`${WEB_API_PREFIX}/reviews/sync/${reviewId}/base-file?path=${encodeURIComponent(selected!.path)}&preview=true`, { signal }),
    enabled: canLoadText && !!selected?.oldSha256 && needsOldText,
    staleTime: 5 * 60 * 1000,
    gcTime: 60 * 1000,
  })
  const newText = useReviewFile(reviewId, selected?.path ?? null, canLoadText && !!selected?.newSha256)
  const useLineDiff = viewMode === 'changes' && canComputeDiff && oldText.data != null && newText.data != null
    && !exceedsDiffLineLimit(oldText.data) && !exceedsDiffLineLimit(newText.data)
  const displayedText = selected?.changeType === 'REMOVED' ? oldText.data : newText.data
  const downloadPath = selected?.changeType === 'REMOVED'
    ? `${WEB_API_PREFIX}/reviews/sync/${reviewId}/base-file?path=${encodeURIComponent(selected.path)}`
    : `${WEB_API_PREFIX}/reviews/${reviewId}/file?path=${encodeURIComponent(selected?.path ?? '')}`
  const isLoadingText = (needsOldText && oldText.isLoading) || (!!selected?.newSha256 && newText.isLoading)
  const isTextError = (needsOldText && oldText.isError) || (!!selected?.newSha256 && newText.isError)
  const selectFile = (path: string) => {
    setSelectedPath(path)
    setViewMode('changes')
  }

  return (
    <Card className="min-w-0 overflow-hidden">
      <div className="flex flex-wrap items-center justify-between gap-2 border-b px-3 py-3">
        <div className="min-w-0">
          <h2 className="text-sm font-semibold">{t(source.baseVersionId ? 'review.githubFileChanges' : 'review.githubFirstImport')}</h2>
          <p className="mt-0.5 text-xs text-muted-foreground">{t(source.baseVersionId ? 'review.githubChangesDescription' : 'review.githubFirstImportDescription')}</p>
        </div>
        <span className="text-xs text-muted-foreground">{t('review.githubChangedFilesCount', { count: files.length })}</span>
      </div>
      {selected ? (
        <>
          <div className="flex flex-wrap items-center gap-2 border-b bg-muted/20 px-3 py-2 text-xs">
            <Button data-analytics-id="review.file.previous" type="button" variant="outline" size="sm" className="h-7 w-7 p-0" aria-label={t('review.githubPreviousFile')}
              disabled={selectedIndex <= 0} onClick={() => selectFile(files[selectedIndex - 1].path)}><ChevronLeft className="h-3.5 w-3.5" /></Button>
            <select data-analytics-id="review.file.select" aria-label={t('review.githubSelectFile')} value={selected.path}
              onChange={(event) => selectFile(event.target.value)}
              className="h-7 min-w-0 flex-1 rounded border border-border bg-background px-2 font-mono text-xs sm:max-w-xl">
              {files.map((file) => <option key={file.path} value={file.path}>{t(`review.githubChange${file.changeType}`)} · {file.path}</option>)}
            </select>
            <Button data-analytics-id="review.file.next" type="button" variant="outline" size="sm" className="h-7 w-7 p-0" aria-label={t('review.githubNextFile')}
              disabled={selectedIndex >= files.length - 1} onClick={() => selectFile(files[selectedIndex + 1].path)}><ChevronRight className="h-3.5 w-3.5" /></Button>
            <span className="text-muted-foreground">{selectedIndex + 1}/{files.length}</span>
            <span className="rounded bg-primary/10 px-1.5 py-0.5 text-primary">{t(`review.githubChange${selected.changeType}`)}</span>
          </div>
          <div className="flex flex-wrap items-center justify-between gap-2 border-b px-3 py-2 text-xs">
            <span className="min-w-0 break-all font-mono">{selected.path}</span>
            {canComputeDiff && selected.changeType === 'MODIFIED' && (
              <div className="flex shrink-0 gap-1" role="group" aria-label={t('review.githubViewMode')}>
                <Button data-analytics-id="review.diff.changes" size="sm" className="h-7 px-2" variant={viewMode === 'changes' ? 'secondary' : 'ghost'} onClick={() => setViewMode('changes')}>{t('review.githubViewChanges')}</Button>
                <Button data-analytics-id="review.diff.full" size="sm" className="h-7 px-2" variant={viewMode === 'full' ? 'secondary' : 'ghost'} onClick={() => setViewMode('full')}>{t('review.githubViewFull')}</Button>
              </div>
            )}
          </div>
          <div className="min-h-64 min-w-0 overflow-x-auto p-3 text-xs">
            {!canLoadText ? (
              <div className="space-y-2">
                <p className="text-muted-foreground">{removedBaseUnavailable
                  ? t('review.githubBaseFileUnavailable')
                  : t(selectedSize > MAX_INLINE_PREVIEW_BYTES ? 'filePreview.tooLarge'
                    : previewCheck?.reason === 'binary' ? 'filePreview.binaryFile' : 'filePreview.unsupported')}</p>
                {!removedBaseUnavailable && (
                  <a data-analytics-id="review.file.download" className="inline-block text-primary underline" href={buildApiUrl(downloadPath)} download>{t('filePreview.downloadFile')}</a>
                )}
              </div>
            ) : isLoadingText ? (
              <p className="text-muted-foreground">{t('review.githubDiffLoading')}</p>
            ) : isTextError ? (
              <p className="text-destructive">{t('review.githubDiffError')}</p>
            ) : useLineDiff ? (
              <Suspense fallback={<p className="text-muted-foreground">{t('review.githubDiffLoading')}</p>}>
                <ReviewFileDiff oldText={oldText.data!} newText={newText.data!} />
              </Suspense>
            ) : (
              <div className="space-y-2">
                {selected.changeType === 'MODIFIED' && viewMode === 'changes' && <p className="text-muted-foreground">{t('review.githubLargeDiffNotice')}</p>}
                <pre className="max-h-[70vh] overflow-auto whitespace-pre font-mono text-xs leading-5">{displayedText}</pre>
              </div>
            )}
          </div>
        </>
      ) : <p className="p-4 text-sm text-muted-foreground">{t('review.githubNoFileChanges')}</p>}
    </Card>
  )
}

export function GithubSyncReviewEvidence({ source }: { source: GithubSyncReviewSource }) {
  const { t } = useTranslation()
  const repository = `${source.ownerLogin}/${source.repositoryName}`
  const commitUrl = `https://github.com/${encodeURIComponent(source.ownerLogin)}/${encodeURIComponent(source.repositoryName)}/commit/${encodeURIComponent(source.commitSha)}`

  return (
    <Card className="space-y-3 p-4">
      <div>
        <h2 className="text-sm font-semibold">{t('review.githubSourceTitle')}</h2>
        <p className="mt-0.5 text-xs text-muted-foreground">{t('review.githubSourceReadOnly')}</p>
      </div>
      <dl className="grid gap-x-4 gap-y-3 text-xs sm:grid-cols-2">
        <div><dt className="text-muted-foreground">{t('review.githubRepository')}</dt><dd className="mt-1 break-all font-medium">{repository}</dd></div>
        <div><dt className="text-muted-foreground">{t('review.githubSkillPath')}</dt><dd className="mt-1 break-all font-mono">{source.skillPath}</dd></div>
        <div><dt className="text-muted-foreground">{t('review.githubCommit')}</dt><dd className="mt-1"><a data-analytics-id="review.commit.external" className="break-all font-mono text-primary underline" href={commitUrl} target="_blank" rel="noopener noreferrer">{source.commitSha}</a></dd></div>
        <div><dt className="text-muted-foreground">{t('review.githubRunId')}</dt><dd className="mt-1 break-all font-mono">{source.runId}</dd></div>
        <div><dt className="text-muted-foreground">{t('review.githubLicenseStatus')}</dt><dd className="mt-1 font-medium">{t(`review.githubLicense${source.evidence.status}`, { defaultValue: source.evidence.status })}</dd></div>
      </dl>
      {source.evidence.status !== 'RECOGNIZED' && (
        <p className="rounded-md border border-amber-500/40 bg-amber-500/10 p-2 text-xs">{t('review.githubLicenseWarning')}</p>
      )}
      <div>
        <h3 className="text-sm font-semibold">{t('review.githubLicenseDocuments')}</h3>
        {source.evidence.documents.length === 0 ? (
          <p className="mt-2 text-xs text-muted-foreground">{t('review.githubNoLicenseDocuments')}</p>
        ) : source.evidence.documents.map((document) => (
          <details key={`${document.scope}/${document.path}`} className="mt-2 rounded-md border p-2 text-xs">
            <summary className="cursor-pointer break-all font-mono">{document.scope}/{document.path}</summary>
            {document.text ? <pre className="mt-3 max-h-64 overflow-auto whitespace-pre-wrap break-words text-xs">{document.text}</pre> : <p className="mt-3 text-muted-foreground">{t('review.githubLicensePreviewUnavailable')}</p>}
          </details>
        ))}
      </div>
    </Card>
  )
}
