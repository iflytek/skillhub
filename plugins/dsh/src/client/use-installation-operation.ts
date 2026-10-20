import { useSyncExternalStore } from 'react';
import { useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import type { NativeOperation } from '../host/types.js';
import type { InstallationServices } from './install-control.js';

export class OperationQueryError extends Error {
  constructor(readonly retryable: boolean) { super('安装状态暂时无法确认。'); }
}

/** Only transient read failures retry; writes and identity/ownership failures never do. */
export const retryOperationQuery = (failures: number, error: Error): boolean =>
  failures < 2 && error instanceof OperationQueryError && error.retryable;

const visible = () => typeof document === 'undefined' || document.visibilityState === 'visible';
const watchVisibility = (listener: () => void) => {
  document.addEventListener('visibilitychange', listener);
  return () => document.removeEventListener('visibilitychange', listener);
};
const settled = (operation: NativeOperation | null | undefined) => !!operation
  && ['completed', 'failed', 'recovery-required', 'unknown'].includes(operation.phase);


export const shouldRefreshInstallation = (previous: NativeOperation | null | undefined, next: NativeOperation | null | undefined): boolean =>
  settled(next) && previous?.phase !== next?.phase;

export function useInstallationOperation(services: Pick<InstallationServices, 'sessionId' | 'operation'>, host: string, operationId?: string) {
  const client = useQueryClient();
  const isVisible = useSyncExternalStore(watchVisibility, visible, () => true);
  return useQuery({ queryKey: ['skillhub-operation', services.sessionId, host, operationId], retry: retryOperationQuery, retryDelay: attempt => 500 * (attempt + 1),
    enabled: !!operationId && isVisible,
    queryFn: async () => {
      const previous = client.getQueryData<NativeOperation | null>(['skillhub-operation', services.sessionId, host, operationId]);
      const response = await services.operation(operationId!, host);
      if (!response.ok) {
        const details = response.error.details;
        throw new OperationQueryError(response.error.code === 'skillhub/installation-unavailable'
          && !!details && typeof details === 'object' && Reflect.get(details, 'retryable') === true);
      }
      if (shouldRefreshInstallation(previous, response.value)) {
        // One shared operation query refreshes local projections for both page and tool consumers.
        void invalidateInstallationQueries(client, services.sessionId);
      }
      return response.value;
    },
    refetchInterval: query => isVisible && !query.state.error && !settled(query.state.data) && query.state.data !== null ? 1000 : false,
    refetchIntervalInBackground: false });
}

/** Receipts and page projections share the same settled-operation refresh boundary. */
export async function invalidateInstallationQueries(client: QueryClient, sessionId: string): Promise<void> {
  await Promise.all([
    client.invalidateQueries({ queryKey: ['skillhub-installed', sessionId] }),
    client.invalidateQueries({ queryKey: ['skillhub-installed-detail', sessionId] }),
    client.invalidateQueries({ queryKey: ['skillhub-receipt-context', sessionId] }),
  ]);
}
