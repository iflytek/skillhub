import { expect, test } from 'bun:test';
import { randomUUID } from 'node:crypto';
import { installRequest, operationQuery } from '../src/host/install-request.js';

const input = { detailToken: 'detail', targetToken: 'target', requestKey: randomUUID(), hostInstanceId: randomUUID(),
  mode: 'new' as const, initiatedFrom: 'plaza' as const };

test('installation wire validation rejects paths, malformed identities and unbounded references', () => {
  expect(installRequest.parse(input)).toEqual(input);
  for (const value of [null, {}, { ...input, path: '/tmp/untrusted' }, { ...input, downloadUrl: 'https://other.example' },
    { ...input, requestKey: 'invalid' }, { ...input, hostInstanceId: '' }, { ...input, detailToken: '' },
    { ...input, targetToken: 'x'.repeat(2049) }, { ...input, mode: 'force' }, { ...input, initiatedFrom: 'shell' }]) {
    expect(installRequest.safeParse(value).success).toBe(false);
  }
  expect(installRequest.parse({ ...input, mode: 'retry' }).requestKey).toBe(input.requestKey);
  expect(operationQuery.safeParse({ operationId: randomUUID(), hostInstanceId: input.hostInstanceId }).success).toBe(true);
  expect(operationQuery.safeParse({ operationId: '../../private', hostInstanceId: input.hostInstanceId }).success).toBe(false);
});
