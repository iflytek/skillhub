import { common, createLowlight } from 'lowlight'

// Create lowlight instance with common languages
const lowlight = createLowlight(common)

interface CodeRendererProps {
  code: string
  language: string | null
  className?: string
}

/**
 * Renders code with syntax highlighting using lowlight (highlight.js wrapper).
 * Reuses the same styling as Markdown code blocks for visual consistency.
 */
export function CodeRenderer({ code, language, className }: CodeRendererProps) {
  let highlightedCode: string

  try {
    if (language && lowlight.registered(language)) {
      // Highlight with specified language
      const tree = lowlight.highlight(language, code, { prefix: 'hljs-' })
      highlightedCode = treeToHtml(tree)
    } else {
      // Fallback to plain text (no highlighting)
      highlightedCode = escapeHtml(code)
    }
  } catch (error) {
    // If highlighting fails, escape HTML and display as plain text
    console.error('Syntax highlighting failed:', error)
    highlightedCode = escapeHtml(code)
  }

  return (
    <div className={className}>
      {/* Reuse the same wrapper styling as Markdown code blocks */}
      <div className="my-6 rounded-2xl border border-border/60 bg-gradient-to-br from-secondary/45 via-background to-secondary/20 p-1 shadow-sm">
        <div className="max-w-full overflow-x-auto rounded-xl bg-background/80 px-4 py-4 backdrop-blur-sm">
          <pre className="m-0 min-w-max bg-transparent p-0 text-[13px] leading-6">
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
function treeToHtml(node: any): string {
  if (node.type === 'text') {
    return escapeHtml(node.value)
  }

  if (node.type === 'element') {
    const className = node.properties?.className?.join(' ') || ''
    const children = node.children?.map(treeToHtml).join('') || ''
    return `<span class="${className}">${children}</span>`
  }

  if (node.type === 'root') {
    return node.children?.map(treeToHtml).join('') || ''
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
