// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { useMutation } from '@tanstack/react-query'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { App } from '@/app/providers'
import { skillLifecycleApi } from '@/api/client'
import { ApiError } from '@/shared/lib/api-error'
import { toast } from '@/shared/lib/toast'
import { useDeleteSkill } from './use-skill-queries'

vi.mock('@/shared/hooks/use-site-presentation', async (original) => {
  const actual = await original<typeof import('@/shared/hooks/use-site-presentation')>()
  return { ...actual, useSitePresentation: () => ({ settings: actual.defaultSitePresentation(), isPending: false, isFetched: true, isError: false, refetch: vi.fn() }) }
})

vi.mock('@/app/router', () => ({ router: {} }))
vi.mock('@tanstack/react-router', () => ({ RouterProvider: () => <DeleteHarness /> }))
vi.mock('@/shared/components/toaster', () => ({ Toaster: () => null }))
vi.mock('@/shared/lib/toast', () => ({ toast: { error: vi.fn(), success: vi.fn() } }))

const blockedError = new ApiError('revoke global publication first', 409, 'revoke global publication first')

// Exercise the real application MutationCache. Like skill-detail, the caller
// reports success only; the global handler owns errors and authentication redirects.
function DeleteHarness() {
  const deletion = useDeleteSkill()
  const unrelated = useMutation({ mutationFn: async () => { throw blockedError } })
  const deleteSkill = async () => {
    try {
      await deletion.mutateAsync({ namespace: 'team', slug: 'promoted', ownerId: 'owner' })
      toast.success('deleted')
    } catch {
      // The click boundary consumes rejection; MutationCache already handled it.
    }
  }
  return <>
    <button onClick={() => { void deleteSkill() }} disabled={deletion.isPending}>Delete</button>
    <button onClick={() => { void unrelated.mutateAsync().catch(() => undefined) }}>Other request</button>
  </>
}

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.clearAllMocks()
  vi.unstubAllGlobals()
})

describe('skill deletion feedback ownership', () => {
  it('shows one actionable error per failed deletion, including a new retry', async () => {
    const request = vi.spyOn(skillLifecycleApi, 'deleteSkill').mockRejectedValue(blockedError)
    render(<App />)
    fireEvent.click(screen.getByRole('button', { name: 'Delete' }))
    await waitFor(() => expect(toast.error).toHaveBeenCalledTimes(1))
    expect(toast.error).toHaveBeenLastCalledWith(blockedError.serverMessage)
    await waitFor(() => expect(screen.getByRole('button', { name: 'Delete' }).hasAttribute('disabled')).toBe(false))
    fireEvent.click(screen.getByRole('button', { name: 'Delete' }))
    await waitFor(() => expect(request).toHaveBeenCalledTimes(2))
    await waitFor(() => expect(toast.error).toHaveBeenCalledTimes(2))
    expect(toast.success).not.toHaveBeenCalled()
  })

  it('reports a successful deletion once without an error notification', async () => {
    vi.spyOn(skillLifecycleApi, 'deleteSkill').mockResolvedValue({ skillId: 123 })
    render(<App />)
    fireEvent.click(screen.getByRole('button', { name: 'Delete' }))
    await waitFor(() => expect(toast.success).toHaveBeenCalledTimes(1))
    expect(toast.error).not.toHaveBeenCalled()
  })


  it('keeps the existing login redirect and one notification when the session expires', async () => {
    vi.spyOn(skillLifecycleApi, 'deleteSkill').mockRejectedValue(new ApiError('session expired', 401))
    render(<App />)
    const location = { href: '' }
    // Override only location on a delegated window; React still sees the real DOM.
    const testWindow = Object.create(window)
    Object.defineProperty(testWindow, 'location', { value: location })
    vi.stubGlobal('window', testWindow)
    fireEvent.click(screen.getByRole('button', { name: 'Delete' }))
    await waitFor(() => expect(location.href).toBe('/login'))
    expect(toast.error).toHaveBeenCalledTimes(1)
    expect(toast.success).not.toHaveBeenCalled()
  })

  it('retains global error feedback for mutations without local ownership', async () => {
    render(<App />)
    fireEvent.click(screen.getByRole('button', { name: 'Other request' }))
    await waitFor(() => expect(toast.error).toHaveBeenCalledExactlyOnceWith(blockedError.serverMessage))
  })
})
