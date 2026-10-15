import { describe, expect, test } from 'bun:test';
import { installedNavigationFor, type InstalledNavigation } from '../src/client/installed-navigation.js';

describe('installed navigation context', () => {
  test('retains the same context but discards filters and selection across session or source changes', () => {
    const owner: { installedNavigation?: InstalledNavigation } = {};
    const first = installedNavigationFor(owner, 'session-a', 'source-1');
    first.scope = 'project'; first.keyword = 'document'; first.triggerKey = 'old-target'; first.listScroll = 640;
    expect(installedNavigationFor(owner, 'session-a', 'source-1')).toBe(first);
    const next = installedNavigationFor(owner, 'session-b', 'source-1');
    expect(next).not.toBe(first);
    expect(next.scope).toBe('all'); expect(next.keyword).toBe('');
    expect(next.triggerKey).toBeNull(); expect(next.listScroll).toBe(0);
    next.keyword = 'other';
    const changed = installedNavigationFor(owner, 'session-b', 'source-2');
    expect(changed.keyword).toBe(''); expect(changed.selected).toBeNull();
    expect(installedNavigationFor(owner, 'session-a', 'source-1')).not.toBe(first);
  });
});
