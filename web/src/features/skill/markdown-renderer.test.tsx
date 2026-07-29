/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { MARKDOWN_IMAGE_CLASS_NAME, MarkdownRenderer } from './markdown-renderer'

afterEach(() => cleanup())

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
