import { QueryClient } from '@tanstack/react-query'
import { describe, expect, it } from 'vitest'
import { decrementUnreadCount, incrementUnreadCount, resetUnreadCount } from './notification-unread-cache'
import { NOTIFICATION_QUERY_KEYS } from './use-notifications'

describe('notification unread cache helpers', () => {
  it('increments unread count from the existing cached value', () => {
    const queryClient = new QueryClient()
    queryClient.setQueryData(NOTIFICATION_QUERY_KEYS.unreadCount('user-a'), { count: 2 })

    incrementUnreadCount(queryClient, 'user-a')

    expect(queryClient.getQueryData(NOTIFICATION_QUERY_KEYS.unreadCount('user-a'))).toEqual({ count: 3 })
  })

  it('initializes unread count cache when incrementing without existing data', () => {
    const queryClient = new QueryClient()

    incrementUnreadCount(queryClient, 'user-a')

    expect(queryClient.getQueryData(NOTIFICATION_QUERY_KEYS.unreadCount('user-a'))).toEqual({ count: 1 })
  })

  it('decrements unread count without going below zero', () => {
    const queryClient = new QueryClient()
    queryClient.setQueryData(NOTIFICATION_QUERY_KEYS.unreadCount('user-a'), { count: 1 })

    decrementUnreadCount(queryClient, 'user-a')
    decrementUnreadCount(queryClient, 'user-a')

    expect(queryClient.getQueryData(NOTIFICATION_QUERY_KEYS.unreadCount('user-a'))).toEqual({ count: 0 })
  })

  it('resets unread count to zero', () => {
    const queryClient = new QueryClient()
    queryClient.setQueryData(NOTIFICATION_QUERY_KEYS.unreadCount('user-a'), { count: 5 })

    resetUnreadCount(queryClient, 'user-a')

    expect(queryClient.getQueryData(NOTIFICATION_QUERY_KEYS.unreadCount('user-a'))).toEqual({ count: 0 })
  })
})
