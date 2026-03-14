import ReactMarkdown from 'react-markdown'
import rehypeHighlight from 'rehype-highlight'
import rehypeSanitize from 'rehype-sanitize'
import remarkGfm from 'remark-gfm'

interface MarkdownRendererProps {
  content: string
  className?: string
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
        remarkPlugins={[remarkGfm]}
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
