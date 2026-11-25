import { expect, test } from 'bun:test';
import { QueryClient } from '@tanstack/react-query';
import { invalidateRegistrySkill, queryHasSource, readCacheOptions } from '../src/client/read-cache.js';

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

test('a skill write refreshes its Registry views without evicting unrelated cached results', async () => {
  const client = new QueryClient();
  const versions = (source: string, slug: string) => ['skillhub-module', JSON.stringify(['session', source, 'team', slug, '1.0']), 'versions', 0];
  const affected = [
    ['account-owned', 'source', 'team', 0], ['account-team', 'source', 'team', ''],
    ['skillhub-catalog', 'source', '', 'newest', ''], ['skillhub-detail', 'source', 'team', 'example', '1.0'],
    versions('source', 'example'),
  ];
  const unaffected = [
    ['skillhub-catalog', 'another-source', '', 'newest', ''], ['account-team', 'source', 'other-team', ''],
    ['skillhub-detail', 'source', 'team', 'other', '1.0'], versions('another-source', 'example'), versions('source', 'other'),
  ];
  let generation = 1;
  const read = (queryKey: unknown[]) => client.fetchQuery({ ...readCacheOptions, queryKey, queryFn: () => generation });
  for (const key of [...affected, ...unaffected]) expect(await read(key)).toBe(1);
  generation++;
  invalidateRegistrySkill(client, 'source', 'team', 'example');
  for (const key of affected) expect(await read(key)).toBe(2);
  for (const key of unaffected) expect(await read(key)).toBe(1);
  client.clear();
});
