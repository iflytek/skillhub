import { expect, test } from 'bun:test';
import { renderToStaticMarkup } from 'react-dom/server';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { encodePackage, MAX_TRANSFER_BYTES, PackageImport, PackagePreviewContent, importProgressView, type PackageTransferServices } from '../src/client/package-transfer.js';
import { InstalledCardMetadata, installedCardVersions } from '../src/client/installed-card-metadata.js';
import type { InstalledSkillList } from '../src/host/types.js';
const item: InstalledSkillList['items'][number] = { itemKey: 'exact-install', namespace: 'local', slug: 'notes', version: 'local', scope: 'project', state: 'present', sourceKind: 'local-import', currentSource: false };
test('binary transfer encoding preserves byte values and enforces browser limit', () => {
  const bytes = new Uint8Array([0, 255, 128, 10]);
  expect([...atob(encodePackage(bytes))].map(char => char.charCodeAt(0))).toEqual([...bytes]);
  expect(() => encodePackage(new Uint8Array())).toThrow();
  expect(() => encodePackage(new Uint8Array(MAX_TRANSFER_BYTES + 1))).toThrow();
});
test('imported cards retain local provenance without suggesting registry updates or vlocal', () => {
  expect(installedCardVersions([item]).label).toBe('仅此项目');
  const html = renderToStaticMarkup(<InstalledCardMetadata items={[item]} />);
  expect(html).toContain('仅此项目');
  expect(html).not.toContain('@local');
  expect(html).not.toContain('vlocal');
  expect(html).not.toContain('本地导入');
});
test('initial transfer UI has one secondary import action, no automatic confirmation or host calls', () => {
  let calls = 0;
  const transfer: PackageTransferServices = { preview: async () => { calls++; throw new Error(); }, confirm: async () => { calls++; throw new Error(); } };
  const services = { sessionId: 'session', operation: async () => { calls++; throw new Error(); } };
  const html = renderToStaticMarkup(<QueryClientProvider client={new QueryClient()}><PackageImport transfer={transfer} revision="source" contextKey="session/source" services={services} onCompleted={() => {}} /></QueryClientProvider>);
  expect(html).toContain('导入技能包');
  expect(html).not.toContain('技能详情更多操作');
  expect(html).toContain('accept=".zip,application/zip"');
  expect(html).not.toContain('确认导入');
  expect(calls).toBe(0);
});

test('long package preview preserves all text inside a keyboard accessible summary region', () => {
  const name = 'a'.repeat(128);
  const summary = 'https://example.test/' + 'x'.repeat(980);
  const html = renderToStaticMarkup(<PackagePreviewContent preview={{ previewId: 'opaque', name, summary, files: 3, bytes: 1024, expiresAt: 'later' }} />);
  expect(html).toContain(name);
  expect(html).toContain(summary);
  expect(html).toContain('aria-label="技能说明" tabindex="0"');
  expect(html).toContain('astron-sh-package-summary');
  expect(html).not.toContain('展开');
  expect(html).toContain('3 个文件');
});

test('import progress distinguishes verification failures and real completion from confirmation', () => {
  expect(importProgressView(undefined, false, false)).toEqual({ label: '等待确认导入', state: 'ready' });
  expect(importProgressView(undefined, true, false)).toEqual({ label: '正在确认导入', state: 'running' });
  expect(importProgressView(undefined, false, true)).toEqual({ label: '导入状态待核验', state: 'stopped' });
});

test('import result only reports success for completed observations and preserves uncertain recovery', () => {
  const completed = { phase: 'completed' as const, warningCount: 0, recovery: 'not-needed' as const };
  expect(importProgressView(completed, false, false)).toEqual({ label: '导入成功', state: 'complete' });
  expect(importProgressView(completed, false, true).state).toBe('stopped');
  expect(importProgressView({ ...completed, phase: 'failed' }, false, false)).toEqual({ label: '导入未完成', state: 'stopped' });
  expect(importProgressView({ ...completed, phase: 'unknown' }, false, false).state).toBe('stopped');
  expect(importProgressView({ ...completed, recovery: 'unknown' }, false, false).state).toBe('stopped');
  expect(importProgressView({ ...completed, warningCount: 1 }, false, false).label).toBe('导入完成，需核验告警');
  expect(importProgressView({ ...completed, phase: 'staging' }, false, false)).toEqual({ label: '正在校验技能包', state: 'running' });
});
