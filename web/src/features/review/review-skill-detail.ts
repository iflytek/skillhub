import type { ReviewSkillDetail, SkillVersion } from '@/api/types'

export function getReviewSkillDocumentation(detail: ReviewSkillDetail) {
  if (!detail.documentationPath || !detail.documentationContent) {
    return null
  }

  return {
    path: detail.documentationPath,
    content: detail.documentationContent,
  }
}

export function isActiveReviewVersion(version: SkillVersion, detail: ReviewSkillDetail) {
  return version.version === detail.activeVersion
}

export function getReviewDownloadHref(detail: ReviewSkillDetail) {
  return detail.downloadUrl
}
