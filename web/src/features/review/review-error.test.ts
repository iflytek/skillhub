import { describe, expect, it } from 'vitest'
import { resolveReviewActionErrorDescription } from './review-error'

describe('resolveReviewActionErrorDescription', () => {
  it('returns the error message when present', () => {
    expect(resolveReviewActionErrorDescription(new Error('审核规则校验失败'))).toBe('审核规则校验失败')
  })

  it('returns undefined for blank or non-error values', () => {
    expect(resolveReviewActionErrorDescription(new Error('   '))).toBeUndefined()
    expect(resolveReviewActionErrorDescription('审核失败')).toBeUndefined()
  })
})
