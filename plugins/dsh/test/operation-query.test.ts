import { expect, test } from 'bun:test';
import { OperationQueryError, retryOperationQuery, invalidateInstallationQueries } from '../src/client/use-installation-operation.js';

test('operation reads retry only explicitly transient failures, at most twice', () => {
  expect(retryOperationQuery(0, new OperationQueryError(true))).toBe(true);
  expect(retryOperationQuery(1, new OperationQueryError(true))).toBe(true);
  expect(retryOperationQuery(2, new OperationQueryError(true))).toBe(false);
  expect(retryOperationQuery(0, new OperationQueryError(false))).toBe(false);
  expect(retryOperationQuery(0, new Error('Unknown failure'))).toBe(false);
});


test('settled operations invalidate fresh receipt projections and existing installed queries only for their Session', async () => {
  const { QueryClient } = await import('@tanstack/react-query');
  const client = new QueryClient();
  const keys = [
    ['skillhub-installed', 'a', 'new-revision', 'receipt', 'host', 'operation', 'source'],
    ['skillhub-installed', 'a', 'new-revision'],
    ['skillhub-installed-detail', 'a', 'new-revision', 'item'],
    ['skillhub-receipt-context', 'a', 'host', 'operation', 'source', 'old-revision'],
  ];
  const other = ['skillhub-installed', 'b', 'new-revision'];
  for (const key of [...keys, other]) client.setQueryData(key, { present: true });
  await invalidateInstallationQueries(client, 'a');
  for (const key of keys) expect(client.getQueryState(key)?.isInvalidated).toBe(true);
  expect(client.getQueryState(other)?.isInvalidated).toBe(false);
  client.clear();
});
