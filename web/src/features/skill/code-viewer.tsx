import { useMemo } from 'react'
import { common, createLowlight } from 'lowlight'

const lowlight = createLowlight(common)

interface CodeViewerProps {
  content: string
  fileName: string
  className?: string
}

const EXT_LANG: Record<string, string> = {
  js: 'javascript', jsx: 'javascript', mjs: 'javascript', cjs: 'javascript',
  ts: 'typescript', tsx: 'typescript',
  py: 'python', java: 'java', json: 'json',
  yml: 'yaml', yaml: 'yaml', xml: 'xml', html: 'xml',
  sh: 'bash', bash: 'bash', zsh: 'bash',
  css: 'css', scss: 'css', less: 'less',
  sql: 'sql', rb: 'ruby', go: 'go', rs: 'rust',
  kt: 'kotlin', swift: 'swift', c: 'c', cpp: 'cpp', h: 'c',
  toml: 'ini', ini: 'ini', properties: 'ini',
}

function detectLanguage(fileName: string): string | undefined {
  const name = fileName.split('/').pop()?.toLowerCase() || ''
  if (name === 'dockerfile') return 'dockerfile'
  if (name === 'makefile') return 'makefile'
  const ext = name.split('.').pop() || ''
  return EXT_LANG[ext]
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function hastToHtml(node: any): string {
  if (!node) return ''
  if (node.type === 'text') return escapeHtml(node.value || '')
  if (node.type === 'element') {
    const cls = node.properties?.className?.join(' ')
    const attr = cls ? ` class="${cls}"` : ''
    const children = (node.children || []).map(hastToHtml).join('')
    return `<${node.tagName}${attr}>${children}</${node.tagName}>`
  }
  if (node.type === 'root' || node.children) {
    return (node.children || []).map(hastToHtml).join('')
  }
  return ''
}

function escapeHtml(str: string): string {
  return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

export function CodeViewer({ content, fileName, className }: CodeViewerProps) {
  const language = detectLanguage(fileName)

  const html = useMemo(() => {
    if (!language) return null
    try {
      return hastToHtml(lowlight.highlight(language, content))
    } catch {
      return null
    }
  }, [content, language])

  const lineCount = content.split('\n').length - (content.endsWith('\n') ? 1 : 0)

  return (
    <div className={['rounded-lg border overflow-hidden', className].filter(Boolean).join(' ')}>
      <div className="bg-muted px-4 py-2 text-sm font-medium font-mono border-b flex items-center justify-between">
        <span>{fileName}</span>
        <span className="text-xs text-muted-foreground">{lineCount} lines</span>
      </div>
      <div className="overflow-x-auto bg-[#0d1117] p-4 flex gap-4">
        <div className="text-right text-[#484f58] text-sm font-mono leading-6 select-none shrink-0">
          {Array.from({ length: lineCount }, (_, i) => (
            <div key={i}>{i + 1}</div>
          ))}
        </div>
        {html ? (
          <pre className="m-0 p-0 text-sm font-mono leading-6 text-[#e6edf3] min-w-0">
            <code dangerouslySetInnerHTML={{ __html: html }} />
          </pre>
        ) : (
          <pre className="m-0 p-0 text-sm font-mono leading-6 text-[#e6edf3] min-w-0">
            <code>{content}</code>
          </pre>
        )}
      </div>
    </div>
  )
}
