import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { buildApiUrl, WEB_API_PREFIX } from '@/api/client'
import type { ReviewSkillDetail } from '@/api/types'
import { FileTree } from '@/features/skill/file-tree'
import { FilePreviewDialog } from '@/features/skill/file-preview-dialog'
import { canPreviewFile } from '@/features/skill/file-type-utils'
import type { FileTreeNode } from '@/features/skill/file-tree-builder'
import { MarkdownRenderer } from '@/features/skill/markdown-renderer'
import { ComplianceSnapshotPanel } from '@/features/skill/compliance-snapshot-panel'
import { ReviewComplianceDiffPanel, pickBaseVersion } from './review-compliance-diff-panel'
import { buttonVariants } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { getReviewDownloadHref, getReviewSkillDocumentation, isActiveReviewVersion } from './review-skill-detail'
import { useReviewFile } from './use-review-file'

interface ReviewSkillDetailSectionProps {
  detail?: ReviewSkillDetail
  isLoading?: boolean
  hasError?: boolean
  reviewId?: number
  documentationTooLarge?: boolean
  documentationLoading?: boolean
  documentationError?: boolean
}

export function ReviewSkillDetailSection({ detail, isLoading, hasError, reviewId, documentationTooLarge = false,
  documentationLoading = false, documentationError = false }: ReviewSkillDetailSectionProps) {
  const { t } = useTranslation()
  const [previewNode, setPreviewNode] = useState<FileTreeNode | null>(null)
  const [previewDialogOpen, setPreviewDialogOpen] = useState(false)
  const { data: previewContent, isLoading: isLoadingPreview, error: previewError } = useReviewFile(
    reviewId,
    previewNode?.path || null,
    previewDialogOpen && !!previewNode && (previewNode.file?.fileSize ?? 0) <= 256 * 1024
      && canPreviewFile(previewNode.name, previewNode.file?.fileSize ?? 0).canPreview,
  )

  if (isLoading) return <Card className="h-56 animate-shimmer" />
  if (hasError) return <Card className="p-6 text-sm text-destructive">{t('review.skillDetailError')}</Card>
  if (!detail) return null

  const documentation = getReviewSkillDocumentation(detail)
  const downloadHref = getReviewDownloadHref(detail)
  const handleDownloadFile = () => {
    if (!previewNode || !reviewId) return
    const url = buildApiUrl(`${WEB_API_PREFIX}/reviews/${reviewId}/file?path=${encodeURIComponent(previewNode.path)}`)
    const link = document.createElement('a')
    link.href = url
    link.download = previewNode.name
    document.body.appendChild(link)
    link.click()
    link.remove()
  }

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-muted-foreground">{t('review.skillDetailDescription', { skill: `${detail.skill.namespace}/${detail.skill.slug}` })}</p>
        {downloadHref && <a data-analytics-id="review.package.download" className={buttonVariants({ variant: 'outline', size: 'sm', className: 'px-2.5' })} href={buildApiUrl(downloadHref)}>
          {t('review.downloadSkillZip')}
        </a>}
      </div>
      <div className="grid min-w-0 gap-3 lg:grid-cols-[minmax(220px,260px)_minmax(0,1fr)]">
        <Card className="min-w-0 p-3">
          <h3 className="mb-2 text-sm font-semibold">{t('fileTree.title')} <span className="font-normal text-muted-foreground">{detail.files.length}</span></h3>
          {detail.files.length > 0 ? (
            <div className="max-h-[620px] overflow-auto">
              <FileTree analyticsId="review.file.open" files={detail.files} onFileClick={(node) => { setPreviewNode(node); setPreviewDialogOpen(true) }} bare compact />
            </div>
          ) : <p className="text-sm text-muted-foreground">{t('skillDetail.noFiles')}</p>}
        </Card>
        <Card className="min-w-0 p-4">
          <h3 className="mb-3 text-sm font-semibold">{t('review.documentationTitle')}</h3>
          {documentationLoading ? (
            <div className="h-24 animate-shimmer rounded-lg" />
          ) : documentationError ? (
            <div className="space-y-2 text-sm text-muted-foreground">
              <p className="text-destructive">{t('review.documentationLoadFailed')}</p>
              {detail.documentationPath && reviewId && (
                <a data-analytics-id="review.file.download" className="text-primary underline" href={buildApiUrl(`${WEB_API_PREFIX}/reviews/${reviewId}/file?path=${encodeURIComponent(detail.documentationPath)}`)} download>
                  {t('filePreview.downloadFile')}
                </a>
              )}
            </div>
          ) : documentation ? (
            <div className="min-w-0">
              <p className="mb-3 break-all font-mono text-xs text-muted-foreground">{documentation.path}</p>
              <MarkdownRenderer content={documentation.content} />
            </div>
          ) : documentationTooLarge && detail.documentationPath && reviewId ? (
            <div className="space-y-2 text-sm text-muted-foreground">
              <p>{t('review.documentationTooLarge')}</p>
              <a data-analytics-id="review.file.download" className="text-primary underline" href={buildApiUrl(`${WEB_API_PREFIX}/reviews/${reviewId}/file?path=${encodeURIComponent(detail.documentationPath)}`)} download>
                {t('filePreview.downloadFile')}
              </a>
            </div>
          ) : <p className="text-sm text-muted-foreground">{t('review.noDocumentation')}</p>}
        </Card>
      </div>
      <FilePreviewDialog closeAnalyticsId="review.file.close" downloadAnalyticsId="review.file.download"
        open={previewDialogOpen}
        onOpenChange={setPreviewDialogOpen}
        node={previewNode}
        content={previewContent || null}
        isLoading={isLoadingPreview}
        error={previewError}
        onDownload={handleDownloadFile}
        maxPreviewBytes={256 * 1024} copyAnalyticsId="review.file.copy"
      />
    </div>
  )
}

