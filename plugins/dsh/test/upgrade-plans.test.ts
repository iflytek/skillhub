import { expect, test } from 'bun:test';
import { randomUUID } from 'node:crypto';
import { UpgradePlans } from '../src/host/upgrade-plans.js';
import type { UpgradePlanSnapshot } from '../src/host/upgrade-plans.js';

const context = { sessionId: 'session', cwd: '/project', sourceRevision: 'source' };
const snapshot: UpgradePlanSnapshot = {
  baseline: { status: 'ready', inventoryItem: { registry: 'https://registry.example', namespace: 'demo', slug: 'writer', targets: [] }, targets: [] },
  candidate: { namespace: 'demo', slug: 'writer', version: '2', versionId: 2, fingerprint: 'new', downloadUrl: '/download' },
};

test('upgrade plans bind immutable snapshots, five-minute expiry and one request identity', () => {
  let now = 1_000_000;
  const plans = new UpgradePlans(() => now);
  const input = structuredClone(snapshot);
  const plan = plans.issue(context, input);
  expect(Date.parse(plan.expiresAt)).toBe(now + 300_000);
  input.candidate.version = 'changed';
  const read = plans.read(plan.planId, context);
  expect(read.candidate.version).toBe('2');
  read.candidate.version = 'mutated';
  expect(plans.read(plan.planId, context).candidate.version).toBe('2');
  for (const changed of [{ ...context, sessionId: 'other' }, { ...context, cwd: '/other' }, { ...context, sourceRevision: 'other' }]) {
    expect(() => plans.read(plan.planId, changed)).toThrow('Prepare the operation plan again');
  }
  expect(() => plans.claim(plan.planId, context, 'bad')).toThrow('Invalid request key');
  const key = randomUUID();
  expect(plans.claim(plan.planId, context, key)).toEqual(snapshot);
  expect(plans.claim(plan.planId, context, key)).toEqual(snapshot);
  expect(() => plans.claim(plan.planId, context, randomUUID())).toThrow('another request');
  now += 299_999;
  expect(plans.read(plan.planId, context)).toEqual(snapshot);
  now++;
  expect(() => plans.read(plan.planId, context)).toThrow('Prepare the operation plan again');
  const next = plans.issue(context, snapshot);
  plans.clear();
  expect(() => plans.read(next.planId, context)).toThrow();
});
