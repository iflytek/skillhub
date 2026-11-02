import { useCallback, useState, useSyncExternalStore } from 'react';
import type { ConfigForm } from '@deepseek-ai/dsh-client-ui-settings/client';
import type { SkillHubConfig } from '../host/types.js';
import { savedRegistryOptions, sameRegistry } from './registry-config.js';
import { SelectField } from './ui.js';

/** Optional settings service; catalog and local management also work without it. */
export class RegistryConfiguration {
  private form: ConfigForm<SkillHubConfig> | undefined;
  private listeners = new Set<() => void>();
  readonly getSnapshot = () => this.form;
  readonly subscribe = (listener: () => void) => {
    this.listeners.add(listener);
    return () => { this.listeners.delete(listener); };
  };
  bind(form: ConfigForm<SkillHubConfig>): () => void {
    this.form = form;
    for (const listener of this.listeners) listener();
    return () => {
      if (this.form !== form) return;
      this.form = undefined;
      for (const listener of this.listeners) listener();
    };
  }
}
export function RegistrySelector({ configuration }: { configuration: RegistryConfiguration }) {
  const form = useSyncExternalStore(configuration.subscribe, configuration.getSnapshot);
  return form ? <ConnectedSelector form={form} /> : <SelectField label="技能来源" value="unavailable" disabled onValueChange={() => {}}
    options={[{ value: 'unavailable', label: '来源配置不可用' }]} />;
}
function ConnectedSelector({ form }: { form: ConfigForm<SkillHubConfig> }) {
  const subscribe = useCallback((listener: () => void) => form.subscribe(listener), [form]);
  const read = useCallback(() => form.getSnapshot(), [form]);
  const state = useSyncExternalStore(subscribe, read);
  const [pending, setPending] = useState(false);
  const [message, setMessage] = useState('');
  const options = state.value ? savedRegistryOptions(state.value) : [];
  const selected = options.findIndex(entry => sameRegistry(entry.url, state.value?.registryUrl ?? ''));
  async function select(value: string) {
    if (pending || !state.writable || state.revision === undefined || !state.value) return;
    const url = value === 'disconnected' ? '' : options[Number(value)]?.url;
    if (url === undefined) return;
    setPending(true); setMessage('');
    try {
      const accepted = await form.mutate([{ op: 'set', path: ['registryUrl'], value: url }], state.revision);
      if (!accepted) setMessage('来源已被其他页面修改，请重新选择。');
    } catch { setMessage('切换失败，请重试。'); }
    finally { setPending(false); }
  }
  return <div className="astron-sh-registry-selector">
    <SelectField label="技能来源" value={selected < 0 ? 'disconnected' : String(selected)}
      disabled={pending || !state.writable || state.status !== 'ready'} onValueChange={value => void select(value)}
      options={[...options.map((entry, index) => ({ value: String(index), label: entry.name })), { value: 'disconnected', label: '未连接来源' }]} />
    {message && <span role="alert">{message}</span>}
  </div>;
}
