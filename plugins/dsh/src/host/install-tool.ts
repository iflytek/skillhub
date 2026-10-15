import { defineTool } from '@deepseek-ai/dsh-tools';
import type { Context } from '@deepseek-ai/cordis';
import { conversationInstallRequest } from './conversation-install-request.js';
import type { ConversationInstallRequest } from './conversation-install-request.js';
import type { ConversationSearchContext } from './search-tool.js';
import type { NativeOperation } from './types.js';
import { installationFailureCode, installationFailureCodes } from './installation-failure.js';

export const INSTALL_TOOL_NAME = 'astron_skillhub_install';
const string = { type: 'string', required: true } as const;
const acceptedSchema = {
  type: 'object', additionalProperties: false, properties: {
    schemaVersion: { type: 'integer', const: 1, required: true },
    kind: { type: 'string', const: 'skillhub-install', required: true },
    status: { type: 'string', const: 'accepted', required: true },
    toolCallId: string, operationId: string, hostInstanceId: string,
    namespace: string, slug: string, version: string, targetLabel: string,
    sourceLabel: string, sourceRevision: string, hostLabel: string, createdAt: string,
  },
} as const;
export const installOutputSchema = { oneOf: [acceptedSchema, {
  type: 'object', additionalProperties: false, properties: {
    schemaVersion: { type: 'integer', const: 1, required: true },
    kind: { type: 'string', const: 'skillhub-install', required: true },
    status: { type: 'string', const: 'not-confirmed', required: true },
    toolCallId: string,
    code: { type: 'string', enum: installationFailureCodes, required: true },
  },
}] } as const;

export function registerInstallTool(tools: Pick<Context['tools'], 'get' | 'register'>, install: (
  request: ConversationInstallRequest, context: ConversationSearchContext, signal: AbortSignal,
) => Promise<NativeOperation | undefined>) {
  if (tools.get(INSTALL_TOOL_NAME)) throw new Error('SkillHub install tool name is already registered.');
  return tools.register(defineTool({
    name: INSTALL_TOOL_NAME,
    description: 'Install a SkillHub skill only when the user explicitly requests installation. Searching or asking about a skill is not an install request. If multiple candidates match and the user says "that skill", "the document skill", or otherwise does not uniquely identify one, ask which namespace/slug or result number they mean and stop without calling this tool. Never pick the first, a random, or an arbitrary selectionRef. An explicit request to install does not resolve an ambiguous target. Keep selectionRef, toolCallId, operationId, hostInstanceId, and sourceRevision internal to tool calls and correlation; never display them or ask the user to copy them. Describe the skill by namespace/slug, version, and current status. For a prior search/detail choice use its selectionRef; never replace an expired or missing reference with coordinates. Use namespace and slug only for a new complete coordinate explicitly supplied by the user. User scope is the default; use project only when requested. This tool does not upgrade, uninstall, or run skills. An accepted result is not installation success. The DSH installation result card checks completion for the user; there is no operation-query tool. After acceptance, summarize that installation has started and direct the user to that card. Do not use shell commands, filesystem inspection, invented tools, or another install call to check progress.',
    parameters: {
      selectionRef: { type: 'string', description: 'Host-issued reference for the selected search/detail item. Mutually exclusive with namespace and slug.' },
      namespace: { type: 'string', description: 'Namespace of a newly specified complete coordinate. Requires slug; no selectionRef.' },
      slug: { type: 'string', description: 'Slug of a newly specified complete coordinate. Requires namespace; no selectionRef.' },
      scope: { type: 'string', enum: ['user', 'project'], description: 'Install scope; defaults to user. Never supply file paths.' },
    },
    output: {
      schema: installOutputSchema,
      render: (_args, result) => [{ type: 'text', text: JSON.stringify(result) + '\nInternal references (selectionRef, toolCallId, operationId, hostInstanceId, sourceRevision) are for tool use and correlation only. Do not display them or ask the user to copy them; report skill coordinates, version, and status instead.' + (result.status === 'accepted'
        ? '\nInstallation request accepted; this is not proof of successful installation or skill execution. The DSH installation result card automatically checks and displays the current outcome. Tell the user installation has started and to check that card. No operation-query tool is available. Do not call shell, read files, invent a status tool, or submit installation again to check progress.'
        : '\nInstallation acceptance was not confirmed. Do not create a new tool call to retry or substitute coordinates for an expired reference. Explain the code and check the original operation or reopen the skill detail.') }],
      presentationMeta: (_args, result) => result,
    },
    async execute(args, exec) {
      const parsed = conversationInstallRequest.safeParse(args);
      if (!parsed.success) throw new Error('Provide either selectionRef or namespace and slug, plus optional user/project scope. No other fields are allowed.');
      const session = exec.agent?.session;
      if (!session?.id || !session.header.cwd || !exec.callId) throw new Error('SkillHub install requires a trusted conversation context.');
      let operation: NativeOperation | undefined;
      try {
        operation = await install(parsed.data, { sessionId: session.id, cwd: session.header.cwd, callId: exec.callId }, exec.signal);
      } catch (error) {
        // Core failures can contain Host paths; they must not enter the model transcript.
        return { schemaVersion: 1 as const, kind: 'skillhub-install' as const, status: 'not-confirmed' as const,
          toolCallId: exec.callId, code: installationFailureCode(error) };
      }
      if (!operation) return { schemaVersion: 1 as const, kind: 'skillhub-install' as const, status: 'not-confirmed' as const,
        toolCallId: exec.callId, code: 'UNCONFIRMED' as const };
      return { schemaVersion: 1 as const, kind: 'skillhub-install' as const, status: 'accepted' as const,
        toolCallId: exec.callId, operationId: operation.operationId, hostInstanceId: operation.context.hostInstanceId,
        namespace: operation.namespace, slug: operation.slug, version: operation.version, targetLabel: operation.targetLabel,
        sourceLabel: operation.context.sourceLabel, sourceRevision: operation.context.sourceRevision,
        hostLabel: operation.context.hostLabel, createdAt: operation.context.createdAt };
    },
  }));
}
