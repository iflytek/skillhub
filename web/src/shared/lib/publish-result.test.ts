import { describe, expect, it } from 'vitest'
import type { PublishResult } from '@/api/types'
import { formatPublishResultLabel, getPublishResultItems, isPublishResultItem } from './publish-result'

const singleResult = {
  skillId: 1,
  namespace: 'team',
  slug: 'alpha',
  version: '1.0.0',
  status: 'PUBLISHED',
  fileCount: 2,
  totalSize: 128,
}

describe('publish-result helpers', () => {
  it('keeps the legacy single publish response as one result item', () => {
    expect(isPublishResultItem(singleResult)).toBe(true)
    expect(getPublishResultItems(singleResult)).toEqual([singleResult])
    expect(formatPublishResultLabel(singleResult)).toBe('team/alpha@1.0.0')
  })

  it('prefers backend batch results when present', () => {
    const batchResult: PublishResult = {
      ...singleResult,
      results: [
        singleResult,
        { ...singleResult, skillId: 2, slug: 'beta', status: 'PENDING_REVIEW' },
      ],
    }

    expect(getPublishResultItems(batchResult).map((item) => item.slug)).toEqual(['alpha', 'beta'])
  })

  it('falls back to the top-level publish result when batch results are empty', () => {
    const result: PublishResult = { ...singleResult, results: [] }

    expect(getPublishResultItems(result)).toEqual([result])
  })
})
