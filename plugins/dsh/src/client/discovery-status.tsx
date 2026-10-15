/** Discovery is an observation at query time, not a permanent installation or execution guarantee. */
export function DiscoveryStatus({ confirmed, checkedAt, refreshing, refresh }: {
  confirmed: boolean; checkedAt: number; refreshing: boolean; refresh(): void;
}) {
  return <div aria-label="技能发现核验">
    <p>{refreshing ? '正在核验 DSH 技能发现状态…'
      : confirmed ? '上次核验时，DSH 已发现此操作对应的技能内容。' : '尚未确认 DSH 发现此操作对应的技能内容。'}</p>
    {checkedAt > 0 && <p>最近核验：<time dateTime={new Date(checkedAt).toISOString()}>{new Date(checkedAt).toLocaleString()}</time></p>}
    <button disabled={refreshing} onClick={refresh}>刷新发现状态</button>
  </div>;
}
