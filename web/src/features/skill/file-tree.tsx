import { useMemo, useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import { Folder } from 'lucide-react'
import type { SkillFile } from '@/api/types'
import { buildFileTree } from './file-tree-builder'
import { FileTreeNodeComponent } from './file-tree-node'
import type { FileTreeNode } from './file-tree-builder'

interface FileTreeProps {
  files: SkillFile[]
  onFileClick?: (node: FileTreeNode) => void
  /** When true, renders without the outer border/header (for embedding in a Card) */
  bare?: boolean
}

/**
 * Displays a hierarchical file tree with expandable directories.
 * Converts flat file list into tree structure and renders with proper nesting.
 */
export function FileTree({ files, onFileClick, bare }: FileTreeProps) {
  const { t } = useTranslation()

  // Cache tree structure to avoid rebuilding on every render
  const tree = useMemo(() => buildFileTree(files), [files])

  // Stable callback reference to prevent child re-renders
  const handleFileClick = useCallback(
    (node: FileTreeNode) => {
      if (node.type === 'file' && onFileClick) {
        onFileClick(node)
      }
    },
    [onFileClick]
  )

  const treeContent = (
    <div>
      {tree.map((node) => (
        <FileTreeNodeComponent
          key={node.id}
          node={node}
          onFileClick={handleFileClick}
          defaultExpanded={false}
        />
      ))}
    </div>
  )

  // Bare mode: no wrapper, for embedding inside a Card
  if (bare) {
    return treeContent
  }

  // Standalone mode: with border and header
  return (
    <div className="border border-border rounded-lg overflow-hidden bg-card">
      <div className="bg-muted/80 px-4 py-2.5 flex items-center justify-between border-b border-border/40">
        <div className="text-sm font-medium text-foreground flex items-center gap-2">
          <Folder className="h-4 w-4 text-muted-foreground" />
          {t('fileTree.title')}
        </div>
        <span className="text-xs text-muted-foreground bg-background/60 px-2 py-0.5 rounded-full">
          {files.length}
        </span>
      </div>
      {treeContent}
    </div>
  )
}
