import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { PublishResult } from '@/api/types'

const mocks = vi.hoisted(() => ({
  fetchJson: vi.fn<() => Promise<PublishResult>>(),
}))

interface MutationOptions<TVariables, TData> {
  mutationFn: (variables: TVariables) => Promise<TData>
}

interface PublishVariables {
  namespace: string
  files: File[]
  visibility: string
}

vi.mock('@tanstack/react-query', () => ({
  useMutation: <TVariables, TData>(options: MutationOptions<TVariables, TData>) => options,
  useQuery: vi.fn(),
  useQueryClient: () => ({ invalidateQueries: vi.fn() }),
}))

vi.mock('@/api/client', () => ({
  WEB_API_PREFIX: '/api/web',
  fetchJson: mocks.fetchJson,
  fetchText: vi.fn(),
  getCsrfHeaders: () => ({ 'X-XSRF-TOKEN': 'token' }),
  skillLifecycleApi: {},
}))

vi.mock('@/features/skill/skill-delete-flow', () => ({
  clearDeletedSkillQueries: vi.fn(),
}))

import { usePublishSkill } from './use-skill-queries'

const alphaResult = {
  skillId: 1,
  namespace: 'team',
  slug: 'alpha',
  version: '1.0.0',
  status: 'PUBLISHED',
  fileCount: 2,
  totalSize: 128,
}

const betaResult = {
  ...alphaResult,
  skillId: 2,
  slug: 'beta',
}

function createZip(name: string): File {
  return new File(['zip'], name, { type: 'application/zip' })
}

describe('usePublishSkill mutation', () => {
  beforeEach(() => {
    mocks.fetchJson.mockReset()
  })

  it('returns backend batch results from a single publish request', async () => {
    mocks.fetchJson.mockResolvedValueOnce({
      ...alphaResult,
      results: [alphaResult, betaResult],
    })

    const mutation = usePublishSkill() as unknown as MutationOptions<PublishVariables, PublishResult>
    const result = await mutation.mutationFn({
      namespace: 'team',
      files: [createZip('alpha.zip'), createZip('beta.zip')],
      visibility: 'PUBLIC',
    })

    expect(mocks.fetchJson).toHaveBeenCalledTimes(1)
    expect(result.results?.map((item) => item.slug)).toEqual(['alpha', 'beta'])
  })

  it('falls back to single-file requests when the backend only returns one result', async () => {
    mocks.fetchJson
      .mockResolvedValueOnce(alphaResult)
      .mockResolvedValueOnce(betaResult)

    const mutation = usePublishSkill() as unknown as MutationOptions<PublishVariables, PublishResult>
    const result = await mutation.mutationFn({
      namespace: 'team',
      files: [createZip('alpha.zip'), createZip('beta.zip')],
      visibility: 'PUBLIC',
    })

    expect(mocks.fetchJson).toHaveBeenCalledTimes(2)
    expect(result.results?.map((item) => item.slug)).toEqual(['alpha', 'beta'])
  })
})
