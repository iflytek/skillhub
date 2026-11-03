import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { githubSyncAdminApi, githubTeamSourcesApi } from '@/api/client'
import { toast } from '@/shared/lib/toast'
import { Button } from '@/shared/ui/button'
import { Textarea } from '@/shared/ui/textarea'

export function GithubSourceIgnoreEditor({ sourceId, namespaceSlug }: {
  sourceId: number
  namespaceSlug?: string
}) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const queryKey = ['github-sync', namespaceSlug ?? 'platform', 'source-ignore', sourceId]
  const patterns = useQuery({
    queryKey,
    queryFn: () => namespaceSlug
      ? githubTeamSourcesApi.ignorePatterns(namespaceSlug, sourceId)
      : githubSyncAdminApi.ignorePatterns(sourceId),
  })
  const save = useMutation({
    mutationFn: (ignorePatterns: string[]) => namespaceSlug
      ? githubTeamSourcesApi.updateIgnorePatterns(namespaceSlug, sourceId, ignorePatterns)
      : githubSyncAdminApi.updateIgnorePatterns(sourceId, ignorePatterns),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey })
      queryClient.invalidateQueries({ queryKey: namespaceSlug
        ? ['github-sync', 'team', namespaceSlug, 'resolved'] : ['github-sync', 'sources'] })
    },
  })

  if (patterns.isLoading) return <p className="text-sm text-muted-foreground">{t('githubSync.loading')}</p>
  if (patterns.error) return <p role="alert" className="text-sm text-destructive">{t('githubSync.loadFailed')}: {patterns.error.message}</p>
  if (!patterns.data) return null
  return <IgnoreForm key={`${sourceId}:${JSON.stringify(patterns.data.ignorePatterns)}`}
    initial={patterns.data.ignorePatterns ?? []} saving={save.isPending}
    onSave={async (next) => {
      try {
        await save.mutateAsync(next)
        toast.success(t('githubSync.saved'))
      } catch (error) {
        toast.error(t('githubSync.saveFailed'), error instanceof Error ? error.message : '')
      }
    }} />
}

function IgnoreForm({ initial, saving, onSave }: {
  initial: string[]
  saving: boolean
  onSave: (patterns: string[]) => Promise<void>
}) {
  const { t } = useTranslation()
  const [value, setValue] = useState(initial.join('\n'))
  const submit = (event: FormEvent) => {
    event.preventDefault()
    void onSave(value.split('\n').map((line) => line.trim()).filter(Boolean))
  }
  return (
    <form data-analytics-id="github.ignore.save" className="space-y-2 border-t pt-4" onSubmit={submit}>
      <label htmlFor="github-source-ignore-patterns" className="block text-sm font-medium">{t('githubSync.ignorePaths')}</label>
      <Textarea id="github-source-ignore-patterns" value={value} onChange={(event) => setValue(event.target.value)}
        placeholder="examples/legacy\narchive" rows={4} />
      <p className="text-xs text-muted-foreground">{t('githubSync.ignorePathsHelp')}</p>
      <Button type="submit" size="sm" className="sync-action" disabled={saving}>{t('githubSync.save')}</Button>
    </form>
  )
}
