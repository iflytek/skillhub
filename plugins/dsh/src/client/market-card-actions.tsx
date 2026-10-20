import { useState } from 'react';
import type { InstalledSkillList } from '../host/types.js';
import type { InstalledServices } from './installed-skills.js';
import { useInstalledUpdate } from './installed-update.js';
import { Button, SelectField } from './ui.js';

type InstalledItem = InstalledSkillList['items'][number];
export function MarketCardActions({ records, services, sourceRevision, verified, onInstall, onManage }: {
  records: InstalledItem[]; services?: InstalledServices; sourceRevision: string; verified: boolean;
  onInstall(): void; onManage(item: InstalledItem, intent: 'remove' | 'upgrade'): void;
}) {
  if (!records.length) return <Button size="sm" variant="default" disabled={!verified} onClick={onInstall}>安装</Button>;
  return services && <ManagedActions records={records} services={services} sourceRevision={sourceRevision} verified={verified} onManage={onManage} />;
}
function ManagedActions({ records, services, sourceRevision, verified, onManage }: {
  records: InstalledItem[]; services: InstalledServices; sourceRevision: string; verified: boolean;
  onManage(item: InstalledItem, intent: 'remove' | 'upgrade'): void;
}) {
  const [scope, setScope] = useState('choose');
  const item = records.length === 1 ? records[0] : records.find(record => record.scope === scope);
  const update = useInstalledUpdate(services, sourceRevision, item?.itemKey);
  const removable = verified && !!item && ['present', 'missing'].includes(item.state);
  const upgradable = verified && item?.state === 'present' && !update.isFetching && !update.isError && update.data?.state === 'upgradable';
  return <>
    {records.length > 1 && <SelectField label="选择要管理的安装范围" value={scope} onValueChange={setScope}
      options={[{ value: 'choose', label: '选择范围' }, ...records.map(record => ({ value: record.scope, label: record.scope === 'user' ? '所有项目' : '仅此项目' }))]} />}
    {upgradable ? <Button size="sm" variant="default" onClick={() => { if (item) onManage(item, 'upgrade'); }}>升级</Button>
      : <Button size="sm" variant="ghost" disabled={!verified || item?.state !== 'present' || update.isFetching}
        title={update.isError ? update.error.message : update.data?.state === 'current' ? '已是当前版本' : update.data ? '暂不能确认可升级版本' : '核对来源发布版本和安装指纹'}
        onClick={() => { void update.refetch(); }}>{update.isFetching ? '检查中…' : update.isError ? '重试更新' : update.data?.state === 'current' ? '已是最新' : update.data ? '查看更新' : '检查更新'}</Button>}
    <Button size="sm" variant="outline" disabled={!removable} onClick={() => { if (item) onManage(item, 'remove'); }}>{item?.state === 'missing' ? '清理记录' : '卸载'}</Button>
  </>;
}
