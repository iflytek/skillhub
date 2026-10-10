import {
  Children,
  isValidElement,
  memo,
  useEffect,
  useMemo,
  useRef,
  useState,
  type MouseEvent,
  type ReactElement,
  type ReactNode,
} from 'react'
import ReactMarkdown from 'react-markdown'
import rehypeHighlight from 'rehype-highlight'
import rehypeSanitize from 'rehype-sanitize'
import remarkGfm from 'remark-gfm'
import { cn } from '@/shared/lib/utils'
import { remarkInferCodeLanguage } from './code-language'
import { stripMarkdownFrontmatter } from './markdown-frontmatter'

export const MARKDOWN_IMAGE_CLASS_NAME = 'h-auto max-w-full'

type MermaidApi = typeof import('mermaid').default

let mermaidPromise: Promise<MermaidApi> | undefined
let mermaidRenderQueue: Promise<void> = Promise.resolve()
let mermaidBlockSequence = 0

function loadMermaid(): Promise<MermaidApi> {
  mermaidPromise ??= import('mermaid').then(({ default: mermaid }) => {
    mermaid.initialize({
      startOnLoad: false,
      securityLevel: 'strict',
      suppressErrorRendering: true,
    })
    return mermaid
  })

  return mermaidPromise
}

function enqueueMermaidRender<T>(task: () => Promise<T>): Promise<T> {
  const render = mermaidRenderQueue.then(task, task)
  mermaidRenderQueue = render.then(
    () => undefined,
    () => undefined,
  )
  return render
}

function getTextContent(node: ReactNode): string {
  return Children.toArray(node)
    .map((child) => {
      if (typeof child === 'string' || typeof child === 'number') {
        return String(child)
      }

      if (isValidElement(child)) {
        return getTextContent((child as ReactElement<{ children?: ReactNode }>).props.children)
      }

      return ''
    })
    .join('')
}

function CodeBlock({ children, mermaidError }: { children: ReactNode; mermaidError?: boolean }) {
  return (
    <div
      className="my-4 rounded-lg border border-border/60 bg-secondary/30"
      data-mermaid-error={mermaidError || undefined}
    >
      <div className="max-w-full overflow-x-auto rounded-lg bg-background px-4 py-3">
        <pre className="m-0 min-w-max bg-transparent p-0 text-[13px] leading-6">{children}</pre>
      </div>
    </div>
  )
}

interface MermaidBlockProps {
  children: ReactNode
}

const MermaidBlock = memo(function MermaidBlock({ children }: MermaidBlockProps) {
  const source = useMemo(() => getTextContent(children), [children])
  const renderId = useRef(`mermaid-${++mermaidBlockSequence}`).current
  const diagramRef = useRef<HTMLDivElement>(null)
  const [svg, setSvg] = useState<string | null>(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    let mounted = true
    setSvg(null)
    setFailed(false)

    enqueueMermaidRender(async () => {
      const mermaid = await loadMermaid()
      return mermaid.render(renderId, source)
    })
      .then(({ svg: renderedSvg }) => {
        if (mounted) {
          setSvg(renderedSvg)
        }
      })
      .catch(() => {
        if (mounted) {
          setSvg(null)
          setFailed(true)
        }
      })

    return () => {
      mounted = false
    }
  }, [renderId, source])

  useEffect(() => {
    if (svg && diagramRef.current) {
      diagramRef.current.innerHTML = svg
    }
  }, [svg])

  if (!svg) {
    return <CodeBlock mermaidError={failed}>{children}</CodeBlock>
  }

  return (
    <div className="my-4 overflow-x-auto rounded-lg border border-border/60 bg-secondary/30" data-mermaid-diagram>
      <div ref={diagramRef} className="min-w-0 bg-background px-4 py-3 [&_svg]:h-auto [&_svg]:max-w-full" />
    </div>
  )
})

interface MarkdownRendererProps {
  content: string
  className?: string
  onLinkClick?: (href: string, event: MouseEvent<HTMLAnchorElement>) => void
}

/**
 * Renders markdown from skill packages using a constrained plugin stack.
 * Frontmatter is stripped before render because package metadata is surfaced in
 * dedicated UI sections and should not appear twice in the document body.
 * Memoized to prevent re-parsing on every render.
 */
function MarkdownRendererComponent({ content, className, onLinkClick }: MarkdownRendererProps) {
  const containerClassName = [
    className,
    'max-w-none break-words text-sm text-foreground/90 [overflow-wrap:anywhere]',
  ]
    .filter(Boolean)
    .join(' ')

  // Cache the normalized content to prevent re-parsing on every render
  const normalizedContent = useMemo(
    () => stripMarkdownFrontmatter(content),
    [content]
  )

  return (
    <div className={cn(containerClassName, 'notranslate')} translate="no">
      <ReactMarkdown
        remarkPlugins={[remarkGfm, remarkInferCodeLanguage]}
        rehypePlugins={[rehypeSanitize, [rehypeHighlight, { detect: true, ignoreMissing: true }]]}
        components={{
          p: ({ className: paragraphClassName, children, ...props }) => (
            <p className={cn('my-4 text-[15px] leading-8 text-foreground/85', paragraphClassName)} {...props}>
              {children}
            </p>
          ),
          a: ({ className: linkClassName, children, href, ...props }) => (
            <a
              className={cn(
                'font-medium text-primary underline decoration-primary/30 underline-offset-4 transition-colors hover:text-primary/80',
                linkClassName
              )}
              {...props}
              href={href}
              onClick={(event) => onLinkClick?.(href ?? '', event)}
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
          pre: ({ children }) => {
            const codeChild = Children.toArray(children).find(isValidElement) as
              | ReactElement<{ className?: string; children?: ReactNode }>
              | undefined
            const codeClassName = codeChild?.props.className

            if (codeClassName?.split(/\s+/).includes('language-mermaid')) {
              return <MermaidBlock>{codeChild}</MermaidBlock>
            }

            return <CodeBlock>{children}</CodeBlock>
          },
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
              <code className={cn(codeClassName, 'text-foreground/90')} {...props}>
                {children}
              </code>
            )
          },
          blockquote: ({ className: blockquoteClassName, children, ...props }) => (
            <blockquote
              className={cn(
                'my-4 border-l-4 border-l-primary/40 bg-secondary/20 px-4 py-3 text-foreground/80',
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
            <div className="my-4 overflow-hidden rounded-lg border border-border/60 bg-card/80">
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
            <img className={cn(MARKDOWN_IMAGE_CLASS_NAME, imageClassName)} alt={alt ?? ''} {...props} />
          ),
        }}
      >
        {normalizedContent}
      </ReactMarkdown>
    </div>
  )
}

/** Memoized so collapse/expand parent state does not re-parse large skill docs. */
export const MarkdownRenderer = memo(MarkdownRendererComponent)
