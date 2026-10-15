import { expect, test } from 'bun:test';
import { OperationQueryError, retryOperationQuery } from '../src/client/use-installation-operation.js';

test('operation reads retry only explicitly transient failures, at most twice', () => {
  expect(retryOperationQuery(0, new OperationQueryError(true))).toBe(true);
  expect(retryOperationQuery(1, new OperationQueryError(true))).toBe(true);
  expect(retryOperationQuery(2, new OperationQueryError(true))).toBe(false);
  expect(retryOperationQuery(0, new OperationQueryError(false))).toBe(false);
  expect(retryOperationQuery(0, new Error('Unknown failure'))).toBe(false);
});
