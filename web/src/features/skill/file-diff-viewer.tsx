import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import ReactDiffViewer from 'react-diff-viewer-continued'
import { AlertCircle, RefreshCw } from 'lucide-react'
import { useSkillFile } from '@/shared/hooks/use-skill-queries'
import { Button } from '@/shared/ui/button'

const BINARY_EXTENSIONS = new Set([
  'png', 'jpg', 'jpeg', 'gif', 'ico', 'woff', 'woff2', 'ttf', 'eot',
  'zip', 'tar', 'gz', 'jar', 'war', 'class', 'so', 'dll', 'exe', 'pdf'
])

function isBinaryFile(filePath: string): boolean {
  const ext = filePath.split('.').pop()?.toLowerCase() || ''
  return BINARY_EXTENSIONS.has(ext)
}

function isLargeFile(sizeBytes: number): boolean {
  return sizeBytes > 1024 * 1024 // 1MB
}

interface FileDiffViewerProps {
  namespace: string
  slug: string
  sourceVersion: string | null
  targetVersion: string | null
  filePath: string
  fileSize: number
  changeType: 'added' | 'removed' | 'modified'
  isExpanded: boolean
}

export function FileDiffViewer({
  namespace,
  slug,
  sourceVersion,
  targetVersion,
  filePath,
  fileSize,
  changeType,
  isExpanded,
}: FileDiffViewerProps) {
  const { t } = useTranslation()
  const [splitView, setSplitView] = useState(false)

  // Only fetch source version if it's not a newly added file
  const {
    data: sourceContent,
    isLoading: sourceLoading,
    error: sourceError,
    refetch: refetchSource
  } = useSkillFile(
    namespace,
    slug,
    sourceVersion ?? undefined,
    changeType !== 'added' ? filePath : null,
    isExpanded
  )

  // Only fetch target version if it's not a removed file
  const {
    data: targetContent,
    isLoading: targetLoading,
    error: targetError,
    refetch: refetchTarget
  } = useSkillFile(
    namespace,
    slug,
    targetVersion ?? undefined,
    changeType !== 'removed' ? filePath : null,
    isExpanded
  )

  if (!isExpanded) return null

  if (isBinaryFile(filePath)) {
    return (
      <div className="flex items-center justify-center p-8 text-sm text-muted-foreground bg-muted/20 rounded-md border border-border/40 my-4">
        {t('skillDetail.binaryFileNotice')}
      </div>
    )
  }

  const isLoading = sourceLoading || targetLoading
  const hasError = sourceError || targetError

  if (isLoading) {
    return (
      <div className="p-8 space-y-2 animate-pulse my-4">
        <div className="h-4 bg-muted rounded w-3/4"></div>
        <div className="h-4 bg-muted rounded w-1/2"></div>
        <div className="h-4 bg-muted rounded w-5/6"></div>
      </div>
    )
  }

  if (hasError) {
    return (
      <div className="flex flex-col items-center justify-center p-8 space-y-4 text-sm text-destructive bg-destructive/10 rounded-md border border-destructive/20 my-4">
        <div className="flex items-center space-x-2">
          <AlertCircle className="w-4 h-4" />
          <span>{t('skillDetail.diffLoadError')}</span>
        </div>
        <Button
          variant="outline"
          size="sm"
          onClick={() => {
            if (sourceError) refetchSource()
            if (targetError) refetchTarget()
          }}
        >
          <RefreshCw className="w-4 h-4 mr-2" />
          {t('skillDetail.diffRetry')}
        </Button>
      </div>
    )
  }

  const oldValue = changeType === 'added' ? '' : (sourceContent || '')
  const newValue = changeType === 'removed' ? '' : (targetContent || '')

  return (
    <div className="mt-4 rounded-md border overflow-hidden">
      {isLargeFile(fileSize) && (
        <div className="bg-amber-500/10 text-amber-700 dark:text-amber-400 p-2 text-xs text-center border-b border-amber-500/20">
          {t('skillDetail.largeFileWarning')}
        </div>
      )}
      <div className="flex justify-end p-2 border-b bg-muted/30">
        <div className="flex bg-muted rounded-md p-0.5">
          <button
            onClick={() => setSplitView(false)}
            className={`px-3 py-1 text-xs font-medium rounded-sm ${!splitView ? 'bg-background shadow-sm' : 'text-muted-foreground'}`}
          >
            {t('skillDetail.diffViewUnified')}
          </button>
          <button
            onClick={() => setSplitView(true)}
            className={`px-3 py-1 text-xs font-medium rounded-sm ${splitView ? 'bg-background shadow-sm' : 'text-muted-foreground'}`}
          >
            {t('skillDetail.diffViewSplit')}
          </button>
        </div>
      </div>
      <div className="max-h-[600px] overflow-auto diff-viewer-container">
        <ReactDiffViewer
          oldValue={oldValue}
          newValue={newValue}
          splitView={splitView}
          useDarkTheme={document.documentElement.classList.contains('dark')}
          hideLineNumbers={false}
          showDiffOnly={false}
        />
      </div>
    </div>
  )
}
