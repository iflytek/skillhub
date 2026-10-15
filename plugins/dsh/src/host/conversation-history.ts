import type { SessionLogSnapshot } from '@deepseek-ai/dsh-session-query';
import type { SelectionScope, SelectionSnapshot } from './conversation-selections.js';
import { parseSearchResult } from './search-result.js';
import { SEARCH_TOOL_NAME } from './search-tool.js';

/** Coordinates come only from a validated Host log, never from a Client payload. */
export function historicalSearchCandidate(history: SessionLogSnapshot, scope: SelectionScope,
  source: string, callId: string, selectionRef: string): Pick<SelectionSnapshot, 'namespace' | 'slug' | 'version'> | undefined {
  if (history.session.id !== scope.sessionId || history.session.cwd !== scope.cwd) return;
  const calls = history.events.filter(event => event.type === 'tool/call' && event.data.callId === callId);
  const results = history.events.filter(event => event.type === 'tool/result' && event.data.message.source.callId === callId);
  if (calls.length !== 1 || results.length !== 1) return;
  const call = calls[0]!;
  const result = results[0]!;
  if (call.type !== 'tool/call' || result.type !== 'tool/result') return;
  if (history.events.indexOf(call) < history.inheritedEventCount || history.events.indexOf(result) < history.inheritedEventCount
    || history.events.indexOf(result) <= history.events.indexOf(call)
    || call.data.name !== SEARCH_TOOL_NAME || result.data.message.isError || result.data.error
    || result.data.message.toolCallId !== callId || result.data.message.source.kind !== 'tool'
    || call.data.turn !== result.data.turn || call.data.step !== result.data.step) return;
  const metadata = parseSearchResult(result.data.meta);
  if (!metadata || metadata.sourceLabel !== source) return;
  const items = metadata.items.filter(item => item.selectionRef === selectionRef);
  if (items.length !== 1) return;
  const item = items[0]!;
  return { namespace: item.namespace, slug: item.slug, version: item.latestVersion };
}
