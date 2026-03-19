import type { SkillDetail, SkillLifecycleVersion, SkillSummary } from '@/api/types'

type SkillLifecycleCarrier = Pick<SkillSummary, 'headlineVersion' | 'publishedVersion' | 'ownerPreviewVersion' | 'resolutionMode'>
  | Pick<SkillDetail, 'headlineVersion' | 'publishedVersion' | 'ownerPreviewVersion' | 'resolutionMode'>

/**
 * Small lifecycle helpers shared by list cards and detail pages so version-display rules stay
 * aligned with backend projections.
 */
export function getHeadlineVersion(skill: SkillLifecycleCarrier): SkillLifecycleVersion | null {
  return skill.headlineVersion ?? null
}

export function getPublishedVersion(skill: SkillLifecycleCarrier): SkillLifecycleVersion | null {
  return skill.publishedVersion ?? null
}

export function getOwnerPreviewVersion(skill: SkillLifecycleCarrier): SkillLifecycleVersion | null {
  return skill.ownerPreviewVersion ?? null
}

export function hasPendingOwnerPreview(skill: SkillLifecycleCarrier): boolean {
  return skill.ownerPreviewVersion?.status === 'PENDING_REVIEW'
}

export function isOwnerPreviewResolution(skill: SkillLifecycleCarrier): boolean {
  return skill.resolutionMode === 'OWNER_PREVIEW' && skill.headlineVersion?.status === 'PENDING_REVIEW'
}
