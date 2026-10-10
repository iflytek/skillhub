import { renderToStaticMarkup } from 'react-dom/server'
import { createElement, Fragment } from 'react'
import type { ComponentProps, HTMLAttributes, ReactNode } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { FilePreviewDialog } from './file-preview-dialog'
import type { FileTreeNode } from './file-tree-builder'

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => key,
      i18n: { language: 'en' },
    }),
  }
})

vi.mock('@/shared/ui/dialog', () => ({
  Dialog: ({ children }: { children: ReactNode }) => createElement(Fragment, null, children),
  DialogContent: ({ children, ...props }: HTMLAttributes<HTMLDivElement>) => createElement('div', props, children),
}))

vi.mock('@/features/skill/markdown-renderer', () => ({
  MarkdownRenderer: ({ content }: { content: string }) => createElement('div', { 'data-testid': 'markdown-renderer' }, content),
}))

vi.mock('@/features/skill/code-renderer', () => ({
  CodeRenderer: ({ code, language }: { code: string, language: string | null }) =>
    createElement('div', { 'data-testid': 'code-renderer' }, `${language ?? 'plain'}:${code}`),
}))

vi.mock('@/shared/lib/toast', () => ({
  toast: {
    success: vi.fn(),
  },
}))

function createNode(overrides: Partial<FileTreeNode> = {}): FileTreeNode {
  return {
    id: 'root/demo.md',
    name: 'demo.md',
    path: 'root/demo.md',
    type: 'file',
    depth: 1,
    file: { fileSize: 128 } as FileTreeNode['file'],
    ...overrides,
  }
}

function renderDialog(overrides: Partial<ComponentProps<typeof FilePreviewDialog>> = {}) {
  const props: ComponentProps<typeof FilePreviewDialog> = {
    open: true,
    onOpenChange: vi.fn(),
    node: createNode(),
    content: '# Demo\n\ncontent',
    isLoading: false,
    error: null,
    onDownload: vi.fn(),
    ...overrides,
  }

  return renderToStaticMarkup(createElement(FilePreviewDialog, props))
}

describe('FilePreviewDialog', () => {
  it('renders nothing when the node is missing', () => {
    const html = renderDialog({ node: null })

    expect(html).toBe('')
  })

  it('renders the loading state and hides the copy button when there is no content', () => {
    const html = renderDialog({
      content: null,
      isLoading: true,
      node: createNode({ name: 'loading.txt', path: 'files/loading.txt', file: { fileSize: 12 } as FileTreeNode['file'] }),
    })

    expect(html).toContain('loading.txt')
    expect(html).toContain('filePreview.downloadHint')
    expect(html).toContain('filePreview.close')
    expect(html).not.toContain('filePreview.copy')
    expect(html).toContain('animate-spin')
  })

  it('renders the error state with the server message', () => {
    const html = renderDialog({
      content: null,
      error: new Error('boom'),
      node: createNode({ name: 'error.txt', path: 'files/error.txt', file: { fileSize: 12 } as FileTreeNode['file'] }),
    })

    expect(html).toContain('filePreview.loadError')
    expect(html).toContain('boom')
  })

  it.each([
    ['too-large', 'oversize.md', 1024 * 1024 + 1, 'filePreview.tooLarge'],
    ['binary', 'image.png', 128, 'filePreview.binaryFile'],
    ['unsupported', 'archive.foo', 128, 'filePreview.unsupported'],
  ] as const)('renders the %s fallback message for non-previewable files', (_, name, fileSize, messageKey) => {
    const html = renderDialog({
      content: null,
      node: createNode({
        name,
        path: `files/${name}`,
        file: { fileSize } as FileTreeNode['file'],
      }),
    })

    expect(html).toContain(messageKey)
    expect(html).toContain('filePreview.downloadHint')
  })

  it('renders markdown content and shows the copy button when content is available', () => {
    const html = renderDialog({
      node: createNode({
        name: 'README.md',
        path: 'docs/README.md',
        file: { fileSize: 256 } as FileTreeNode['file'],
      }),
      content: '# Heading\n\nMarkdown body',
    })

    expect(html).toContain('data-testid="markdown-renderer"')
    expect(html).toContain('# Heading')
    expect(html).toContain('Markdown body')
    expect(html).toContain('filePreview.copy')
    expect(html).toContain('filePreview.downloadHint')
  })

  it('renders syntax-highlighted code for supported source files', () => {
    const html = renderDialog({
      node: createNode({
        name: 'script.ts',
        path: 'src/script.ts',
        file: { fileSize: 128 } as FileTreeNode['file'],
      }),
      content: 'const answer = 42',
    })

    expect(html).toContain('data-testid="code-renderer"')
    expect(html).toContain('typescript:const answer = 42')
  })

  it('renders plain text when the file has no highlight language', () => {
    const html = renderDialog({
      node: createNode({
        name: 'notes',
        path: 'docs/notes',
        file: { fileSize: 128 } as FileTreeNode['file'],
      }),
      content: 'plain text content',
    })

    expect(html).not.toContain('data-testid="code-renderer"')
    expect(html).not.toContain('data-testid="markdown-renderer"')
    expect(html).toContain('<pre class="text-sm font-mono whitespace-pre-wrap break-words"><code>plain text content</code></pre>')
  })
})
