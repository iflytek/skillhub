import { describe, expect, it } from 'vitest'
import { appendNamespaceMember, replaceNamespaceMemberRole } from './namespace-member-cache'
import type { NamespaceMember, PagedResponse } from '@/api/types'

const baseMember = (overrides: Partial<NamespaceMember>): NamespaceMember => ({
  id: 1,
  userId: 'user-1',
  role: 'MEMBER',
  createdAt: '2026-03-17T00:00:00',
  ...overrides,
})

const basePage = (items: NamespaceMember[]): PagedResponse<NamespaceMember> => ({
  items,
  total: items.length,
  page: 0,
  size: 20,
})

describe('appendNamespaceMember', () => {
  it('appends a newly added member with the returned role', () => {
    const page = basePage([baseMember({})])
    const addedMember = baseMember({ id: 2, userId: 'user-2', role: 'ADMIN' })

    const result = appendNamespaceMember(page, addedMember)
    expect(result.items).toEqual([page.items[0], addedMember])
    expect(result.total).toBe(2)
  })

  it('replaces the existing member when the same user is returned again', () => {
    const page = basePage([baseMember({ role: 'MEMBER' })])
    const updatedMember = baseMember({ role: 'ADMIN' })

    const result = appendNamespaceMember(page, updatedMember)
    expect(result.items).toEqual([updatedMember])
    expect(result.total).toBe(1)
  })

  it('handles undefined page', () => {
    const addedMember = baseMember({ id: 2, userId: 'user-2', role: 'ADMIN' })

    const result = appendNamespaceMember(undefined, addedMember)
    expect(result.items).toEqual([addedMember])
    expect(result.total).toBe(1)
  })
})

describe('replaceNamespaceMemberRole', () => {
  it('updates the member role in the current list', () => {
    const page = basePage([
      baseMember({}),
      baseMember({ id: 2, userId: 'user-2', role: 'MEMBER' }),
    ])

    const result = replaceNamespaceMemberRole(page, 'user-2', 'ADMIN')
    expect(result?.items).toEqual([
      page.items[0],
      { ...page.items[1], role: 'ADMIN' },
    ])
  })

  it('handles undefined page', () => {
    const result = replaceNamespaceMemberRole(undefined, 'user-2', 'ADMIN')
    expect(result).toBeUndefined()
  })
})
