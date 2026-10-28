import { defineTool, type ParameterSchemaSpec } from '@deepseek-ai/dsh-tools';
import type { Context } from '@deepseek-ai/cordis';
import { z } from 'zod';
import type { ConversationSearchContext } from './search-tool.js';
import { installationFailureCode } from './installation-failure.js';

export const MANAGEMENT_TOOLS = {
  enable: 'astron_skillhub_enable', disable: 'astron_skillhub_disable',
  availability: 'astron_skillhub_check_availability',
  list: 'astron_skillhub_list_installed', check: 'astron_skillhub_check_update',
  upgrade: 'astron_skillhub_upgrade', uninstall: 'astron_skillhub_uninstall', operation: 'astron_skillhub_operation',
} as const;
export type ManagementAction = keyof typeof MANAGEMENT_TOOLS;
const reference = z.string().min(1).max(2048);
export const managementRequests = {
  enable: z.object({ sourceRevision: reference, itemKey: reference }).strict(),
  disable: z.object({ sourceRevision: reference, itemKey: reference }).strict(),
  availability: z.union([z.object({ sourceRevision: reference, itemKey: reference }).strict(),
    z.object({ name: z.string().min(1).max(128).regex(/^[a-z0-9]+(?:-[a-z0-9]+)*$/) }).strict()]),
  list: z.object({}).strict(),
  check: z.object({ sourceRevision: reference, itemKey: reference, version: reference.optional() }).strict(),
  upgrade: z.object({ sourceRevision: reference, itemKey: reference, version: reference.optional() }).strict(),
  uninstall: z.object({ sourceRevision: reference, itemKey: reference }).strict(),
  operation: z.object({ operationId: z.uuid(), hostInstanceId: z.uuid() }).strict(),
};
export type ManagementRequest = { name?: string; sourceRevision?: string; itemKey?: string; version?: string; operationId?: string; hostInstanceId?: string };


/** The model needs outcomes, not local paths, diagnostics or executable confirmation references. */
function modelView(result: Record<string, unknown>): object {
  if (result.kind === 'skillhub-availability') {
    const value = result.diagnosis as Record<string, unknown> | undefined;
    const installation = value?.installation as Record<string, unknown> | undefined;
    const invocation = value?.invocation as Record<string, unknown> | undefined;
    return { schemaVersion: 1, kind: 'skillhub-availability', diagnosis: value && {
      checkedAt: value.checkedAt, name: value.name, scope: value.scope, association: value.association, integrity: value.integrity,
      discovery: value.discovery, reason: value.reason, installationState: value.installationState,
      installation: installation && { namespace: installation.namespace, slug: installation.slug, version: installation.version, scope: installation.scope },
      invocation: invocation && { userInvocable: invocation.userInvocable, modelInvocable: invocation.modelInvocable },
    } };
  }
  const operation = result.operation;
  if (!operation || typeof operation !== 'object') return { ...result, sourceLabel: undefined, confirmationRef: undefined };
  const value = operation as Record<string, unknown>;
  const context = value.context as Record<string, unknown> | undefined;
  return { ...result, sourceLabel: undefined, confirmationRef: undefined, operation: {
    operationId: value.operationId, hostInstanceId: context?.hostInstanceId, kind: value.kind, phase: value.phase,
    namespace: value.namespace, slug: value.slug, version: value.version, targetLabel: value.targetLabel,
    discovery: value.discovery, files: value.files, recovery: value.recovery,
  } };
}

const modelGuidance = 'List, update-check and operation results are point-in-time snapshots, not live installation state. UI buttons or external actions can change an installation after a result was returned. Before answering what is currently installed or its current version, or starting a new upgrade/uninstall/enable/disable request, call astron_skillhub_list_installed again in the current turn and use that fresh result; a prior operation receipt proves only that operation, not the current installation. Internal itemKey, sourceRevision, operationId and hostInstanceId values, including shortened prefixes, are tool-routing data only: never quote, compare or use them as evidence in user-facing explanations. Explain changes only with the returned skill name, installation scope, version and state. The tool card already presents the details: give a short conclusion instead of duplicating it as a long table or list. Report only returned state; do not fabricate confirmation or installation integrity.';

