import { useTranslation } from 'react-i18next'
import type { SkillFile } from '@/api/types'
import { buildFileTree } from './file-tree-builder'
import { FileTreeNodeComponent } from './file-tree-node'
import type { FileTreeNode } from './file-tree-builder'

interface FileTreeProps {
  files: SkillFile[]
  onFileClick?: (node: FileTreeNode) => void
}

/**
 * Displays a hierarchical file tree with expandable directories.
 * Converts flat file list into tree structure and renders with proper nesting.
 */
export function FileTree({ files, onFileClick }: FileTreeProps) {
  const { t } = useTranslation()
  const tree = buildFileTree(files)

  const handleFileClick = (node: FileTreeNode) => {
    if (node.type === 'file' && onFileClick) {
      onFileClick(node)
    }
  }

  return (
    <div className="border border-border rounded-lg overflow-hidden bg-card">
      <div className="bg-muted/80 px-4 py-2.5 flex items-center justify-between border-b border-border/40">
        <div className="text-sm font-medium text-foreground flex items-center gap-2">
          {t('fileTree.title')}
        </div>
        <span className="text-xs text-muted-foreground bg-background/60 px-2 py-0.5 rounded-full">
          {files.length}
        </span>
      </div>
      <div className="divide-y divide-border/20">
        {tree.map((node) => (
          <FileTreeNodeComponent
            key={node.id}
            node={node}
            onFileClick={handleFileClick}
            defaultExpanded={node.depth === 0 && node.type === 'directory'}
          />
        ))}
      </div>
    </div>
  )
}
