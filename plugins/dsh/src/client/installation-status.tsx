import type { ConversationSearchResult } from '../host/search-result.js';
import { readableDate } from './skill-presentation.js';

type Installation = NonNullable<ConversationSearchResult['items'][number]['installation']>;
const scopeLabel = { user: '当前用户', project: '当前项目' };
const stateLabel = {
  present: '文件存在', missing: '安装目录缺失', 'metadata-invalid': '安装记录无法识别',
  mismatch: '安装记录与本地文件不一致', unreadable: '本地文件无法读取',
};

export function InstallationStatus({ installation, pending = false, snapshot = false }: {
  installation?: Installation; pending?: boolean; snapshot?: boolean;
}) {
  const present = installation?.records.filter(record => record.state === 'present') ?? [];
  const abnormal = installation?.records.filter(record => record.state !== 'present') ?? [];
  const partial = !installation?.complete || !!installation.unavailable.length;
  const label = pending ? '核对安装状态…' : !installation ? (snapshot ? '安装状态未记录' : '安装状态未知')
    : present.length ? `已安装 ${present.map(record => `v${record.version} · ${scopeLabel[record.scope]}`).join(' / ')}${abnormal.length ? ' · 部分异常' : ''}${partial ? ' · 部分未知' : ''}`
      : abnormal.length ? '安装记录异常' : partial ? '安装状态未知' : '无本来源记录';
  const detail = pending ? '正在读取当前来源的本地安装记录。'
    : !installation ? '没有可核验的安装状态，不代表该技能未安装。'
      : [
        ...installation.records.map(record => `${scopeLabel[record.scope]} · v${record.version} · ${stateLabel[record.state]}`),
        partial ? '部分安装记录无法读取，不能排除其他安装位置。' : '',
        !installation.records.length && !partial ? '未发现本来源安装记录；不代表其他来源或手动安装的同名技能不存在。' : '',
        snapshot ? `查询时状态：${readableDate(installation.checkedAt, true)}；不代表当前仍然安装。` : '文件存在不代表 DSH 已启用或技能已执行。',
      ].filter(Boolean).join('；');
  return <span className="astron-sh-installation-status" role="status" data-tone={pending || !installation ? 'neutral' : abnormal.length ? 'warning' : present.length ? 'success' : 'neutral'}
    aria-label={`${label}。${detail}`} title={detail}>{label}</span>;
}
