/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { UserAvatar } from './user-avatar'

describe('UserAvatar', () => {
  afterEach(() => {
    cleanup()
  })

  it('renders the image when a src is provided', () => {
    render(<UserAvatar src="https://example.com/avatar.png" name="Ada Lovelace" />)
    const img = screen.getByRole('img', { name: 'Ada Lovelace' })
    expect(img.tagName).toBe('IMG')
    expect(img.getAttribute('src')).toBe('https://example.com/avatar.png')
  })

  it('renders initials when no src is provided', () => {
    render(<UserAvatar name="Ada Lovelace" />)
    const fallback = screen.getByRole('img', { name: 'Ada Lovelace' })
    expect(fallback.tagName).toBe('DIV')
    expect(fallback.textContent).toBe('AL')
  })

  it('falls back to initials when the image fails to load', () => {
    render(<UserAvatar src="https://example.com/broken.png" name="Grace Hopper" />)
    const img = screen.getByRole('img', { name: 'Grace Hopper' })

    fireEvent.error(img)

    const fallback = screen.getByRole('img', { name: 'Grace Hopper' })
    expect(fallback.tagName).toBe('DIV')
    expect(fallback.textContent).toBe('GH')
  })

  it('derives a two-letter initial from a single-word name', () => {
    render(<UserAvatar name="madonna" />)
    expect(screen.getByRole('img', { name: 'madonna' }).textContent).toBe('MA')
  })
})
