import type { NativeOperation } from '../host/types.js';

const outcomes = { enabled: '已启用', disabled: '已停用', installed: '安装完成', upgraded: '升级完成', removed: '已移除',
  'stale-record-removed': '失效记录已清理', restored: '已恢复', unknown: '结果待核验' };
const warnings = { 'backup-cleanup': '备份清理需要核验。', 'removal-cleanup': '卸载隔离文件清理需要核验。',
  'verification-required': '操作日志含有需要进一步核验的告警。' };

/** Historical operation details; never infer them from the current installed list. */
export function OperationDetails({ operation }: { operation: NativeOperation }) {
  const details = operation.resultDetails;
  if (!details) return null;
  return <details style={{ overflowWrap: 'anywhere' }}><summary>原操作结果详情</summary>
    <p>操作前版本：{details.beforeVersion ?? '未记录或不适用'}</p>
    <p>操作后版本：{details.afterVersion ?? '未确认或不适用'}</p>
    {details.targets.length ? <ul>{details.targets.map((target, index) =>
      <li key={index}>{target.label}：{outcomes[target.outcome]}</li>)}</ul> : <p>原日志未记录目标明细。</p>}
    {details.warnings.length > 0 && <ul aria-label="操作告警">{details.warnings.map((warning, index) =>
      <li key={index}>{warnings[warning]}</li>)}</ul>}
    <p>以上为原操作记录，当前安装状态请查看已安装列表。</p>
  </details>;
}
