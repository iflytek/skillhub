import { Copy, Download, X } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Dialog, DialogContent } from '@/shared/ui/dialog'
import { Button } from '@/shared/ui/button'
import { MarkdownRenderer } from './markdown-renderer'
import { getFileTypeLabel, canPreviewFile } from './file-type-utils'
import type { FileTreeNode } from './file-tree-builder'

interface FilePreviewDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  node: FileTreeNode | null
  content: string | null
  isLoading: boolean
  error: Error | null
  onDownload: () => void
}

/**
 * Dialog component for previewing file contents.
 * Supports Markdown rendering and plain text display.
 * Shows appropriate messages for non-previewable files.
 */
export function FilePreviewDialog({
  open,
  onOpenChange,
  node,
  content,
  isLoading,
  error,
  onDownload,
}: FilePreviewDialogProps) {
  const { t } = useTranslation()

  if (!node) return null

  const fileTypeLabel = getFileTypeLabel(node.name)
  const previewCheck = canPreviewFile(node.name, node.file?.fileSize || 0)
  const isMarkdown = ['md', 'mdx', 'markdown'].includes(fileTypeLabel)

  /**
   * Copies file content to clipboard.
   */
  const handleCopy = async () => {
    if (content) {
      await navigator.clipboard.writeText(content)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      {/* Hide the default close button; override base width to fill most of the viewport */}
      <DialogContent className="w-[min(calc(100vw-2rem),72rem)] max-h-[90vh] p-0 gap-0 flex flex-col [&>button]:hidden">
        {/* Header: file name, type badge, and action buttons (including close) */}
        <div className="flex items-center justify-between px-5 py-3 border-b border-border/40 bg-muted/30 flex-shrink-0">
          <div className="flex items-center gap-3 min-w-0 flex-1">
            <span className="font-mono text-sm font-medium text-foreground truncate">{node.name}</span>
            <span className="px-2 py-0.5 text-xs rounded border border-border/60 bg-background/60 text-muted-foreground flex-shrink-0">
              {fileTypeLabel}
            </span>
          </div>
          <div className="flex items-center gap-1 flex-shrink-0">
            {content && (
              <Button
                variant="ghost"
                size="icon"
                className="h-8 w-8 rounded-lg opacity-60 hover:opacity-100 hover:bg-accent transition-all duration-200 hover:scale-110 active:scale-95"
                onClick={handleCopy}
                title={t('filePreview.copy')}
              >
                <Copy className="h-4 w-4 transition-transform duration-200 active:scale-125" />
              </Button>
            )}
            <Button
              variant="ghost"
              size="icon"
              className="h-8 w-8 rounded-lg opacity-60 hover:opacity-100 hover:bg-accent transition-all duration-200 hover:scale-110 active:scale-95"
              onClick={onDownload}
              title={t('filePreview.downloadHint', { path: node.path })}
            >
              <Download className="h-4 w-4 transition-transform duration-200 hover:translate-y-0.5" />
            </Button>
            <Button
              variant="ghost"
              size="icon"
              className="h-8 w-8 rounded-lg opacity-60 hover:opacity-100 hover:bg-destructive/10 hover:text-destructive transition-all duration-200 hover:rotate-90 active:scale-95"
              onClick={() => onOpenChange(false)}
              title={t('filePreview.close')}
            >
              <X className="h-4 w-4" />
            </Button>
          </div>
        </div>

        {/* Content area: loading, error, non-previewable, or actual content */}
        <div className="overflow-auto p-6 bg-card flex-1 min-h-0">
          {isLoading ? (
            <div className="flex items-center justify-center py-12">
              <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" />
            </div>
          ) : error ? (
            <div className="text-center py-12">
              <p className="text-sm font-medium text-foreground">{t('filePreview.loadError')}</p>
              <p className="text-sm text-muted-foreground mt-2">{error.message}</p>
            </div>
          ) : !previewCheck.canPreview ? (
            <div className="text-center py-12 space-y-4">
              <p className="text-sm font-medium text-foreground">
                {previewCheck.reason === 'too-large'
                  ? t('filePreview.tooLarge')
                  : previewCheck.reason === 'binary'
                    ? t('filePreview.binaryFile')
                    : t('filePreview.unsupported')}
              </p>
              <Button onClick={onDownload}>{t('filePreview.downloadHint', { path: node.path })}</Button>
            </div>
          ) : content && isMarkdown ? (
            <MarkdownRenderer content={content} />
          ) : content ? (
            <pre className="text-sm font-mono whitespace-pre-wrap break-words">
              <code>{content}</code>
            </pre>
          ) : null}
        </div>

        {/* Footer: shows the full file path */}
        <div className="px-5 py-2 border-t border-border/40 bg-muted/20 flex-shrink-0">
          <span className="text-xs text-muted-foreground font-mono">{node.path}</span>
        </div>
      </DialogContent>
    </Dialog>
  )
}
