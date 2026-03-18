import ReactMarkdown from 'react-markdown'
import rehypeHighlight from 'rehype-highlight'
import rehypeSanitize from 'rehype-sanitize'
import remarkGfm from 'remark-gfm'
import { cn } from '@/shared/lib/utils'
import { remarkInferCodeLanguage } from './code-language'
import { stripMarkdownFrontmatter } from './markdown-frontmatter'

interface MarkdownRendererProps {
  content: string
  className?: string
}

export function MarkdownRenderer({ content, className }: MarkdownRendererProps) {
  const containerClassName = [
    className,
    'max-w-none break-words text-sm text-foreground/90 [overflow-wrap:anywhere]',
  ]
    .filter(Boolean)
    .join(' ')
  const normalizedContent = stripMarkdownFrontmatter(content)

  return (
    <div className={containerClassName}>
      <ReactMarkdown
        remarkPlugins={[remarkGfm, remarkInferCodeLanguage]}
        rehypePlugins={[rehypeSanitize, [rehypeHighlight, { detect: true, ignoreMissing: true }]]}
        components={{
          p: ({ className: paragraphClassName, children, ...props }) => (
            <p className={cn('my-4 text-[15px] leading-8 text-foreground/85', paragraphClassName)} {...props}>
              {children}
            </p>
          ),
          a: ({ className: linkClassName, children, ...props }) => (
            <a
              className={cn(
                'font-medium text-primary underline decoration-primary/30 underline-offset-4 transition-colors hover:text-primary/80',
                linkClassName
              )}
              {...props}
            >
              {children}
            </a>
          ),
          strong: ({ className: strongClassName, children, ...props }) => (
            <strong className={cn('font-semibold text-foreground', strongClassName)} {...props}>
              {children}
            </strong>
          ),
          h1: ({ className: headingClassName, children, ...props }) => (
            <h1
              className={cn(
                'scroll-mt-24 mb-6 border-b border-border/50 pb-4 font-heading text-3xl font-bold tracking-tight text-foreground',
                headingClassName
              )}
              {...props}
            >
              {children}
            </h1>
          ),
          h2: ({ className: headingClassName, children, ...props }) => (
            <h2
              className={cn(
                'scroll-mt-24 mt-10 mb-4 border-b border-border/40 pb-3 font-heading text-2xl font-semibold tracking-tight text-foreground',
                headingClassName
              )}
              {...props}
            >
              {children}
            </h2>
          ),
          h3: ({ className: headingClassName, children, ...props }) => (
            <h3
              className={cn(
                'scroll-mt-24 mt-8 mb-3 font-heading text-xl font-semibold tracking-tight text-foreground',
                headingClassName
              )}
              {...props}
            >
              {children}
            </h3>
          ),
          ul: ({ className: listClassName, children, ...props }) => (
            <ul className={cn('my-5 list-disc space-y-2 pl-6 text-foreground/85 marker:text-primary/60', listClassName)} {...props}>
              {children}
            </ul>
          ),
          ol: ({ className: listClassName, children, ...props }) => (
            <ol className={cn('my-5 list-decimal space-y-2 pl-6 text-foreground/85 marker:text-primary/60', listClassName)} {...props}>
              {children}
            </ol>
          ),
          li: ({ className: itemClassName, children, ...props }) => (
            <li className={cn('pl-1 leading-7', itemClassName)} {...props}>
              {children}
            </li>
          ),
          pre: ({ children }) => (
            <div className="my-6 rounded-2xl border border-border/60 bg-gradient-to-br from-secondary/45 via-background to-secondary/20 p-1 shadow-sm">
              <div className="max-w-full overflow-x-auto rounded-xl bg-background/80 px-4 py-4 backdrop-blur-sm">
                <pre className="m-0 min-w-max bg-transparent p-0 text-[13px] leading-6">{children}</pre>
              </div>
            </div>
          ),
          code: ({ className: codeClassName, children, ...props }) => {
            const isInline = !codeClassName?.includes('language-')

            if (isInline) {
              return (
                <code
                  className="break-words rounded-md border border-border/40 bg-secondary/45 px-1.5 py-0.5 text-[0.9em] font-medium text-foreground/95"
                  {...props}
                >
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
          blockquote: ({ className: blockquoteClassName, children, ...props }) => (
            <blockquote
              className={cn(
                'relative my-6 overflow-hidden rounded-r-xl border-l-4 border-l-primary/35 bg-secondary/30 px-5 py-4 text-foreground/80 shadow-sm',
                blockquoteClassName
              )}
              {...props}
            >
              {children}
            </blockquote>
          ),
          hr: ({ className: hrClassName, ...props }) => (
            <hr className={cn('my-10 mx-auto w-full max-w-full border-border/50', hrClassName)} {...props} />
          ),
          table: ({ children }) => (
            <div className="my-6 overflow-hidden rounded-2xl border border-border/80 bg-card/80 shadow-sm">
              <div className="max-w-full overflow-x-auto">
                <table className="m-0 min-w-full border-separate border-spacing-0 text-sm">{children}</table>
              </div>
            </div>
          ),
          thead: ({ className: sectionClassName, children, ...props }) => (
            <thead className={cn('bg-secondary/55', sectionClassName)} {...props}>
              {children}
            </thead>
          ),
          tbody: ({ className: sectionClassName, children, ...props }) => (
            <tbody className={cn('[&_tr:nth-child(even)]:bg-secondary/12', sectionClassName)} {...props}>
              {children}
            </tbody>
          ),
          tr: ({ className: rowClassName, children, ...props }) => (
            <tr className={cn('transition-colors hover:bg-secondary/25', rowClassName)} {...props}>
              {children}
            </tr>
          ),
          th: ({ className: cellClassName, children, ...props }) => (
            <th
              className={cn(
                'border-b border-r border-border/70 px-4 py-3 text-left text-xs font-semibold uppercase tracking-[0.18em] text-muted-foreground last:border-r-0',
                cellClassName
              )}
              {...props}
            >
              {children}
            </th>
          ),
          td: ({ className: cellClassName, children, ...props }) => (
            <td
              className={cn(
                'border-b border-r border-border/60 px-4 py-3 align-top text-foreground/85 last:border-r-0',
                cellClassName
              )}
              {...props}
            >
              {children}
            </td>
          ),
          img: ({ className: imageClassName, alt, ...props }) => (
            <img className={cn('w-full', imageClassName)} alt={alt ?? ''} {...props} />
          ),
        }}
      >
        {normalizedContent}
      </ReactMarkdown>
    </div>
  )
}
