import ReactMarkdown from 'react-markdown'
import rehypeHighlight from 'rehype-highlight'
import rehypeSanitize from 'rehype-sanitize'
import remarkGfm from 'remark-gfm'

interface MarkdownRendererProps {
  content: string
  className?: string
}

export function MarkdownRenderer({ content, className }: MarkdownRendererProps) {
  const containerClassName = [className, 'prose prose-sm max-w-none dark:prose-invert']
    .filter(Boolean)
    .join(' ')

  return (
    <div className={containerClassName}>
      <ReactMarkdown remarkPlugins={[remarkGfm]} rehypePlugins={[rehypeSanitize, rehypeHighlight]}>
        {content}
      </ReactMarkdown>
    </div>
  )
}
