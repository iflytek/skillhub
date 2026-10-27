import { z } from 'zod';

const text = z.string().min(1).max(2048);
const item = z.object({ itemKey: text, namespace: text, slug: text, version: text,
  scope: z.enum(['user', 'project']), state: z.enum(['present', 'missing', 'metadata-invalid', 'mismatch', 'unreadable']) });
const base = { schemaVersion: z.literal(1), toolCallId: text, sourceRevision: text, sourceLabel: text };
const installed = z.object({ ...base, kind: z.literal('skillhub-installed'), items: z.array(item),
  complete: z.boolean(), issues: z.array(z.string()), unavailable: z.array(z.enum(['user', 'project'])) });
const check = z.object({ state: z.enum(['upgradable', 'current', 'blocked', 'unavailable']), currentVersion: text,
  candidateVersion: text.nullable(), reason: z.string(), checkedAt: text });
const update = z.object({ ...base, kind: z.literal('skillhub-update-check'), item, check });
const operation = z.object({ operationId: z.uuid(), context: z.object({ hostInstanceId: z.uuid() }) });
const management = z.object({ ...base, kind: z.literal('skillhub-management'), item,
  action: z.enum(['upgrade', 'uninstall']), status: z.enum(['awaiting-confirmation', 'cancelled', 'blocked', 'accepted']),
  candidateVersion: text.nullable(), reason: z.string(), confirmationRef: text.nullable(), operation: operation.nullable() });
const operationResult = z.object({ schemaVersion: z.literal(1), kind: z.literal('skillhub-operation'), toolCallId: text, operation: operation.extend({ kind: z.string(), namespace: text.optional(), slug: text, version: text, targetLabel: text, phase: z.string(), warningCount: z.number(), discovery: z.enum(['unconfirmed', 'confirmed', 'failed']).optional(), recovery: z.enum(['not-needed', 'restored', 'incomplete', 'unknown']).optional() }).nullable() });
export const availabilitySchema = z.object({ installation: z.object({ namespace: text, slug: text, version: text, scope: z.enum(['user', 'project']) }).optional(), scope: z.enum(['user', 'project']).optional(), checkedAt: text, name: z.string().max(128).regex(/^[a-z0-9]+(?:-[a-z0-9]+)*$/).optional(),
  association: z.enum(['managed-reference', 'not-associated']), integrity: z.enum(['verified', 'changed', 'missing', 'unknown']),
  discovery: z.enum(['winner', 'shadowed', 'not-discovered', 'unknown']),
  invocation: z.object({ userInvocable: z.boolean(), modelInvocable: z.boolean() }).optional(),
  reason: z.enum(['observed', 'host-unavailable', 'files-missing', 'files-unreadable', 'local-changes', 'baseline-unverified',
    'definition-unverified', 'snapshot-unavailable', 'snapshot-incomplete', 'observation-changed', 'not-discovered', 'same-name-winner', 'definition-changed', 'read-failed']) });
const availability = z.object({ schemaVersion: z.literal(1), kind: z.literal('skillhub-availability'), toolCallId: text, diagnosis: availabilitySchema });
const schema = z.discriminatedUnion('kind', [installed, update, management, operationResult, availability]);
export type ManagementViewResult = z.infer<typeof schema>;
export type ManagementReceipt = z.infer<typeof management>;
/** Transcript metadata is display data, never authority to mutate. Host rechecks every reference. */
export function parseManagementResult(value: unknown, callId: string): ManagementViewResult | undefined {
  const result = schema.safeParse(value);
  if (!result.success || result.data.toolCallId !== callId) return undefined;
  if (result.data.kind === 'skillhub-management' && ((result.data.status === 'awaiting-confirmation' && !result.data.confirmationRef)
    || (result.data.status === 'accepted' && !result.data.operation))) return undefined;
  return result.data;
}
export function managementReason(reason: string): string {
  const messages: Record<string, string> = {
    'same-content': '已安装此版本，无需更新。',
    'local-changes': '存在本地修改，本次操作已阻止。',
    'remote-older': '所选版本较旧，暂不支持降级。',
    'unknown-order': '无法确定版本先后顺序，暂不能更新。',
    'same-version-content-changed': '相同版本内容已变化，请核实来源。',
    'shared-target': '此安装由多个来源共同管理，暂不能修改。',
    'record-changed': '安装记录已变化，请重新查询。',
    'recovery-required': '有未完成的操作，请先核验恢复状态。',
    'metadata-invalid': '安装记录无法核验，暂不能修改。',
    'local-record-unverified': '安装记录无法核验，暂不能修改。',
    'missing-fingerprint': '缺少内容基线，暂不能更新。',
  };
  return messages[reason] ?? '当前条件不满足，请重新查询安装记录后重试。';
}
