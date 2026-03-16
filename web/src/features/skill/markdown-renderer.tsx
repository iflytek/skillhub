import ReactMarkdown from 'react-markdown'
import rehypeHighlight from 'rehype-highlight'
import rehypeSanitize from 'rehype-sanitize'
import remarkFrontmatter from 'remark-frontmatter'
import remarkGfm from 'remark-gfm'
import type { Root } from 'mdast'
import { visit } from 'unist-util-visit'

interface MarkdownRendererProps {
  content: string
  className?: string
}

function remarkStripFrontmatter() {
  return (tree: Root) => {
    visit(tree, (node, index, parent) => {
      if (!parent || index === undefined) {
        return
      }

      const nodeType = String(node.type)
      if (nodeType === 'yaml' || nodeType === 'toml') {
        parent.children.splice(index, 1)
      }
    })
  }
}

export function MarkdownRenderer({ content, className }: MarkdownRendererProps) {
  const containerClassName = [
    className,
    'prose prose-sm max-w-none break-words [overflow-wrap:anywhere] dark:prose-invert',
  ]
    .filter(Boolean)
    .join(' ')

  return (
    <div className={containerClassName}>
      <ReactMarkdown
        remarkPlugins={[remarkFrontmatter, remarkStripFrontmatter, remarkGfm]}
        rehypePlugins={[rehypeSanitize, rehypeHighlight]}
        components={{
          pre: ({ children }) => (
            <div className="max-w-full overflow-x-auto rounded-lg bg-muted/40 p-4">
              <pre className="m-0 min-w-max bg-transparent p-0">{children}</pre>
            </div>
          ),
          code: ({ className: codeClassName, children, ...props }) => {
            const isInline = !codeClassName?.includes('language-')

            if (isInline) {
              return (
                <code className="break-words rounded bg-muted px-1 py-0.5 text-sm" {...props}>
                  {children}
                </code>
              )
            }

            return (
              <code className={codeClassName} {...props}>
                {children}
              </code>
            )
          },
          table: ({ children }) => (
            <div className="max-w-full overflow-x-auto">
              <table>{children}</table>
            </div>
          ),
        }}
      >
        {content}
      </ReactMarkdown>
    </div>
  )
}
