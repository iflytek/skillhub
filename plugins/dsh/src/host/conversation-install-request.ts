import { createHash } from 'node:crypto';
import { z } from 'zod';

const scope = z.enum(['user', 'project']).optional();
const coordinate = z.string().min(1).max(2048).refine(value =>
  // eslint-disable-next-line no-control-regex -- Deliberately reject control characters in untrusted coordinates.
  value !== '.' && value !== '..' && !/[\s/\\\u0000-\u001f\u007f]/u.test(value));

/** A historical choice must retain its Host-issued reference, never fall back to coordinates. */
export const conversationInstallRequest = z.union([
  z.object({ selectionRef: z.string().min(1).max(2048), scope }).strict(),
  z.object({ namespace: coordinate, slug: coordinate, scope }).strict(),
]);
export type ConversationInstallRequest = z.infer<typeof conversationInstallRequest>;

const invocation = z.object({
  hostInstanceId: z.uuid(), sessionId: z.string().min(1), callId: z.string().min(1),
}).strict();

/** UUID v5 in the persistent Host namespace; request parameters are checked separately by the journal. */
export function conversationInstallKey(context: z.infer<typeof invocation>): string {
  const { hostInstanceId, sessionId, callId } = invocation.parse(context);
  const namespace = Buffer.from(hostInstanceId.replaceAll('-', ''), 'hex');
  const name = JSON.stringify(['skillhub-conversation-install-v1', sessionId, callId]);
  const bytes = createHash('sha1').update(namespace).update(name).digest().subarray(0, 16);
  bytes[6] = (bytes[6]! & 0x0f) | 0x50;
  bytes[8] = (bytes[8]! & 0x3f) | 0x80;
  const hex = bytes.toString('hex');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

/** A choice awaiting UI confirmation is not an accepted operation or a fingerprint lock. */
export interface PendingConversationInstall {
  schemaVersion: 1; kind: 'skillhub-install'; status: 'awaiting-confirmation';
  toolCallId: string; selectionRef: string; namespace: string; slug: string; version: string;
  sourceLabel: string; sourceRevision: string; createdAt: string;
}

/** No installation was submitted after a human declined or skipped the scope question. */
export interface CancelledConversationInstall {
  schemaVersion: 1; kind: 'skillhub-install'; status: 'cancelled'; toolCallId: string;
}
