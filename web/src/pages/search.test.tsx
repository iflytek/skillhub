import type { ReactNode } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const navigateMock = vi.fn()
const useSearchMock = vi.fn()
const buttonRecords: Array<{ label: string; variant?: string | null; onClick?: (() => void) | undefined }> = []
const paginationProps: Array<{ onPageChange: (page: number) => void }> = []

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => navigateMock,
  useSearch: () => useSearchMock(),
}))

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string, options?: Record<string, unknown>) => {
        if (options && typeof options.count === 'number') {
          return `${key}:${options.count}`
        }
        return key
      },
    }),
  }
})

vi.mock('@/features/auth/use-auth', () => ({
  useAuth: () => ({
    isAuthenticated: true,
  }),
}))

vi.mock('@/features/search/search-bar', () => ({
  SearchBar: () => <div>search-bar</div>,
}))

vi.mock('@/features/skill/skill-card', () => ({
  SkillCard: () => <div>skill-card</div>,
}))

vi.mock('@/shared/components/skeleton-loader', () => ({
  SkeletonList: () => <div>skeleton</div>,
}))

vi.mock('@/shared/components/empty-state', () => ({
  EmptyState: () => <div>empty-state</div>,
}))

vi.mock('@/shared/components/pagination', () => ({
  Pagination: (props: { onPageChange: (page: number) => void }) => {
    paginationProps.push(props)
    return <div>pagination</div>
  },
}))

vi.mock('@/shared/ui/button', () => ({
  Button: ({
    children,
    onClick,
    variant,
  }: {
    children?: ReactNode
    onClick?: () => void
    variant?: string
  }) => {
    const label = Array.isArray(children) ? children.join('') : String(children ?? '')
    buttonRecords.push({ label, variant, onClick })
    return <button data-variant={variant}>{children}</button>
  },
}))

vi.mock('@/app/page-shell-style', () => ({
  APP_SHELL_PAGE_CLASS_NAME: 'page-shell',
}))

const useSearchSkillsMock = vi.fn()

vi.mock('@/shared/hooks/use-skill-queries', () => ({
  useSearchSkills: () => useSearchSkillsMock(),
}))

vi.mock('@/shared/hooks/use-label-queries', () => ({
  useVisibleLabels: () => ({
    data: [
      { slug: 'code-generation', type: 'RECOMMENDED', displayName: 'Code Generation' },
      { slug: 'official', type: 'RECOMMENDED', displayName: 'Official' },
    ],
  }),
}))

vi.mock('@/shared/hooks/use-user-queries', () => ({
  useMyStars: () => ({
    data: [],
    isLoading: false,
    isFetching: false,
  }),
}))

import { SearchPage } from './search'

function findButton(label: string) {
  const record = buttonRecords.find((item) => item.label === label)
  if (!record) {
    throw new Error(`Missing button: ${label}`)
  }
  return record
}

describe('SearchPage', () => {
  beforeEach(() => {
    navigateMock.mockReset()
    buttonRecords.length = 0
    paginationProps.length = 0
    useSearchMock.mockReturnValue({
      q: 'agent',
      label: 'code-generation',
      sort: 'downloads',
      page: 1,
      starredOnly: false,
    })
    useSearchSkillsMock.mockReturnValue({
      data: {
        items: [{ id: 1, displayName: 'Demo Skill', summary: 'summary', namespace: 'global', slug: 'demo', downloadCount: 1, starCount: 1, ratingCount: 0, updatedAt: '2026-03-20T00:00:00Z', canSubmitPromotion: false }],
        total: 24,
        page: 1,
        size: 12,
      },
      isLoading: false,
      isFetching: false,
    })
  })

  it('marks the selected label button as active on initial render', () => {
    const html = renderToStaticMarkup(<SearchPage />)

    expect(html).toContain('Code Generation')
    expect(findButton('Code Generation').variant).toBe('default')
    expect(findButton('Official').variant).toBe('outline')
  })

  it('toggles the selected label off and resets paging', () => {
    renderToStaticMarkup(<SearchPage />)

    findButton('Code Generation').onClick?.()

    expect(navigateMock).toHaveBeenCalledWith({
      to: '/search',
      search: {
        q: 'agent',
        label: '',
        sort: 'downloads',
        page: 0,
        starredOnly: false,
      },
    })
  })

  it('preserves the active label when changing sort', () => {
    renderToStaticMarkup(<SearchPage />)

    findButton('search.sort.newest').onClick?.()

    expect(navigateMock).toHaveBeenCalledWith({
      to: '/search',
      search: {
        q: 'agent',
        label: 'code-generation',
        sort: 'newest',
        page: 0,
        starredOnly: false,
      },
    })
  })

  it('preserves the active label when paging and when toggling starred-only', () => {
    renderToStaticMarkup(<SearchPage />)

    paginationProps[0]?.onPageChange(2)
    findButton('search.filterStarred').onClick?.()

    expect(navigateMock).toHaveBeenNthCalledWith(1, {
      to: '/search',
      search: {
        q: 'agent',
        label: 'code-generation',
        sort: 'downloads',
        page: 2,
        starredOnly: false,
      },
    })
    expect(navigateMock).toHaveBeenNthCalledWith(2, {
      to: '/search',
      search: {
        q: 'agent',
        label: 'code-generation',
        sort: 'downloads',
        page: 0,
        starredOnly: true,
      },
    })
  })
})
