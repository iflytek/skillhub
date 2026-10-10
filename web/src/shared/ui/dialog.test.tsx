/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  Dialog,
  DialogTrigger,
  DialogContent,
  DialogHeader,
  DialogFooter,
  DialogTitle,
  DialogDescription,
} from './dialog'

describe('Dialog components', () => {
  afterEach(() => {
    cleanup()
  })

  it('exports all dialog sub-components', () => {
    expect(Dialog).toBeDefined()
    expect(DialogTrigger).toBeDefined()
    expect(DialogContent).toBeDefined()
    expect(DialogHeader).toBeDefined()
    expect(DialogFooter).toBeDefined()
    expect(DialogTitle).toBeDefined()
    expect(DialogDescription).toBeDefined()
  })

  it('sets displayName on forwardRef components', () => {
    expect(DialogTrigger.displayName).toBe('DialogTrigger')
    expect(DialogContent.displayName).toBe('DialogContent')
    expect(DialogTitle.displayName).toBe('DialogTitle')
    expect(DialogDescription.displayName).toBe('DialogDescription')
  })

  it('sets displayName on function components', () => {
    expect(DialogHeader.displayName).toBe('DialogHeader')
    expect(DialogFooter.displayName).toBe('DialogFooter')
  })

  it('opens through the trigger by notifying onOpenChange', () => {
    const onOpenChange = vi.fn()

    render(
      <Dialog open={false} onOpenChange={onOpenChange}>
        <DialogTrigger>Open dialog</DialogTrigger>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Token created</DialogTitle>
            <DialogDescription>Copy the token now</DialogDescription>
          </DialogHeader>
        </DialogContent>
      </Dialog>
    )

    expect(screen.queryByRole('dialog')).toBeNull()
    fireEvent.click(screen.getByRole('button', { name: 'Open dialog' }))
    expect(onOpenChange).toHaveBeenCalledWith(true)
  })

  it('renders dialog content when open and closes via the close button', () => {
    const onOpenChange = vi.fn()

    render(
      <Dialog open onOpenChange={onOpenChange}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Confirm delete</DialogTitle>
            <DialogDescription>This cannot be undone</DialogDescription>
          </DialogHeader>
        </DialogContent>
      </Dialog>
    )

    expect(screen.getByRole('dialog')).toBeTruthy()
    expect(screen.getByText('Confirm delete')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Close' }))
    expect(onOpenChange).toHaveBeenCalledWith(false)
  })
})
