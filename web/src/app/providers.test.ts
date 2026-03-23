import { describe, expect, it, vi } from 'vitest'

// App/providers is a wiring module that sets up QueryClient, RouterProvider,
// and Toaster. It exports only the App component. We verify the export exists.

vi.mock('@tanstack/react-query', () => ({
  QueryClient: vi.fn().mockImplementation(() => ({})),
  QueryClientProvider: ({ children }: { children: unknown }) => children,
  QueryCache: vi.fn().mockImplementation(() => ({})),
  MutationCache: vi.fn().mockImplementation(() => ({})),
}))

vi.mock('@tanstack/react-router', () => ({
  RouterProvider: () => null,
}))

vi.mock('@/shared/components/toaster', () => ({
  Toaster: () => null,
}))

vi.mock('@/shared/lib/api-error', () => ({
  handleApiError: vi.fn(),
}))

vi.mock('./router', () => ({
  router: {},
}))

import { App } from './providers'

describe('App', () => {
  it('exports a named App component function', () => {
    expect(typeof App).toBe('function')
    expect(App.name).toBe('App')
  })
})
