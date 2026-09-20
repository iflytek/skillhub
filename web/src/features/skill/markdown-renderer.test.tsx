/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { MARKDOWN_IMAGE_CLASS_NAME, MarkdownRenderer } from './markdown-renderer'

const { mermaidInitialize, mermaidModuleLoaded, mermaidRender } = vi.hoisted(() => ({
  mermaidInitialize: vi.fn(),
  mermaidModuleLoaded: vi.fn(),
  mermaidRender: vi.fn(),
}))

vi.mock('mermaid', () => {
  mermaidModuleLoaded()

  return {
    default: {
      initialize: mermaidInitialize,
      render: mermaidRender,
    },
  }
})

afterEach(() => {
  cleanup()
  mermaidInitialize.mockClear()
  mermaidModuleLoaded.mockClear()
  mermaidRender.mockReset()
})

describe('MARKDOWN_IMAGE_CLASS_NAME', () => {
  it('keeps markdown images at their intrinsic width while remaining responsive', () => {
    const classNames = MARKDOWN_IMAGE_CLASS_NAME.split(' ')

    expect(classNames).toContain('h-auto')
    expect(classNames).toContain('max-w-full')
    expect(classNames).not.toContain('w-full')
  })
})

describe('MarkdownRenderer links', () => {
  it('passes the raw markdown href to the optional link click handler', () => {
    const onLinkClick = vi.fn()

    render(<MarkdownRenderer content="[Usage](docs/usage.md)" onLinkClick={onLinkClick} />)
    fireEvent.click(screen.getByRole('link', { name: 'Usage' }))

    expect(onLinkClick).toHaveBeenCalledTimes(1)
    expect(onLinkClick.mock.calls[0][0]).toBe('docs/usage.md')
  })

  it('keeps links renderable without a click handler', () => {
    render(<MarkdownRenderer content="[Usage](docs/usage.md)" />)

    expect(screen.getByRole('link', { name: 'Usage' }).getAttribute('href')).toBe('docs/usage.md')
  })

  it('marks the document container as notranslate', () => {
    const { container } = render(<MarkdownRenderer content="Hello" />)
    const root = container.firstElementChild as HTMLElement

    expect(root.getAttribute('translate')).toBe('no')
    expect(root.classList.contains('notranslate')).toBe(true)
  })

  it('keeps the same content root when re-rendered with a stable onLinkClick', () => {
    const onLinkClick = vi.fn()
    const { container, rerender } = render(
      <MarkdownRenderer content="[Usage](docs/usage.md)" onLinkClick={onLinkClick} />,
    )
    const firstRoot = container.firstElementChild

    rerender(<MarkdownRenderer content="[Usage](docs/usage.md)" onLinkClick={onLinkClick} />)

    expect(container.firstElementChild).toBe(firstRoot)
  })
})

describe('MarkdownRenderer Mermaid blocks', () => {
  it('does not load Mermaid for documents without Mermaid blocks', () => {
    render(<MarkdownRenderer content="Plain Markdown" />)

    expect(mermaidModuleLoaded).not.toHaveBeenCalled()
  })

  it('renders Mermaid output outside the source code preformatted container', async () => {
    mermaidRender.mockResolvedValue({ svg: '<svg data-testid="mermaid-svg"><path /></svg>' })

    const { container } = render(
      <MarkdownRenderer content={'```mermaid\nflowchart TD\nA-->B\n```'} />,
    )

    await waitFor(() => expect(container.querySelector('[data-testid="mermaid-svg"]')).toBeTruthy())

    expect(mermaidInitialize).toHaveBeenCalledWith({ startOnLoad: false, securityLevel: 'strict' })
    expect(container.querySelector('[data-testid="mermaid-svg"]')?.closest('pre')).toBeNull()
  })

  it('keeps the original Mermaid source when rendering fails', async () => {
    mermaidRender.mockRejectedValue(new Error('invalid Mermaid syntax'))

    const { container } = render(
      <MarkdownRenderer content={'```mermaid\nnot a valid diagram\n```'} />,
    )

    await waitFor(() => expect(mermaidRender).toHaveBeenCalled())

    expect(container.querySelector('pre code')?.textContent).toContain('not a valid diagram')
  })

  it('assigns different render IDs to Mermaid blocks in the same document', async () => {
    mermaidRender.mockImplementation(async (id: string) => ({ svg: `<svg data-render-id="${id}" />` }))

    render(
      <MarkdownRenderer
        content={'```mermaid\nflowchart TD\nA-->B\n```\n\n```mermaid\nflowchart LR\nC-->D\n```'}
      />,
    )

    await waitFor(() => expect(mermaidRender).toHaveBeenCalledTimes(2))

    const ids = mermaidRender.mock.calls.map(([id]) => id)
    expect(new Set(ids).size).toBe(2)
  })

  it('continues rendering later blocks after an earlier Mermaid render fails', async () => {
    mermaidRender
      .mockRejectedValueOnce(new Error('invalid Mermaid syntax'))
      .mockResolvedValueOnce({ svg: '<svg data-testid="second-mermaid-svg" />' })

    const { container } = render(
      <MarkdownRenderer
        content={'```mermaid\ninvalid\n```\n\n```mermaid\nflowchart LR\nA-->B\n```'}
      />,
    )

    await waitFor(() => expect(mermaidRender).toHaveBeenCalledTimes(2))
    await waitFor(() => expect(container.querySelector('[data-testid="second-mermaid-svg"]')).toBeTruthy())
  })

  it('keeps ordinary fenced code in the existing preformatted container', () => {
    const { container } = render(
      <MarkdownRenderer content={'```typescript\nconst answer = 42\n```'} />,
    )

    const code = container.querySelector('pre code')

    expect(code).not.toBeNull()
    expect(code?.textContent).toContain('const answer = 42')
  })
})
