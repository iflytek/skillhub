import { useState, memo, useMemo } from 'react'
import { ChevronRight, ChevronDown, Folder, FolderOpen, FileText, FileCode, File } from 'lucide-react'
import type { FileTreeNode } from './file-tree-builder'
import { getFileIcon } from './file-type-utils'
import { cn } from '@/shared/lib/utils'

interface FileTreeNodeProps {
  analyticsId?: string
  node: FileTreeNode
  onFileClick: (node: FileTreeNode) => void
  defaultExpanded?: boolean
  compact?: boolean
}

/**
 * Maps icon name string to actual Lucide icon component.
 */
function getIconComponent(iconName: string) {
  const icons: Record<string, typeof File> = {
    FileText,
    FileCode,
    File,
  }
  return icons[iconName] || File
}

/**
 * Formats file size in bytes to human-readable format.
 */
function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

/**
 * Recursive file tree node component.
 * Renders either a file or directory node with expand/collapse functionality.
 * Memoized to prevent unnecessary re-renders when parent updates.
 */
export const FileTreeNodeComponent = memo(function FileTreeNodeComponent({
  node,
  onFileClick,
  defaultExpanded = false,
  compact = false,
  analyticsId,
}: FileTreeNodeProps) {
  const [isExpanded, setIsExpanded] = useState(defaultExpanded)

  // Cache icon computation
  const IconComponent = useMemo(
    () => getIconComponent(getFileIcon(node.name)),
    [node.name]
  )

  // Render file node
  if (node.type === 'file') {
    return (
      <div
        data-analytics-id={analyticsId}
        className={cn('flex items-center justify-between px-3 py-2 hover:bg-accent/10 cursor-pointer transition-colors group', compact && 'py-1.5')}
        style={{ paddingLeft: `${node.depth * 16 + 12}px` }}
        onClick={() => onFileClick(node)}
      >
        <div className="flex items-center gap-2 min-w-0 flex-1">
          <IconComponent className={cn('h-4 w-4 text-muted-foreground flex-shrink-0', compact && 'h-3.5 w-3.5')} />
          <span className={cn('font-mono text-sm text-foreground truncate', compact && 'text-xs')}>{node.name}</span>
        </div>
        {node.file && (
          <span className="text-xs text-muted-foreground flex-shrink-0 ml-2">
            {formatFileSize(node.file.fileSize)}
          </span>
        )}
      </div>
    )
  }

  // Render directory node
  return (
    <div>
      <div
        data-analytics-id={analyticsId ? 'skill.directory.toggle' : undefined}
        className={cn('flex items-center gap-2 px-3 py-2 hover:bg-muted/50 cursor-pointer transition-colors', compact && 'py-1.5')}
        style={{ paddingLeft: `${node.depth * 16 + 12}px` }}
        onClick={() => setIsExpanded(!isExpanded)}
      >
        {isExpanded ? (
          <ChevronDown className={cn('h-4 w-4 text-muted-foreground flex-shrink-0', compact && 'h-3.5 w-3.5')} />
        ) : (
          <ChevronRight className={cn('h-4 w-4 text-muted-foreground flex-shrink-0', compact && 'h-3.5 w-3.5')} />
        )}
        {isExpanded ? (
          <FolderOpen className={cn('h-4 w-4 text-amber-500 flex-shrink-0', compact && 'h-3.5 w-3.5')} />
        ) : (
          <Folder className={cn('h-4 w-4 text-amber-500 flex-shrink-0', compact && 'h-3.5 w-3.5')} />
        )}
        <span className={cn('font-mono text-sm text-foreground truncate', compact && 'text-xs')}>{node.name}</span>
      </div>
      {isExpanded && node.children && (
        <div>
          {node.children.map((child) => (
            <FileTreeNodeComponent
              analyticsId={analyticsId}
              key={child.id}
              node={child}
              onFileClick={onFileClick}
              defaultExpanded={false}
              compact={compact}
            />
          ))}
        </div>
      )}
    </div>
  )
})
