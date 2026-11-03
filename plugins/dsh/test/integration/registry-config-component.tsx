import { createRoot } from 'react-dom/client';
import type { ConfigForm, ConfigFormSnapshot } from '@deepseek-ai/dsh-client-ui-settings/client';
import type { SkillHubConfig } from '../../src/host/types.js';
import { ConfigPage } from '../../src/client/config-page.js';
import { NativeControlStyles } from '../../src/client/native-control-styles.js';

let snapshot: ConfigFormSnapshot<SkillHubConfig> = { status: 'ready', revision: 1, writable: true, base: {}, user: {}, value: {
  registryUrl: 'https://a.example', savedRegistries: [{ name: '来源 A', url: 'https://a.example' }, { name: '来源 B', url: 'https://b.example' }],
  detailTabs: { versions: 'auto', reviews: 'auto', securityAudit: 'auto' },
} };
const listeners = new Set<() => void>();
const writes: { revision: number | undefined; paths: string[][] }[] = [];
let releaseWrite: (() => void) | undefined;
let holdWrites = false;
const notify = () => listeners.forEach(listener => listener());
const form: ConfigForm<SkillHubConfig> = {
  getSnapshot: () => snapshot,
  subscribe: listener => { listeners.add(listener); return () => { listeners.delete(listener); }; },
  mutate: async (operations, revision) => {
    writes.push({ revision, paths: operations.map(op => op.path.map(String)) });
    if (holdWrites) await new Promise<void>(resolve => { releaseWrite = resolve; });
    if (!snapshot.writable || revision !== snapshot.revision) return false;
    const value = structuredClone(snapshot.value!);
    for (const operation of operations) {
      if (operation.op !== 'set') throw new Error('unexpected operation');
      const path = operation.path.map(String);
      if (path.length === 1 && path[0] === 'registryUrl') value.registryUrl = String(operation.value);
      else if (path.length === 1 && path[0] === 'savedRegistries') value.savedRegistries = operation.value as SkillHubConfig['savedRegistries'];
      else if (path[0] === 'detailTabs') Object.assign(value.detailTabs, { [path[1]!]: operation.value });
      else throw new Error('unexpected field');
    }
    snapshot = { ...snapshot, value, revision: snapshot.revision! + 1 }; notify(); return true;
  },
  set: async () => false, unset: async () => false,
};
Object.assign(window, { registryFixture: {
  holdWrites: () => { holdWrites = true; },
  releaseWrite: () => { holdWrites = false; releaseWrite?.(); },
  state: () => snapshot,
  writes: () => writes,
  replaceSources: () => { snapshot = { ...snapshot, revision: snapshot.revision! + 1, value: { ...snapshot.value!, registryUrl: 'https://b.example', savedRegistries: [{ name: '替换来源', url: 'https://replacement.example' }, { name: '来源 B', url: 'https://b.example' }] } }; notify(); },
  legacyCurrent: () => { snapshot = { ...snapshot, revision: snapshot.revision! + 1, value: { ...snapshot.value!, registryUrl: 'https://legacy.example', savedRegistries: [{ name: '来源 A', url: 'https://a.example' }, { name: '来源 B', url: 'https://b.example' }] } }; notify(); },
  readonly: () => { snapshot = { ...snapshot, writable: false }; notify(); },
  unavailable: () => { snapshot = { ...snapshot, status: 'unavailable', value: undefined }; notify(); },
  externalSource: () => { snapshot = { ...snapshot, revision: snapshot.revision! + 1, value: { ...snapshot.value!, registryUrl: snapshot.value!.savedRegistries![1]!.url } }; notify(); },
} });
createRoot(document.getElementById('root')!).render(<main className="skillhub-controls"><NativeControlStyles />
  <ConfigPage form={form} testConnection={async () => { throw new Error('not exercised'); }} />
</main>);
