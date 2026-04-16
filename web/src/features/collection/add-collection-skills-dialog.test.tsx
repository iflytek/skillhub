// @vitest-environment jsdom

import type { ReactNode } from 'react'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AddCollectionSkillsDialog } from './add-collection-skills-dialog'

const useCollectionAddCandidatesMock = vi.fn()
const useBulkAddCollectionSkillsMock = vi.fn()
const useVisibleLabelsMock = vi.fn()

vi.mock('@tanstack/react-router', () => ({
  Link: ({ children }: { children?: ReactNode }) => <a>{children}</a>,
}))

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  const messages: Record<string, string> = {
    'collections.addSkillAction': 'Add skill',
    'collections.addSkillDialogTitle': 'Add skills to collection',
    'collections.addSkillDialogDescription': 'Find skills and add them in one pass.',
    'collections.alreadyInCollection': 'Already in collection',
    'collections.addSelected': 'Add selected',
    'search.placeholder': 'Search skills',
    'search.filters.label': 'Filter',
    'search.sort.label': 'Sort',
    'search.sort.newest': 'Newest',
    'search.sort.downloads': 'Downloads',
    'search.sort.relevance': 'Relevance',
    'collections.addDialogEmptyTitle': 'No addable skills available',
    'collections.addDialogEmptyDescription': 'All visible skills are already in this collection.',
    'collections.addDialogEmptyCta': 'Browse and publish skills',
    'pagination.prev': 'Previous',
    'pagination.next': 'Next',
    'pagination.pagePrefix': 'Page',
    'pagination.pageSuffix': '',
    'dialog.cancel': 'Cancel',
  }
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string, options?: Record<string, unknown>) => {
        if (key === 'collections.addSelected' && typeof options?.count === 'number') {
          return `Add selected (${options.count})`
        }
        return messages[key] ?? key
      },
    }),
  }
})

vi.mock('@/shared/hooks/use-collection-queries', () => ({
  useCollectionAddCandidates: (...args: unknown[]) => useCollectionAddCandidatesMock(...args),
  useBulkAddCollectionSkills: (...args: unknown[]) => useBulkAddCollectionSkillsMock(...args),
}))

vi.mock('@/shared/hooks/use-label-queries', () => ({
  useVisibleLabels: (...args: unknown[]) => useVisibleLabelsMock(...args),
}))

type Candidate = {
  id: number
  displayName: string
  namespace: string
  summary: string
  status: string
  visibility: string
  alreadyInCollection: boolean
}

function renderDialog(candidates: Candidate[], total = candidates.length) {
  useVisibleLabelsMock.mockReturnValue({
    data: [
      { slug: 'recommended', type: 'RECOMMENDED', displayName: 'Recommended' },
    ],
  })
  useCollectionAddCandidatesMock.mockReturnValue({
    data: {
      items: candidates,
      total,
      page: 0,
      size: 2,
    },
    isLoading: false,
    isFetching: false,
  })
  useBulkAddCollectionSkillsMock.mockReturnValue({
    mutateAsync: vi.fn().mockResolvedValue({
      addedIds: [],
      alreadyInCollectionIds: [],
      failedIds: [],
    }),
    isPending: false,
  })

  render(
    <AddCollectionSkillsDialog collectionId="42" collectionSkillIds={[22]}>
      <button type="button">Add skill</button>
    </AddCollectionSkillsDialog>,
  )
}

describe('AddCollectionSkillsDialog', () => {
  beforeEach(() => {
    useCollectionAddCandidatesMock.mockReset()
    useBulkAddCollectionSkillsMock.mockReset()
    useVisibleLabelsMock.mockReset()
  })

  afterEach(() => {
    vi.clearAllMocks()
    cleanup()
  })

  it('opens modal after clicking Add skill trigger', () => {
    renderDialog([
      {
        id: 1,
        displayName: 'Skill One',
        namespace: 'team',
        summary: 'Summary one',
        status: 'PUBLISHED',
        visibility: 'PUBLIC',
        alreadyInCollection: false,
      },
    ])

    expect(screen.queryByText('Add skills to collection')).toBeNull()
    fireEvent.click(screen.getByRole('button', { name: 'Add skill' }))

    expect(screen.getByText('Add skills to collection')).toBeTruthy()
  })

  it('renders search, filter, sort controls and paginated candidate list', () => {
    renderDialog(
      [
        {
          id: 1,
          displayName: 'Skill One',
          namespace: 'team',
          summary: 'Summary one',
          status: 'PUBLISHED',
          visibility: 'PUBLIC',
          alreadyInCollection: false,
        },
        {
          id: 2,
          displayName: 'Skill Two',
          namespace: 'team',
          summary: 'Summary two',
          status: 'PUBLISHED',
          visibility: 'PRIVATE',
          alreadyInCollection: false,
        },
      ],
      4,
    )

    fireEvent.click(screen.getByRole('button', { name: 'Add skill' }))

    expect(screen.getByLabelText('Search skills')).toBeTruthy()
    expect(screen.getByText('Filter')).toBeTruthy()
    expect(screen.getByText('Sort')).toBeTruthy()
    expect(screen.getAllByText('Skill One').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Skill Two').length).toBeGreaterThan(0)
    expect(screen.getByText('Page')).toBeTruthy()
  })

  it('disables already-added candidates and shows Already in collection badge', () => {
    renderDialog([
      {
        id: 22,
        displayName: 'Existing Skill',
        namespace: 'team',
        summary: 'Existing summary',
        status: 'PUBLISHED',
        visibility: 'PUBLIC',
        alreadyInCollection: true,
      },
    ])

    fireEvent.click(screen.getByRole('button', { name: 'Add skill' }))

    expect(screen.getAllByText('Already in collection').length).toBeGreaterThan(0)
    expect(screen.getByRole('button', { name: 'Already in collection' }).hasAttribute('disabled')).toBe(true)
  })

  it('updates Add selected (N) label while toggling selections', () => {
    renderDialog([
      {
        id: 1,
        displayName: 'Skill One',
        namespace: 'team',
        summary: 'Summary one',
        status: 'PUBLISHED',
        visibility: 'PUBLIC',
        alreadyInCollection: false,
      },
      {
        id: 2,
        displayName: 'Skill Two',
        namespace: 'team',
        summary: 'Summary two',
        status: 'PUBLISHED',
        visibility: 'PUBLIC',
        alreadyInCollection: false,
      },
    ])

    fireEvent.click(screen.getByRole('button', { name: 'Add skill' }))

    expect(screen.getByRole('button', { name: 'Add selected (0)' })).toBeTruthy()

    fireEvent.click(screen.getByTestId('add-collection-skills-select-1'))
    expect(screen.getByRole('button', { name: 'Add selected (1)' })).toBeTruthy()

    fireEvent.click(screen.getByTestId('add-collection-skills-select-2'))
    expect(screen.getByRole('button', { name: 'Add selected (2)' })).toBeTruthy()
  })

  it('shows modal empty-state when no addable candidates exist', () => {
    renderDialog([
      {
        id: 22,
        displayName: 'Existing Skill',
        namespace: 'team',
        summary: 'Existing summary',
        status: 'PUBLISHED',
        visibility: 'PUBLIC',
        alreadyInCollection: true,
      },
    ])

    fireEvent.click(screen.getByRole('button', { name: 'Add skill' }))

    expect(screen.getByTestId('add-collection-skills-empty-state')).toBeTruthy()
    expect(screen.getByText('No addable skills available')).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Browse and publish skills' })).toBeTruthy()
  })
})
