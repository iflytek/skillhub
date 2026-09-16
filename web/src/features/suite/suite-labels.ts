import type { TFunction } from 'i18next'

const STATUS_KEYS: Record<string, string> = {
  DRAFT: 'skillDetail.versionStatusDraft',
  PENDING_REVIEW: 'skillDetail.versionStatusPendingReview',
  PUBLISHED: 'skillDetail.versionStatusPublished',
  REJECTED: 'skillDetail.versionStatusRejected',
  YANKED: 'skillDetail.versionStatusYanked',
}

const VISIBILITY_KEYS: Record<string, string> = {
  PUBLIC: 'publish.visibilityOptions.public',
  NAMESPACE_ONLY: 'publish.visibilityOptions.namespaceOnly',
  PRIVATE: 'publish.visibilityOptions.private',
}

export function suiteStatusLabel(t: TFunction, status: string): string {
  const key = STATUS_KEYS[status]
  return key ? t(key) : status
}

export function suiteVisibilityLabel(t: TFunction, visibility: string): string {
  const key = VISIBILITY_KEYS[visibility]
  return key ? t(key) : visibility
}

export function suiteBlockingReasonLabel(t: TFunction, reason: string): string {
  return t(`suite.blockingReasons.${reason}`, { defaultValue: reason })
}
