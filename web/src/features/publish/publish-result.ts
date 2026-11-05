import type { PublishResult } from '@/api/types'
import { ApiError } from '@/shared/lib/api-error'
import { isPrecheckConfirmationMessage, isVersionExistsMessage } from './publish-error-utils'

export type PublishSubmissionState =
  | { kind: 'submitting'; label: string; version?: string }
  | { kind: 'accepted'; result: PublishResult; hadPublishedVersion?: boolean }
  | { kind: 'version-conflict'; version?: string }
  | { kind: 'unknown' }
  | { kind: 'error'; reason: 'forbidden' | 'invalid' | 'failed' }

/** Warning confirmation stays with the existing precheck flow. Never retry here. */
export function classifyPublishFailure(error: unknown, version?: string): PublishSubmissionState | null {
  if (!(error instanceof ApiError)) return { kind: 'unknown' }
  const message = error.serverMessage || error.message
  if (isVersionExistsMessage(message) || isVersionExistsMessage(error.serverMessageKey)) return { kind: 'version-conflict', version }
  if (isPrecheckConfirmationMessage(message)) return null
  if (error.status === 0 || error.status === 408 || error.status >= 500 || (error.status >= 200 && error.status < 300)) {
    return { kind: 'unknown' }
  }
  if (error.status === 401 || error.status === 403) return { kind: 'error', reason: 'forbidden' }
  if (error.status === 400 || error.status === 413 || error.status === 422) return { kind: 'error', reason: 'invalid' }
  return { kind: 'error', reason: 'failed' }
}

const versionStates = ['SCANNING', 'PENDING_REVIEW', 'UPLOADED', 'PUBLISHED', 'SCAN_FAILED', 'REJECTED', 'YANKED', 'DRAFT'] as const
export function publishResultPhase(status: string): typeof versionStates[number] | 'unrecognized' {
  return versionStates.find(value => value === status) ?? 'unrecognized'
}
