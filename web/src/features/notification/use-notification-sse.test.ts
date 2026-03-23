import { QueryClient } from '@tanstack/react-query'
import { describe, expect, it, vi } from 'vitest'
import { attachNotificationSseListeners } from './use-notification-sse'

function createFakeConnection() {
  const listeners = new Map<string, Array<(event: MessageEvent) => void>>()
  return {
    addEventListener(type: string, listener: (event: MessageEvent) => void) {
      const current = listeners.get(type) ?? []
      current.push(listener)
      listeners.set(type, current)
    },
    close() {
      // no-op for tests
    },
    emit(type: string) {
      for (const listener of listeners.get(type) ?? []) {
        listener(new MessageEvent(type))
      }
    },
  }
}

describe('attachNotificationSseListeners', () => {
  it('does not refetch unread count when the sse connection opens or reconnects', () => {
    const queryClient = new QueryClient()
    const invalidateQueries = vi.spyOn(queryClient, 'invalidateQueries')
    const connection = createFakeConnection()

    attachNotificationSseListeners(connection, queryClient, 'user-a')

    connection.emit('open')
    connection.emit('open')

    expect(invalidateQueries).not.toHaveBeenCalledWith({
      queryKey: ['notifications', 'user-a', 'unread-count'],
    })
  })

  it('does not mutate the unread badge when the connection opens or reconnects', () => {
    const queryClient = new QueryClient()
    queryClient.setQueryData(['notifications', 'user-a', 'unread-count'], { count: 4 })
    const connection = createFakeConnection()

    attachNotificationSseListeners(connection, queryClient, 'user-a')

    connection.emit('open')
    connection.emit('open')

    expect(queryClient.getQueryData(['notifications', 'user-a', 'unread-count'])).toEqual({ count: 4 })
  })

  it('increments unread count and invalidates notification list on new notification events', () => {
    const queryClient = new QueryClient()
    queryClient.setQueryData(['notifications', 'user-a', 'unread-count'], { count: 1 })
    const invalidateQueries = vi.spyOn(queryClient, 'invalidateQueries')
    const connection = createFakeConnection()

    attachNotificationSseListeners(connection, queryClient, 'user-a')
    connection.emit('notification')

    expect(queryClient.getQueryData(['notifications', 'user-a', 'unread-count'])).toEqual({ count: 2 })
    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: ['notifications', 'user-a', 'list'],
    })
  })

  it('starts the unread badge from one when no cache exists yet', () => {
    const queryClient = new QueryClient()
    const connection = createFakeConnection()

    attachNotificationSseListeners(connection, queryClient, 'user-a')
    connection.emit('notification')

    expect(queryClient.getQueryData(['notifications', 'user-a', 'unread-count'])).toEqual({ count: 1 })
  })
})
