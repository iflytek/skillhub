import { useSyncExternalStore } from 'react';

type Entry = 'main' | 'sidebar' | 'conversation' | 'configuration';
/** Client entry availability is independent of the Host's write authorization. */
export class UiAvailability {
  private value: Readonly<Record<Entry, boolean>> = { main: false, sidebar: false, conversation: false, configuration: false };
  private readonly listeners = new Set<() => void>();
  readonly getSnapshot = () => this.value;
  readonly subscribe = (listener: () => void) => {
    this.listeners.add(listener);
    return () => { this.listeners.delete(listener); };
  };
  enable(entry: Entry): () => void {
    this.update(entry, true);
    let active = true;
    return () => { if (active) { active = false; this.update(entry, false); } };
  }
  private update(entry: Entry, available: boolean) {
    if (this.value[entry] === available) return;
    this.value = { ...this.value, [entry]: available };
    for (const listener of this.listeners) listener();
  }
}

export function unavailableEntries(value: ReturnType<UiAvailability['getSnapshot']>): string[] {
  const messages: string[] = [];
  const catalog = value.main && value.sidebar;
  if (!catalog) messages.push(value.conversation ? '技能广场入口暂不可用，可继续使用对话中的技能卡片。' : '技能广场入口暂不可用。');
  if (!value.conversation) messages.push(catalog ? '对话技能卡片暂不可用，可在技能广场浏览和管理技能。' : '对话技能卡片暂不可用。');
  if (!value.configuration) messages.push('配置入口暂不可用，当前来源设置保持不变。');
  return messages;
}

export function UiAvailabilityNotice({ availability }: { availability: UiAvailability }) {
  const value = useSyncExternalStore(availability.subscribe, availability.getSnapshot);
  const messages = unavailableEntries(value);
  if (!messages.length) return null;
  return <aside role="status" aria-label="SkillHub 部分可用" style={{ padding: 12, border: '1px solid #8885', borderRadius: 8, marginBlock: 12 }}>
    <strong>SkillHub 部分功能暂不可用</strong>
    {messages.map(message => <p key={message} style={{ marginBlock: 6 }}>{message}</p>)}
  </aside>;
}