export function ReviewSkillChangesSection({ detail, isLoading, hasError, concealTechnicalVersions = false }: Omit<ReviewSkillDetailSectionProps, 'reviewId'> & { concealTechnicalVersions?: boolean }) {
  const { t } = useTranslation()
  if (isLoading) return <Card className="h-40 animate-shimmer" />
  if (hasError) return <Card className="p-6 text-sm text-destructive">{t('review.skillDetailError')}</Card>
  if (!detail) return null

  const pendingVersion = detail.versions.find((version) => version.version === detail.activeVersion) ?? null
  const baseVersion = pickBaseVersion(detail.versions, detail.activeVersion ?? '')
  const versionHistory = (
    <Card className="space-y-3 p-4">
      <h3 className="text-base font-semibold">{t('skillDetail.tabVersions')}</h3>
      {detail.versions.length > 0 ? detail.versions.map((version) => (
        <div key={version.id} className="rounded-md border border-border p-3">
          <div className="flex flex-wrap items-center gap-2 text-sm">
            <span className="break-all font-mono font-semibold">{version.version}</span>
            <span className="rounded-full bg-muted px-2 py-0.5 text-xs">{version.status}</span>
            {isActiveReviewVersion(version, detail) && <span className="rounded-full bg-primary/10 px-2 py-0.5 text-xs text-primary">{t('review.activeReviewVersion')}</span>}
            <span className="ml-auto text-muted-foreground">{t('skillDetail.fileCount', { count: version.fileCount })}</span>
          </div>
          {version.changelog && <p className="mt-2 text-sm text-muted-foreground">{version.changelog}</p>}
          <ComplianceSnapshotPanel snapshot={version.complianceSnapshot} />
        </div>
      )) : <p className="text-sm text-muted-foreground">{t('skillDetail.noVersions')}</p>}
    </Card>
  )

  return (
    <div className="space-y-4">
      <ReviewComplianceDiffPanel baseVersion={baseVersion} pendingVersion={pendingVersion}
        concealVersionIdentifiers={concealTechnicalVersions} />
      {concealTechnicalVersions ? (
        <details className="rounded-md border border-border bg-card p-4">
          <summary className="cursor-pointer text-sm font-medium">{t('review.technicalDetails')}</summary>
          <div className="mt-4">{versionHistory}</div>
        </details>
      ) : versionHistory}
    </div>
  )
}
