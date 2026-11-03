import { RemoteError } from '@deepseek-ai/dsh-typert-protocol';

/** Internal diagnostics only: never serialize an error message, context or identifier. */
export const installationContextReasons = ['AGENT', 'CWD', 'SOURCE', 'FS', 'POLICY', 'RUNTIME', 'DETAIL', 'TARGET', 'HOST_MISMATCH'] as const;
export type InstallationContextReason = typeof installationContextReasons[number];
const reasons = new WeakMap<object, InstallationContextReason>();

type ContextMessage = 'Installation context changed.' | 'Conversation unavailable.' | 'Detail expired.'
  | 'Project unavailable.' | 'Installation context unavailable.' | 'Conversation changed.'
  | 'SkillHub source context changed.' | 'Reopen the skill detail and choose the installation target again.';

export function installationContextError(reason: InstallationContextReason, message: ContextMessage = 'Installation context changed.'): RemoteError {
  const error = new RemoteError('skillhub/context-stale', message, { retryable: false });
  reasons.set(error, reason);
  return error;
}

export function installationContextReason(error: unknown): InstallationContextReason | undefined {
  if (!error || typeof error !== 'object') return undefined;
  if ('code' in error && error.code === 'HOST_MISMATCH') return 'HOST_MISMATCH';
  return reasons.get(error);
}
