import { defineTool } from '@deepseek-ai/dsh-tools';
import type { Context } from '@deepseek-ai/cordis';
import type { ConversationSearchResult } from './search-result.js';

export const SEARCH_TOOL_NAME = 'astron_skillhub_search';
const string = { type: 'string', required: true } as const;
const integer = { type: 'integer', required: true } as const;
export const searchOutputSchema = {
  type: 'object', additionalProperties: false,
  properties: {
    schemaVersion: { type: 'integer', const: 1, required: true },
    kind: { type: 'string', const: 'skillhub-search', required: true },
    query: string, sourceRevision: string, sourceLabel: string, createdAt: string,
    total: integer, returned: integer, limit: integer,
    items: { type: 'array', required: true, items: {
      type: 'object', additionalProperties: false, properties: {
        namespace: string, slug: string, latestVersion: string, summary: string, selectionRef: string,
        installation: { type: 'object', additionalProperties: false, properties: {
          checkedAt: string, complete: { type: 'boolean', required: true },
          unavailable: { type: 'array', required: true, items: { type: 'string', enum: ['user', 'project'] } },
          records: { type: 'array', required: true, items: { type: 'object', additionalProperties: false, properties: {
            scope: { type: 'string', enum: ['user', 'project'], required: true }, version: string,
            state: { type: 'string', enum: ['present', 'missing', 'metadata-invalid', 'mismatch', 'unreadable'], required: true },
          } } },
        } },
      },
    } },
  },
} as const;

export interface ConversationSearchContext { sessionId: string; callId: string; cwd: string }
export type { ConversationSearchResult } from './search-result.js';

export function registerSearchTool(tools: Pick<Context['tools'], 'get' | 'register'>, search: (
  query: string, context: ConversationSearchContext, signal: AbortSignal,
) => Promise<ConversationSearchResult>) {
  if (tools.get(SEARCH_TOOL_NAME)) throw new Error('SkillHub search tool name is already registered.');
  return tools.register(defineTool({
    name: SEARCH_TOOL_NAME,
    description: 'Search public SkillHub skills when the user asks to find or browse skills. Searching does not install or run a skill. When multiple candidates match, require a unique namespace/slug or an unambiguous result number before installation; ask for clarification rather than choosing a candidate yourself. Use selectionRef only as an internal tool argument for a selected result. Never display selectionRef or sourceRevision to the user or ask the user to copy them; describe skills by namespace/slug, version, summary, and status. Do not substitute shell installation commands.',
    parameters: { q: { type: 'string', required: true, description: 'Search keywords; empty string lists recent skills.' } },
    output: {
      schema: searchOutputSchema,
      render: (_args, result) => [{ type: 'text', text: JSON.stringify(result) + '\nSearch only; no skill was installed or executed. If multiple candidates fit a later request such as "that skill", ask the user to identify one by namespace/slug or result number; do not choose a candidate or call install until clarified. selectionRef and sourceRevision are internal references: retain them for tool use, but do not display them or ask the user to copy them. Present a short numbered comparison with skill coordinates, versions, and summaries. Tell the user they can view details or reply with a result number and installation scope to request installation; opening details is optional. Installation is a search-time snapshot of current-Registry records in approved user/project roots. Only state=present means files were found for that recorded version and scope; it does not prove content integrity or that DSH is using that skill. Missing, metadata-invalid, mismatch and unreadable records are abnormal, not confirmed installations. Empty records mean no current-source record, never globally uninstalled; other sources or custom roots may contain the skill. complete=false or unavailable scopes mean status is unknown there; absent installation means historical status was not checked. Mention known installed versions and scopes in the comparison, and uncertainty when relevant. Recheck current installed status before making claims about an old snapshot. Do not install until explicitly requested.' }],
      presentationMeta: (_args, result) => result,
    },
    async execute(args, exec) {
      if (Object.keys(args).some(key => key !== 'q') || args.q.length > 1000) {
        throw new Error('Expected only q, at most 1000 characters.');
      }
      const session = exec.agent?.session;
      if (!session?.id || !session.header.cwd || !exec.callId) {
        throw new Error('SkillHub search requires a trusted conversation context.');
      }
      return search(args.q.trim(), { sessionId: session.id, cwd: session.header.cwd, callId: exec.callId }, exec.signal);
    },
  }));
}
