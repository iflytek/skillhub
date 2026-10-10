import { describe, expect, it } from 'vitest'
import {
  getHeadlineVersion,
  getPublishedVersion,
  getOwnerPreviewVersion,
  hasPendingOwnerPreview,
  isOwnerPreviewResolution,
} from './skill-lifecycle'

const version = (status: string) => ({ id: 1, version: '1.0.0', status })

describe('getHeadlineVersion', () => {
  it('returns the headline version when present', () => {
    const skill = { headlineVersion: version('PUBLISHED') }
    expect(getHeadlineVersion(skill)).toEqual(version('PUBLISHED'))
  })

  it('returns null when headline version is undefined', () => {
    const skill = {}
    expect(getHeadlineVersion(skill)).toBeNull()
  })
})

describe('getPublishedVersion', () => {
  it('returns the published version when present', () => {
    const skill = { publishedVersion: version('PUBLISHED') }
    expect(getPublishedVersion(skill)).toEqual(version('PUBLISHED'))
  })

  it('returns null when published version is undefined', () => {
    const skill = {}
    expect(getPublishedVersion(skill)).toBeNull()
  })
})

describe('getOwnerPreviewVersion', () => {
  it('returns the owner preview version when present', () => {
    const skill = { ownerPreviewVersion: version('PENDING_REVIEW') }
    expect(getOwnerPreviewVersion(skill)).toEqual(version('PENDING_REVIEW'))
  })

  it('returns null when owner preview version is undefined', () => {
    const skill = {}
    expect(getOwnerPreviewVersion(skill)).toBeNull()
  })
})

describe('hasPendingOwnerPreview', () => {
  it('returns true when owner preview status is PENDING_REVIEW', () => {
    const skill = { ownerPreviewVersion: version('PENDING_REVIEW') }
    expect(hasPendingOwnerPreview(skill)).toBe(true)
  })

  it('returns false when owner preview status is not PENDING_REVIEW', () => {
    const skill = { ownerPreviewVersion: version('DRAFT') }
    expect(hasPendingOwnerPreview(skill)).toBe(false)
  })

  it('returns false when owner preview version is undefined', () => {
    const skill = {}
    expect(hasPendingOwnerPreview(skill)).toBe(false)
  })
})

describe('isOwnerPreviewResolution', () => {
  it('returns true when resolution is OWNER_PREVIEW and headline is not PUBLISHED', () => {
    const skill = {
      resolutionMode: 'OWNER_PREVIEW',
      headlineVersion: version('PENDING_REVIEW'),
    }
    expect(isOwnerPreviewResolution(skill)).toBe(true)
  })

  it('returns false when resolution is OWNER_PREVIEW but headline is PUBLISHED', () => {
    const skill = {
      resolutionMode: 'OWNER_PREVIEW',
      headlineVersion: version('PUBLISHED'),
    }
    expect(isOwnerPreviewResolution(skill)).toBe(false)
  })

  it('returns false when resolution mode is not OWNER_PREVIEW', () => {
    const skill = {
      resolutionMode: 'DEFAULT',
      headlineVersion: version('DRAFT'),
    }
    expect(isOwnerPreviewResolution(skill)).toBe(false)
  })

  it('returns false when resolution mode is undefined', () => {
    const skill = {}
    expect(isOwnerPreviewResolution(skill)).toBe(false)
  })
})
