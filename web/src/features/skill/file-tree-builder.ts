import type { SkillFile } from '@/api/types'

/**
 * Represents a node in the file tree structure.
 * Can be either a directory or a file node.
 */
export interface FileTreeNode {
  /** Unique identifier for the node */
  id: string
  /** Display name (filename or directory name) */
  name: string
  /** Full path from root */
  path: string
  /** Node type: directory or file */
  type: 'file' | 'directory'
  /** Original file metadata (only for files) */
  file?: SkillFile
  /** Child nodes (only for directories) */
  children?: FileTreeNode[]
  /** Nesting depth (0 for root level) */
  depth: number
}

/**
 * Sorts tree nodes: directories first (alphabetically), then files (alphabetically).
 */
function sortTreeNodes(nodes: FileTreeNode[]): FileTreeNode[] {
  return nodes.sort((a, b) => {
    // Directories come before files
    if (a.type === 'directory' && b.type === 'file') return -1
    if (a.type === 'file' && b.type === 'directory') return 1
    // Within same type, sort alphabetically
    return a.name.localeCompare(b.name)
  })
}

/**
 * Builds a hierarchical tree structure from a flat list of files.
 * Directories are sorted first, then files, both alphabetically.
 *
 * @param files - Flat array of SkillFile objects
 * @returns Array of root-level FileTreeNode objects
 */
export function buildFileTree(files: SkillFile[]): FileTreeNode[] {
  const root: FileTreeNode[] = []
  const directoryMap = new Map<string, FileTreeNode>()

  // Sort files by path for consistent ordering
  const sortedFiles = [...files].sort((a, b) => a.filePath.localeCompare(b.filePath))

  for (const file of sortedFiles) {
    const parts = file.filePath.split('/')
    let currentLevel = root
    let currentPath = ''

    for (let i = 0; i < parts.length; i++) {
      const part = parts[i]
      currentPath = currentPath ? `${currentPath}/${part}` : part
      const isFile = i === parts.length - 1

      if (isFile) {
        // Add file node
        currentLevel.push({
          id: currentPath,
          name: part,
          path: currentPath,
          type: 'file',
          file,
          depth: i,
        })
      } else {
        // Add or reuse directory node
        let dirNode = directoryMap.get(currentPath)

        if (!dirNode) {
          dirNode = {
            id: currentPath,
            name: part,
            path: currentPath,
            type: 'directory',
            children: [],
            depth: i,
          }
          directoryMap.set(currentPath, dirNode)
          currentLevel.push(dirNode)
        }

        currentLevel = dirNode.children!
      }
    }
  }

  // Sort each level: directories first, then files
  function sortRecursive(nodes: FileTreeNode[]) {
    sortTreeNodes(nodes)
    for (const node of nodes) {
      if (node.children) {
        sortRecursive(node.children)
      }
    }
  }

  sortRecursive(root)
  return root
}
