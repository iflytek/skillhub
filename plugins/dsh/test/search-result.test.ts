import { describe, expect, test } from 'bun:test';
import type { ConversationSearchResult } from '../src/host/search-tool.ts';
import { parseSearchResult } from '../src/client/search-result.ts';

const snapshot = {
  schemaVersion: 1, kind: 'skillhub-search', query: 'pdf',
  sourceRevision: '01900000-0000-4000-8000-000000000001', sourceLabel: 'https://registry.example',
  createdAt: '2026-10-13T00:00:00Z', total: 1, returned: 1, limit: 20,
  items: [{ namespace: 'team', slug: 'pdf', latestVersion: '1.0.0', summary: 'Read PDF',
    selectionRef: '01900000-0000-4000-8000-000000000002' }],
} satisfies ConversationSearchResult;
describe('persisted search card boundary', () => {
  test('preserves the original source, timestamp and fixed version', () => {
    expect(parseSearchResult(snapshot)).toEqual(snapshot);
  });
  test.each([
    { ...snapshot, schemaVersion: 2 },
    { ...snapshot, returned: 0 },
    { ...snapshot, total: 0 },
    { ...snapshot, sourceLabel: 'javascript:alert(1)' },
    { ...snapshot, sourceLabel: 'https://user:secret@example.com' },
    { ...snapshot, sourceLabel: 'https://example.com?token=secret' },
    { ...snapshot, items: [{ ...snapshot.items[0], path: '/private' }] },
    { ...snapshot, items: [{ ...snapshot.items[0], selectionRef: 'untrusted' }] },
    { ...snapshot, createdAt: 'yesterday' },
    null,
  ])('rejects unsupported or malformed history without guessing an action', value => {
    expect(parseSearchResult(value)).toBeUndefined();
  });
});
