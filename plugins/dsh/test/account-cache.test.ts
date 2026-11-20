import { expect, test } from 'bun:test';
import { QueryClient } from '@tanstack/react-query';
import { queryHasSource } from '../src/client/read-cache.js';

test('logout removes direct and compound private query keys for only the old source', () => {
  const client = new QueryClient();
  const revision = 'old-source';
  const oldIdentity = JSON.stringify(['session', revision, 'team', 'skill', '1.0']);
  const nextIdentity = JSON.stringify(['session', 'new-source', 'team', 'skill', '1.0']);
  const keys = [
    ['account-owned', revision, 'team', 0],
    ['skillhub-module', oldIdentity, 'reviews', 0],
    ['skillhub-files', oldIdentity, 'detail-token'],
    ['skillhub-file', oldIdentity, 'detail-token', 'private.md'],
    ['skillhub-module', nextIdentity, 'reviews', 0],
  ];
  for (const key of keys) client.setQueryData(key, 'private fixture');
  client.removeQueries({ predicate: query => queryHasSource(query.queryKey, revision) });
  for (const key of keys.slice(0, 4)) expect(client.getQueryData(key)).toBeUndefined();
  expect(client.getQueryData<string>(keys[4]!)).toBe('private fixture');
  expect(queryHasSource(['module', '[malformed'], revision)).toBe(false);
  expect(queryHasSource(['module', 'prefix-old-source-suffix'], revision)).toBe(false);
  client.clear();
});