function modelInstructions(result: Record<string, unknown>): string {
  const common = `\n${modelGuidance}`;
  if (result.status === 'accepted') return common + ' REAL HUMAN APPROVAL IS ALREADY COMPLETE and the mutation IS ALREADY SUBMITTED. DO NOT ask for approval again and DO NOT call upgrade/uninstall/enable/disable again. Use astron_skillhub_operation with the returned operationId and hostInstanceId to check progress. If not terminal, report in progress; completed means report the confirmed outcome. Accepted alone is not completion.';
  if (result.status === 'awaiting-confirmation') return common + ' The existing confirmation card is awaiting the user; no mutation has been submitted. Direct the user to that card, do not create another confirmation or submit another tool call.';
  if (result.status === 'cancelled') return common + ' The user cancelled. Do not resubmit or ask again unless the user explicitly starts a new request.';
  return common;
}

export function registerManagementTools(tools: Pick<Context['tools'], 'get' | 'register'>, run: (
  action: ManagementAction, input: ManagementRequest, context: ConversationSearchContext, signal: AbortSignal,
) => Promise<object>) {
  const disposers: (() => void)[] = [];
  try {
    for (const action of Object.keys(MANAGEMENT_TOOLS) as ManagementAction[]) {
      const name = MANAGEMENT_TOOLS[action];
      if (tools.get(name)) throw new Error('SkillHub management tool name is already registered.');
      const parameters: ParameterSchemaSpec = action === 'availability' ? { name: { type: 'string' as const }, sourceRevision: { type: 'string' as const }, itemKey: { type: 'string' as const } } : action === 'list' ? {} : action === 'operation' ? {
        operationId: { type: 'string' as const, required: true as const }, hostInstanceId: { type: 'string' as const, required: true as const },
      } : { sourceRevision: { type: 'string' as const, required: true as const }, itemKey: { type: 'string' as const, required: true as const },
        ...(['uninstall', 'enable', 'disable'].includes(action) ? {} : { version: { type: 'string' as const } }) };
      disposers.push(tools.register(defineTool({ name,
        description: `${action === 'availability' ? 'READ ONLY availability diagnosis: supply EITHER actual skill name OR both sourceRevision and itemKey from a fresh list, never both modes. Name lookup observes the current session winner only and does not associate Registry ownership. Integrity, discovery and user/model invocation are separate facts; unknown is not missing or not installed. This result does not authorize installation, removal or repair. ' : ''}${action} a current-source SkillHub managed installation. List is NOT all machine skills. Use exact itemKey and sourceRevision from the list; if the user has not uniquely selected a skill and installation scope, ask the user to select exactly ONE installation location using the native question UI when available, never choose arbitrarily. Only single-location operations are supported: NEVER offer or recommend both scopes, all locations, or batch updates/removals. Do not display registry URLs or any internal reference, even shortened prefixes. User scope is for the same OS user across projects on this Host. Project scope is this workspace only, not synchronization to teammates or other machines. Upgrade/uninstall/enable/disable ALWAYS require real human UI confirmation, even if scope was explicitly requested. Never supply paths, confirmation flags or plan IDs. Optional version must be a newer exact version explicitly requested; no downgrade/force. Operation reads only a prior receipt belonging to this conversation. Do not retry mutations with a new call to check status. ${modelGuidance}`,
        parameters,
        output: { schema: { type: 'object', additionalProperties: true, properties: {} },
          render: (_args, result) => [{ type: 'text', text: JSON.stringify(modelView(result)) + modelInstructions(result) }],
          presentationMeta: (_args, result) => result },
        async execute(args, exec) {
          const request = managementRequests[action].parse(args);
          const session = exec.agent?.session;
          if (!session?.id || !session.header.cwd || !exec.callId) throw new Error('Trusted conversation required.');
          try { return z.record(z.string(), z.json()).parse(JSON.parse(JSON.stringify(await run(action, request, { sessionId: session.id, cwd: session.header.cwd, callId: exec.callId }, exec.signal)))); }
          catch (error) { return { schemaVersion: 1, kind: 'skillhub-management', status: 'not-confirmed', action,
            toolCallId: exec.callId, reason: installationFailureCode(error) }; }
        },
      })));
    }
  } catch (error) { disposers.forEach(dispose => dispose()); throw error; }
  return () => disposers.forEach(dispose => dispose());
}
