import { expect, test } from 'bun:test';
import { UiAvailability, unavailableEntries } from '../src/client/ui-availability.js';

test('entry degradation reports missing surfaces and recovers without stale notices', () => {
  const availability = new UiAvailability();
  const changes: unknown[] = [];
  const unsubscribe = availability.subscribe(() => changes.push(availability.getSnapshot()));
  const entries = ['main', 'sidebar', 'conversation', 'configuration'] as const;
  const removers = entries.map(entry => availability.enable(entry));
  expect(unavailableEntries(availability.getSnapshot())).toEqual([]);
  const complete = availability.getSnapshot();
  removers[2]!();
  expect(unavailableEntries(availability.getSnapshot())).toEqual(['对话技能卡片暂不可用，可在技能广场浏览和管理技能。']);
  expect(complete.conversation).toBe(true);
  const restoreConversation = availability.enable('conversation');
  expect(unavailableEntries(availability.getSnapshot())).toEqual([]);
  removers[0]!(); removers[1]!();
  expect(unavailableEntries(availability.getSnapshot())).toEqual(['技能广场入口暂不可用，可继续使用对话中的技能卡片。']);
  removers[3]!();
  expect(unavailableEntries(availability.getSnapshot())).toContain('配置入口暂不可用，当前来源设置保持不变。');
  const before = changes.length;
  removers[3]!();
  expect(changes).toHaveLength(before);
  unsubscribe(); restoreConversation();
  expect(changes).toHaveLength(before);
  expect(unavailableEntries(availability.getSnapshot()).slice(0, 2)).toEqual(['技能广场入口暂不可用。', '对话技能卡片暂不可用。']);
});
