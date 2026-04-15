// @vitest-environment jsdom

import React from 'react'
import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderHook, waitFor } from '@testing-library/react'
import { collectionApi } from '@/api/client'
import { useCreateCollection, useMyCollections } from './use-collection-queries'

function createTestQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })
}

function createWrapper(queryClient: QueryClient) {
  return function Wrapper({ children }: { children: React.ReactNode }) {
    return React.createElement(QueryClientProvider, { client: queryClient }, children)
  }
}

describe('use-collection-queries', () => {
  const createSpy = vi.spyOn(collectionApi, 'create')
  const listMineSpy = vi.spyOn(collectionApi, 'listMine')

  beforeEach(() => {
    createSpy.mockReset()
    listMineSpy.mockReset()
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('useMyCollections uses stable list query key and calls listMine', async () => {
    listMineSpy.mockResolvedValue({ items: [], total: 0, page: 0, size: 20 })
    const queryClient = createTestQueryClient()

    const { result } = renderHook(() => useMyCollections({ page: 1, size: 10 }), {
      wrapper: createWrapper(queryClient),
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(listMineSpy).toHaveBeenCalledWith({ page: 1, size: 10 })

    const cached = queryClient.getQueryData(['collections', 'mine', 1, 10])
    expect(cached).toEqual({ items: [], total: 0, page: 0, size: 20 })
  })

  it('useCreateCollection invalidates mine and detail queries on success', async () => {
    const queryClient = createTestQueryClient()
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    createSpy.mockResolvedValue({
      id: 42,
      ownerId: 'u1',
      slug: 'my-col',
      title: 'T',
      description: '',
      visibility: 'PUBLIC',
      members: [],
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    })

    const { result } = renderHook(() => useCreateCollection(), {
      wrapper: createWrapper(queryClient),
    })

    await result.current.mutateAsync({
      title: 'T',
      visibility: 'PUBLIC',
      slug: 'my-col',
    })

    expect(createSpy).toHaveBeenCalledWith({
      title: 'T',
      visibility: 'PUBLIC',
      slug: 'my-col',
    })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['collections', 'mine'] })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['collections', '42'] })
  })
})
