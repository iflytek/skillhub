import { useMemo } from 'react'
import { common, createLowlight } from 'lowlight'

// Create lowlight instance with common languages
const lowlight = createLowlight(common)
type HighlightedTree = ReturnType<(typeof lowlight)['highlight']>
type HighlightedNode = HighlightedTree | HighlightedTree['children'][number]

interface CodeRendererProps {
  code: string
  language: string | null
  className?: string
}

/**
 * Renders code with syntax highlighting using lowlight (highlight.js wrapper).
 * Reuses the same styling as Markdown code blocks for visual consistency.
 * Memoized to prevent re-highlighting on every render.
 */
export function CodeRenderer({ code, language, className }: CodeRendererProps) {
  // Cache syntax highlighting result
  const highlightedCode = useMemo(() => {
    try {
      if (language && lowlight.registered(language)) {
        const tree = lowlight.highlight(language, code, { prefix: 'hljs-' })
        return treeToHtml(tree)
      }
      return escapeHtml(code)
    } catch (error) {
      console.error('Syntax highlighting failed:', error)
      return escapeHtml(code)
    }
  }, [code, language])

  return (
    <div className={className}>
      {/* Simplified styling - removed gradient, blur, and shadow for better performance */}
      <div className="my-4 rounded-lg border border-border/60 bg-secondary/30">
        <div className="max-w-full overflow-x-auto rounded-lg bg-background px-4 py-3">
          <pre className="m-0 min-w-max bg-transparent p-0 text-sm leading-6">
            <code
              className="hljs"
              dangerouslySetInnerHTML={{ __html: highlightedCode }}
            />
          </pre>
        </div>
      </div>
    </div>
  )
}

/**
 * Converts lowlight AST tree to HTML string
 */
function treeToHtml(node: HighlightedNode): string {
  if (node.type === 'text') {
    return escapeHtml(node.value)
  }

  if (node.type === 'element') {
    const classNames = node.properties?.className
    const className = Array.isArray(classNames) ? classNames.join(' ') : ''
    const children = node.children.map(treeToHtml).join('')
    return `<span class="${className}">${children}</span>`
  }

  if (node.type === 'root') {
    return node.children.map(treeToHtml).join('')
  }

  return ''
}

/**
 * Escapes HTML special characters
 */
function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')
}
