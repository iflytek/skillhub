// @vitest-environment jsdom

import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ConfirmDialog } from './confirm-dialog'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}))

afterEach(cleanup)

describe('ConfirmDialog', () => {
  it('blocks duplicate confirmation while pending and allows a retry after a failed action', async () => {
    let finishFirstAttempt!: (result: boolean) => void
    const firstAttempt = new Promise<boolean>((resolve) => {
      finishFirstAttempt = resolve
    })
    const onConfirm = vi.fn<() => Promise<boolean | void>>()
      .mockReturnValueOnce(firstAttempt)
      .mockResolvedValueOnce(undefined)
    const onOpenChange = vi.fn()

    render(
      <ConfirmDialog
        open
        onOpenChange={onOpenChange}
        title="Revoke role"
        description="Confirm removal of this organization role."
        confirmText="Revoke"
        onConfirm={onConfirm}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: 'Revoke' }))
    fireEvent.click(screen.getByRole('button', { name: 'dialog.processing' }))
    expect(onConfirm).toHaveBeenCalledTimes(1)

    await act(async () => { finishFirstAttempt(false) })
    expect(screen.getByRole('dialog', { name: 'Revoke role' })).toBeTruthy()
    expect(onOpenChange).not.toHaveBeenCalled()

    fireEvent.click(screen.getByRole('button', { name: 'Revoke' }))
    await waitFor(() => expect(onOpenChange).toHaveBeenCalledExactlyOnceWith(false))
    expect(onConfirm).toHaveBeenCalledTimes(2)
  })
})
