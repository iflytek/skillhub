import { expect, test } from 'bun:test';
import { renderToStaticMarkup } from 'react-dom/server';
import { safeSkillWebUrl, SkillWebsiteLink } from '../src/client/skill-website-link.js';
import { RegistryAdapter } from '../src/host/registry-adapter.js';
import { Config } from '../src/host/config.js';
import { Context } from '@deepseek-ai/cordis';
import SkillHubService from '@astron-team/skillhub-dsh';

test('uses the complete API URL including legitimate page query and fragment without a fallback', () => {
  const webUrl = 'https://pages.example/custom/skill?version=2&lang=zh#examples';
  expect(safeSkillWebUrl(webUrl)).toBe(webUrl);
  const html = renderToStaticMarkup(<SkillWebsiteLink webUrl={webUrl} enabled />);
  expect(html).toContain('在官网查看'); expect(html).toContain('astron-sh-button-outline');
  expect(html).toContain('target="_blank"'); expect(html).toContain('rel="noopener noreferrer"');
  expect(html).toContain('version=2&amp;lang=zh#examples');
  expect(renderToStaticMarkup(<SkillWebsiteLink webUrl={webUrl} enabled={false} />)).toBe('');
});

test.each([undefined, null, '', ' ', 'javascript:alert(1)', 'file:///tmp/a', '//example.test/a', '/space/demo/a', 'https://user:secret@example.test', 'https://example.test\\other', 'https://exam\nple.test', 'https://', '\nhttps://example.test', 'https://example.test\t'])('missing or unsafe API link is hidden: %s', webUrl => {
  expect(safeSkillWebUrl(webUrl)).toBeUndefined();
  expect(renderToStaticMarkup(<SkillWebsiteLink webUrl={webUrl} enabled />)).toBe('');
});

test.each([undefined, null, '', 'https://web.example/custom?tab=read#intro', 'javascript:bad', 42])('optional metadata URL does not break old or malformed-source details: %j', async webUrl => {
  const fetcher = Object.assign(async () => Response.json({ data: { id: 1, namespace: 'demo', slug: 'writer', displayName: 'Writer', summary: '', ...(webUrl === undefined ? {} : { webUrl }) } }), { preconnect: fetch.preconnect });
  const metadata = await new RegistryAdapter('https://registry.example', fetcher).metadata('demo', 'writer');
  expect(metadata.id).toBe(1);
  expect(metadata.webUrl).toBe(typeof webUrl === 'number' ? undefined : webUrl);
});

test('obsolete website setting cannot change Registry configuration or add a link override', async () => {
  const oldConfiguration = { registryUrl: 'https://registry.example', websiteUrl: 'https://old.example' };
  expect(Config(oldConfiguration).get().registryUrl).toBe('https://registry.example');
  const ctx = new Context();
  const fiber = ctx.plugin(SkillHubService, oldConfiguration);
  try {
    await fiber;
    expect(ctx.skillhub.getContext().registryUrl).toBe(oldConfiguration.registryUrl);
    expect(ctx.skillhub.getContext()).not.toHaveProperty('websiteUrl');
  } finally { await fiber.dispose(); }
});
