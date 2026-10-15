import { useQuery } from '@tanstack/react-query';
import type { InstalledServices } from './installed-skills.js';

/** Disabled queries run only after an explicit click; rows and details share the result. */
export function useInstalledUpdate(services: InstalledServices, sourceRevision: string, itemKey?: string) {
  return useQuery({ queryKey: ['skillhub-update-check', services.sessionId, sourceRevision, itemKey],
    enabled: false, retry: false,
    queryFn: async ({ signal }) => {
      if (!itemKey) throw new Error('请先选择安装记录。');
      const response = await services.checkUpdate(sourceRevision, itemKey, signal);
      if (!response.ok) throw new Error('更新检查未完成，请刷新安装记录后重试。');
      return response.value;
    } });
}
