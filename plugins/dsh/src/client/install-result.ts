import { z } from 'zod';
import { installationFailureCodes } from '../host/installation-failure.js';

const text = z.string().min(1).max(2048);
const schema = z.object({
  schemaVersion: z.literal(1), kind: z.literal('skillhub-install'), status: z.literal('accepted'),
  toolCallId: text, operationId: z.uuid(), hostInstanceId: z.uuid(),
  namespace: text, slug: text, version: text, targetLabel: text,
  sourceLabel: text.refine(value => {
    try {
      const url = new URL(value);
      return ['http:', 'https:'].includes(url.protocol) && !url.username && !url.password && !url.search && !url.hash;
    } catch { return false; }
  }),
  sourceRevision: z.uuid(), hostLabel: text, createdAt: z.iso.datetime(),
}).strict();
export type InstallResult = z.infer<typeof schema>;
const failureSchema = z.object({ schemaVersion: z.literal(1), kind: z.literal('skillhub-install'),
  status: z.literal('not-confirmed'), toolCallId: text, code: z.enum(installationFailureCodes) }).strict();
export type InstallFailure = z.infer<typeof failureSchema>;
export function parseInstallFailure(value: unknown): InstallFailure | undefined {
  const result = failureSchema.safeParse(value);
  return result.success ? result.data : undefined;
}

/** History can identify an existing operation but can never authorize a write. */
export function parseInstallResult(value: unknown): InstallResult | undefined {
  const result = schema.safeParse(value);
  return result.success ? result.data : undefined;
}

const pendingSchema = schema.pick({ schemaVersion: true, kind: true, toolCallId: true,
  namespace: true, slug: true, version: true, sourceLabel: true, sourceRevision: true, createdAt: true }).extend({
  status: z.literal('awaiting-confirmation'), selectionRef: z.uuid(),
}).strict();
export type PendingInstallResult = z.infer<typeof pendingSchema>;
/** A pending history snapshot can only open a still-valid Host choice, never restore authorization. */
export function parsePendingInstallResult(value: unknown): PendingInstallResult | undefined {
  const result = pendingSchema.safeParse(value);
  return result.success ? result.data : undefined;
}

export function isCancelledInstallResult(value: unknown): boolean {
  return z.object({ schemaVersion: z.literal(1), kind: z.literal('skillhub-install'),
    status: z.literal('cancelled'), toolCallId: text }).strict().safeParse(value).success;
}
