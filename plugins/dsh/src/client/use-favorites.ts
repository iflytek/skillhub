import { useLayoutEffect, useRef } from 'react';
import { useMutation, useQuery, useQueryClient, type QueryClient, type UseMutationOptions } from '@tanstack/react-query';
import type { TypertRemoteNamespaceMap } from '@deepseek-ai/dsh-typert-protocol';
import type { FavoriteList, FavoriteSkill } from '../host/types.js';
import { catalogErrorMessage } from './catalog-error.js';
import { favoriteSnapshot, favoritesKey } from './favorites.js';
import '../../lib/typert.remote-client.js';

type Remote = Pick<TypertRemoteNamespaceMap['skillhub'], 'listFavorites' | 'setFavorite'>;
interface FavoriteMutation { sourceRevision: string; item: FavoriteSkill; favorite: boolean; signal: AbortSignal }

function unwrap<T>(result: { ok: true; value: T } | { ok: false; error: { code: string; details?: unknown } }): T {
  if (result.ok) return result.value;
  throw new Error(catalogErrorMessage(result.error));
}

/** Share serialization across observers of the same source, including a detail/card pair. */
export function favoriteMutationOptions(remote: Pick<Remote, 'setFavorite'>, client: QueryClient, sourceRevision: string | undefined): UseMutationOptions<FavoriteList, Error, FavoriteMutation> {
  return {
    scope: { id: JSON.stringify(favoritesKey(sourceRevision)) }, retry: false,
    mutationFn: async input => {
      input.signal.throwIfAborted();
      return unwrap(await remote.setFavorite(input.sourceRevision, input.item, input.favorite, input.signal));
    },
    onMutate: input => client.cancelQueries({ queryKey: favoritesKey(input.sourceRevision), exact: true }),
    onSuccess: async (value, input) => {
      // Focus/reconnect may have started another read while the write was pending.
      await client.cancelQueries({ queryKey: favoritesKey(input.sourceRevision), exact: true });
      client.setQueryData(favoritesKey(input.sourceRevision), value);
    },
    // Success already returns the complete confirmed list. Only reconcile ambiguous failures,
    // including a committed write whose transport response was lost.
    onError: (_error, input) => client.invalidateQueries({ queryKey: favoritesKey(input.sourceRevision), exact: true }),
  };
}

export function useFavorites(remote: Remote, sourceRevision: string | undefined, enabled: boolean) {
  const client = useQueryClient();
  const controllers = useRef(new Set<AbortController>());
  useLayoutEffect(() => () => {
    for (const controller of controllers.current) controller.abort();
    controllers.current.clear();
  }, [sourceRevision]);
  const query = useQuery({ queryKey: favoritesKey(sourceRevision), enabled: enabled && !!sourceRevision,
    // Re-entry/focus/reconnect can discover changes from another Host; filtering stays in memory.
    retry: false, staleTime: 0,
    queryFn: async ({ signal }) => {
      if (!sourceRevision) throw new Error('请先连接技能来源。');
      return unwrap(await remote.listFavorites(sourceRevision, signal));
    },
  });
  const mutation = useMutation(favoriteMutationOptions(remote, client, sourceRevision));
  const currentMutation = mutation.variables?.sourceRevision === sourceRevision;
  const saving = currentMutation && mutation.isPending;
  const message = currentMutation && mutation.isError ? mutation.error.message : query.isError ? query.error.message : undefined;
  return {
    items: query.data?.items ?? [], loading: query.isPending, fetching: query.isFetching,
    ready: !!query.data && !query.isError, saving, error: message,
    retry: () => { mutation.reset(); void query.refetch(); },
    save: async (item: FavoriteSkill, favorite: boolean): Promise<boolean> => {
      if (!sourceRevision || !query.data || query.isError) return false;
      const controller = new AbortController(); controllers.current.add(controller);
      try {
        await mutation.mutateAsync({ sourceRevision, item: favoriteSnapshot(item), favorite, signal: controller.signal });
        return !controller.signal.aborted;
      } catch { return false; }
      finally { controllers.current.delete(controller); }
    },
  };
}
