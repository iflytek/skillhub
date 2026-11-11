import { describe, expect, it } from 'vitest'
import { ApiError } from '@/shared/lib/api-error'
import { classifyPublishFailure, publishResultPhase } from './publish-result'

describe('publish submission outcomes', () => {
  it.each(['SCANNING', 'PENDING_REVIEW', 'UPLOADED', 'PUBLISHED', 'SCAN_FAILED', 'REJECTED', 'YANKED', 'DRAFT'])('retains the real version state %s', status => {
    expect(publishResultPhase(status)).toBe(status)
  })
  it('does not call an unrecognized response published', () => expect(publishResultPhase('FUTURE')).toBe('unrecognized'))
  it.each([0, 408, 500, 503, 200])('treats ambiguous transport/response %s as unknown', status => {
    expect(classifyPublishFailure(new ApiError('transport', status))).toEqual({ kind: 'unknown' })
  })
  it('keeps unexpected thrown errors unknown', () => expect(classifyPublishFailure(new TypeError('network'))).toEqual({ kind: 'unknown' }))
  it('identifies version conflicts by the existing message contract', () => {
    expect(classifyPublishFailure(new ApiError('Version already exists', 400), '1.0.0')).toEqual({ kind: 'version-conflict', version: '1.0.0' })
    expect(classifyPublishFailure(new ApiError('Other conflict', 409))).toEqual({ kind: 'error', reason: 'failed' })
  })
  it('recognizes the localized version conflict without classifying arbitrary conflicts', () => {
    expect(classifyPublishFailure(new ApiError('Версия уже существует: 1.0.0', 400), '1.0.0')).toEqual({ kind: 'version-conflict', version: '1.0.0' })
  })
  it('leaves warnings in the original confirmation flow', () => {
    expect(classifyPublishFailure(new ApiError('预发布发现以下风险提醒，确认后仍可继续发布', 400))).toBeNull()
  })
  it.each([401, 403])('identifies access rejection %s without retry', status => {
    expect(classifyPublishFailure(new ApiError('denied', status))).toEqual({ kind: 'error', reason: 'forbidden' })
  })
  it.each([400, 413, 422])('identifies an invalid request %s without discarding its draft', status => {
    expect(classifyPublishFailure(new ApiError('invalid', status))).toEqual({ kind: 'error', reason: 'invalid' })
  })
})
