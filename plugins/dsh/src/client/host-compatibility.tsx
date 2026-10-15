import { useQuery } from '@tanstack/react-query';
import type { RemoteResult } from '@deepseek-ai/dsh-typert-protocol';
import type { HostCompatibility } from '../host/types.js';

export interface CompatibilityServices {
  compatibility(): Promise<RemoteResult<HostCompatibility>>;
}

/** One Host query is shared by page and conversation controls within the plugin client. */
export function useWritableHost(services: CompatibilityServices) {
  const query = useQuery({ queryKey: ['skillhub-compatibility'], retry: false,
    queryFn: async () => {
      const response = await services.compatibility();
      if (!response.ok) throw new Error('Host compatibility could not be read');
      return response.value;
    } });
  return { ...query, canWrite: !query.isError && query.data?.dependencyBaseline === 'matched'
    && query.data.localWritesAllowed === true };
}

export function HostWriteStatus({ state }: { state: ReturnType<typeof useWritableHost> }) {
  if (state.canWrite) return null;
  if (state.isPending) return <p role="status">正在核验 DSH 写入兼容性…</p>;
  return <div role="alert">
    <p>{state.isError ? '写入兼容性暂时无法确认，不能提交新操作。'
      : '当前 DSH 依赖组合未通过核验，写入已禁用。原操作结果仍可查询。'}</p>
    <button disabled={state.isFetching} onClick={() => { void state.refetch(); }}>重新核验写入兼容性</button>
  </div>;
}
