export const installationFailureCodes = ['CONTEXT_STALE', 'REQUEST_CONFLICT', 'SOURCE_NOT_CONFIGURED',
  'SOURCE_DENIED', 'SKILL_UNAVAILABLE', 'RATE_LIMITED', 'SOURCE_UNAVAILABLE', 'TARGET_UNAVAILABLE', 'UNCONFIRMED'] as const;
export type InstallationFailureCode = typeof installationFailureCodes[number];

/** Only known codes cross the tool boundary; messages and diagnostic data may contain Host paths. */
export function installationFailureCode(error: unknown): InstallationFailureCode {
  if (!error || typeof error !== 'object') return 'UNCONFIRMED';
  const code = 'code' in error ? error.code : undefined;
  if (code === 'skillhub/context-stale' || code === 'HOST_MISMATCH') return 'CONTEXT_STALE';
  if (code === 'skillhub/request-conflict' || code === 'REQUEST_KEY_CONFLICT') return 'REQUEST_CONFLICT';
  if (code === 'skillhub/source-not-configured') return 'SOURCE_NOT_CONFIGURED';
  if (code === 'skillhub/target-unavailable') return 'TARGET_UNAVAILABLE';
  if (code === 'skillhub/registry-error') {
    const data = 'details' in error ? error.details : undefined;
    const reason = data && typeof data === 'object' && 'reason' in data ? data.reason : undefined;
    if (reason === 'ANONYMOUS_DENIED') return 'SOURCE_DENIED';
    if (reason === 'NOT_FOUND') return 'SKILL_UNAVAILABLE';
    if (reason === 'RATE_LIMITED') return 'RATE_LIMITED';
    return 'SOURCE_UNAVAILABLE';
  }
  return 'UNCONFIRMED';
}
