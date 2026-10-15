import { expect, test } from 'bun:test';
import { catalogErrorMessage } from '../src/client/catalog-error.js';

test.each([
  ['ANONYMOUS_DENIED', '不允许匿名读取'],
  ['RATE_LIMITED', '限流'],
  ['REGISTRY_UNAVAILABLE', '服务暂时不可用'],
  ['NETWORK_ERROR', '网络'],
  ['INVALID_SOURCE', '地址无效'],
  ['NOT_FOUND', '未找到'],
  ['CANCELLED', '已取消'],
  ['INVALID_RESPONSE', '数据格式'],
  ['RESPONSE_TOO_LARGE', '读取限制'],
])('catalog explains %s without displaying upstream diagnostics', (reason, expected) => {
  const message = catalogErrorMessage({ code: 'skillhub/registry-error', details: { reason, body: 'private diagnostic' } });
  expect(message).toContain(expected);
  expect(message).not.toContain('private diagnostic');
});

test('unknown or malformed errors use safe fallback messages', () => {
  for (const details of [undefined, null, 'private diagnostic', { reason: 'private diagnostic' }, { reason: {} }]) {
    expect(catalogErrorMessage({ code: 'skillhub/registry-error', details }))
      .toBe('无法读取 SkillHub，请检查来源地址或稍后重试。');
  }
  expect(catalogErrorMessage({ code: 'private diagnostic' })).toBe('请求失败，请稍后重试。');
  expect(catalogErrorMessage({ code: 'skillhub/context-stale' })).toContain('来源已变化');
  expect(catalogErrorMessage({ code: 'skillhub/source-not-configured' })).toContain('设置 SkillHub 地址');
});

test('rate limit hints only interpolate a validated wait duration', () => {
  expect(catalogErrorMessage({ code: 'skillhub/registry-error', details: { reason: 'RATE_LIMITED', retryAfterSeconds: 30 } }))
    .toContain('至少 30 秒后手动重试');
  for (const retryAfterSeconds of [-1, 0, 1.5, Infinity, 'private diagnostic']) {
    expect(catalogErrorMessage({ code: 'skillhub/registry-error', details: { reason: 'RATE_LIMITED', retryAfterSeconds } }))
      .toBe('SkillHub 来源暂时限流，请稍后重试。');
  }
});
