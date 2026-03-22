import { useState } from 'react'
import { ChevronRight, ChevronDown, Folder, FolderOpen, FileText, FileCode, File } from 'lucide-react'
import { cn } from '@/shared/lib/utils'
import type { FileTreeNode } from './file-tree-builder'
import { getFileIcon } from './file-type-utils'

interface FileTreeNodeProps {
  node: FileTreeNode
  onFileClick: (node: FileTreeNode) => void
  defaultExpanded?: boolean
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
 */
export function FileTreeNodeComponent({ node, onFileClick, defaultExpanded = false }: FileTreeNodeProps) {
  const [isExpanded, setIsExpanded] = useState(defaultExpanded)

  // Render file node
  if (node.type === 'file') {
    const IconComponent = getIconComponent(getFileIcon(node.name))

    return (
      <div
        className="flex items-center justify-between px-3 py-2 hover:bg-accent/10 cursor-pointer transition-colors group"
        style={{ paddingLeft: `${node.depth * 16 + 12}px` }}
        onClick={() => onFileClick(node)}
      >
        <div className="flex items-center gap-2 min-w-0 flex-1">
          <IconComponent className="h-4 w-4 text-muted-foreground flex-shrink-0" />
          <span className="font-mono text-sm text-foreground truncate">{node.name}</span>
        </div>
        {node.file && (
          <span className="text-xs text-muted-foreground opacity-0 group-hover:opacity-100 transition-opacity flex-shrink-0 ml-2">
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
        className="flex items-center gap-2 px-3 py-2 hover:bg-muted/50 cursor-pointer transition-colors"
        style={{ paddingLeft: `${node.depth * 16 + 12}px` }}
        onClick={() => setIsExpanded(!isExpanded)}
      >
        {isExpanded ? (
          <ChevronDown className="h-4 w-4 text-muted-foreground flex-shrink-0" />
        ) : (
          <ChevronRight className="h-4 w-4 text-muted-foreground flex-shrink-0" />
        )}
        {isExpanded ? (
          <FolderOpen className="h-4 w-4 text-amber-500 flex-shrink-0" />
        ) : (
          <Folder className="h-4 w-4 text-amber-500 flex-shrink-0" />
        )}
        <span className="font-mono text-sm text-foreground truncate">{node.name}</span>
      </div>
      {isExpanded && node.children && (
        <div>
          {node.children.map((child) => (
            <FileTreeNodeComponent
              key={child.id}
              node={child}
              onFileClick={onFileClick}
              defaultExpanded={child.depth === 1}
            />
          ))}
        </div>
      )}
    </div>
  )
}
