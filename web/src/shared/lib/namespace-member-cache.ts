import type { NamespaceMember, PagedResponse } from '@/api/types'

export function appendNamespaceMember(
  currentPage: PagedResponse<NamespaceMember> | undefined,
  nextMember: NamespaceMember,
): PagedResponse<NamespaceMember> {
  if (!currentPage) {
    return { items: [nextMember], total: 1, page: 0, size: 20 }
  }

  const existingIndex = currentPage.items.findIndex((member) => member.userId === nextMember.userId)

  if (existingIndex === -1) {
    return {
      ...currentPage,
      items: [...currentPage.items, nextMember],
      total: currentPage.total + 1,
    }
  }

  return {
    ...currentPage,
    items: currentPage.items.map((member, index) => (index === existingIndex ? nextMember : member)),
  }
}

export function replaceNamespaceMemberRole(
  currentPage: PagedResponse<NamespaceMember> | undefined,
  userId: string,
  role: string,
): PagedResponse<NamespaceMember> | undefined {
  if (!currentPage) return currentPage
  return {
    ...currentPage,
    items: currentPage.items.map((member) =>
      member.userId === userId
        ? {
            ...member,
            role,
          }
        : member,
    ),
  }
}
