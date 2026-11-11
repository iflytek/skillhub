import { QueryClient, QueryClientProvider, QueryCache, MutationCache } from '@tanstack/react-query'
import { RouterProvider } from '@tanstack/react-router'
import { Toaster } from '@/shared/components/toaster'
import { handleApiError } from '@/shared/lib/api-error'
import { refreshSkillPermissionsAfterForbidden } from '@/features/skill/refresh-skill-permissions'
import { router } from './router'
import { useSitePresentation } from '@/shared/hooks/use-site-presentation'
import { PresentationDocument } from '@/features/site-presentation/presentation-document'
import { useTranslation } from 'react-i18next'

/**
 * Front-end application root.
 *
 * It wires TanStack Query, TanStack Router, and the global toaster so all pages share one query
 * cache and one navigation context.
 */
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30 * 1000,
      retry: (failureCount, error) => {
        // Don't retry on 401/403/404
        if (error instanceof Error && /HTTP (401|403|404)/.test(error.message)) {
          return false
        }
        return failureCount < 1
      },
      refetchOnWindowFocus: false,
    },
  },
  queryCache: new QueryCache({
    onError: (error, query) => {
      if (query.meta?.skipGlobalErrorHandler) {
        return
      }
      handleApiError(error)
    },
  }),
  mutationCache: new MutationCache({
    onError: async (error, _variables, _context, mutation) => {
      await refreshSkillPermissionsAfterForbidden(queryClient, error)
      if (mutation.meta?.skipGlobalErrorHandler) {
        return
      }
      // Only auto-handle if the mutation doesn't have its own onError
      if (!mutation.options.onError) {
        handleApiError(error)
      }
    },
  }),
})

function ConfiguredApplication() {
  const { isPending, isFetched, isError, refetch } = useSitePresentation()
  const { t } = useTranslation()
  if (isPending && !isFetched) return <div role="status" className="flex min-h-screen items-center justify-center text-sm text-muted-foreground">{t('common.loading')}</div>
  return <>{isError && <div role="alert" className="border-b bg-secondary px-4 py-3 text-center text-sm text-foreground">{t('sitePresentation.defaultsUnavailable')} <button type="button" className="ml-2 underline underline-offset-4" onClick={() => void refetch()}>{t('sitePresentation.retry')}</button></div>}<PresentationDocument /><RouterProvider router={router} /><Toaster /></>
}

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <ConfiguredApplication />
    </QueryClientProvider>
  )
}
