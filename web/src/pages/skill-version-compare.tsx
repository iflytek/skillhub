import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams, useSearch } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { useSkillVersionCompare, useSkillVersions } from '@/shared/hooks/use-skill-queries'
import { Input } from '@/shared/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'

function toDiffSectionId(path: string) {
  return `diff-${encodeURIComponent(path)}`
}

function getChangeTypeLabel(t: (key: string) => string, changeType: string) {
  if (changeType === 'ADDED') {
    return t('skillCompare.changeTypeAdded')
  }
  if (changeType === 'REMOVED') {
    return t('skillCompare.changeTypeRemoved')
  }
  return t('skillCompare.changeTypeModified')
}

export function SkillVersionComparePage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { namespace, slug } = useParams({ from: '/space/$namespace/$slug/compare' })
  const { from, to } = useSearch({ from: '/space/$namespace/$slug/compare' }) as { from: string; to: string }
  const [keyword, setKeyword] = useState('')
  const [activePath, setActivePath] = useState<string | null>(null)
  const { data: versions, isLoading: isVersionsLoading } = useSkillVersions(namespace, slug)

  const publishedVersions = useMemo(
    () => (versions ?? []).filter((version) => version.status === 'PUBLISHED'),
    [versions]
  )

  const hasEnoughVersions = publishedVersions.length >= 2
  const defaultFrom = publishedVersions[1]?.version ?? publishedVersions[0]?.version ?? ''
  const defaultTo = publishedVersions[0]?.version ?? ''

  const normalizedVersions = useMemo(() => {
    if (!hasEnoughVersions) {
      return { from: '', to: '' }
    }

    const isPublishedVersion = (value: string) => publishedVersions.some((version) => version.version === value)
    let nextFrom = from && isPublishedVersion(from) ? from : defaultFrom
    let nextTo = to && isPublishedVersion(to) ? to : defaultTo

    if (nextFrom === nextTo) {
      const alternative = publishedVersions.find((version) => version.version !== nextFrom)?.version ?? ''
      if (!from && !to) {
        nextFrom = defaultFrom
        nextTo = defaultTo
      } else if (from && !to) {
        nextTo = alternative
      } else if (!from && to) {
        nextFrom = alternative
      } else {
        nextTo = alternative
      }
    }

    return { from: nextFrom, to: nextTo }
  }, [defaultFrom, defaultTo, from, hasEnoughVersions, publishedVersions, to])

  useEffect(() => {
    if (!hasEnoughVersions || !normalizedVersions.from || !normalizedVersions.to) {
      return
    }
    if (from === normalizedVersions.from && to === normalizedVersions.to) {
      return
    }
    navigate({
      to: '/space/$namespace/$slug/compare',
      params: { namespace, slug },
      search: { from: normalizedVersions.from, to: normalizedVersions.to },
      replace: true,
    })
  }, [from, hasEnoughVersions, namespace, navigate, normalizedVersions.from, normalizedVersions.to, slug, to])

  const { data, isLoading: isCompareLoading, error } = useSkillVersionCompare(
    namespace,
    slug,
    normalizedVersions.from,
    normalizedVersions.to,
    hasEnoughVersions && !!normalizedVersions.from && !!normalizedVersions.to
  )

  const filteredFiles = useMemo(() => {
    const files = data?.files ?? []
    if (!keyword.trim()) {
      return files
    }
    return files.filter((file) => file.path.toLowerCase().includes(keyword.trim().toLowerCase()))
  }, [data?.files, keyword])

  useEffect(() => {
    if (!activePath && filteredFiles.length > 0) {
      setActivePath(filteredFiles[0].path)
      return
    }
    if (activePath && !filteredFiles.some((file) => file.path === activePath)) {
      setActivePath(filteredFiles[0]?.path ?? null)
    }
  }, [activePath, filteredFiles])

  if (isVersionsLoading) {
    return <div className="py-10 text-sm text-muted-foreground">{t('skillCompare.loading')}</div>
  }

  if (!hasEnoughVersions) {
    return <div className="py-10 text-sm text-muted-foreground">{t('skillCompare.notEnoughPublishedVersions')}</div>
  }

  if (isCompareLoading) {
    return <div className="py-10 text-sm text-muted-foreground">{t('skillCompare.loading')}</div>
  }

  if (error || !data) {
    return <div className="py-10 text-sm text-muted-foreground">{t('skillCompare.errorLoadingCompare')}</div>
  }

  return (
    <div className="mx-auto flex max-w-7xl flex-col gap-6 px-4 py-6 lg:flex-row">
      <aside className="w-full shrink-0 space-y-4 rounded-xl border border-border/60 p-4 lg:w-80">
        <div>
          <h1 className="text-lg font-semibold text-foreground">{t('skillCompare.pageTitle')}</h1>
          <p className="mt-1 text-sm text-muted-foreground">v{data.from} → v{data.to}</p>
        </div>

        <div className="grid grid-cols-3 gap-2 rounded-lg border border-border/50 p-3 text-sm">
          <div>
            <div className="text-xs text-muted-foreground">{t('skillCompare.totalFiles')}</div>
            <div className="font-medium text-foreground">{data.summary.totalFiles}</div>
          </div>
          <div>
            <div className="text-xs text-muted-foreground">{t('skillCompare.addedLines')}</div>
            <div className="font-medium text-emerald-600">+{data.summary.addedLines}</div>
          </div>
          <div>
            <div className="text-xs text-muted-foreground">{t('skillCompare.removedLines')}</div>
            <div className="font-medium text-rose-600">-{data.summary.removedLines}</div>
          </div>
        </div>

        <div className="space-y-2">
          <label className="text-xs text-muted-foreground" htmlFor="compare-from-select">
            {t('skillCompare.baseVersion')}
          </label>
          <Select
            value={normalizedVersions.from}
            onValueChange={(value) => {
              navigate({
                to: '/space/$namespace/$slug/compare',
                params: { namespace, slug },
                search: { from: value, to: normalizedVersions.to },
              })
            }}
          >
            <SelectTrigger id="compare-from-select" aria-label={t('skillCompare.baseVersion')}>
              <SelectValue placeholder={t('skillCompare.baseVersion')} />
            </SelectTrigger>
            <SelectContent>
              {publishedVersions.map((version) => (
                <SelectItem key={version.id} value={version.version} disabled={version.version === normalizedVersions.to}>
                  v{version.version}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div className="space-y-2">
          <label className="text-xs text-muted-foreground" htmlFor="compare-to-select">
            {t('skillCompare.headVersion')}
          </label>
          <Select
            value={normalizedVersions.to}
            onValueChange={(value) => {
              navigate({
                to: '/space/$namespace/$slug/compare',
                params: { namespace, slug },
                search: { from: normalizedVersions.from, to: value },
              })
            }}
          >
            <SelectTrigger id="compare-to-select" aria-label={t('skillCompare.headVersion')}>
              <SelectValue placeholder={t('skillCompare.headVersion')} />
            </SelectTrigger>
            <SelectContent>
              {publishedVersions.map((version) => (
                <SelectItem key={version.id} value={version.version} disabled={version.version === normalizedVersions.from}>
                  v{version.version}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <Input
          value={keyword}
          onChange={(event) => setKeyword(event.target.value)}
          placeholder={t('skillCompare.searchFiles')}
          aria-label={t('skillCompare.searchFiles')}
        />

        <nav className="space-y-1 text-sm" aria-label={t('skillCompare.fileList')}>
          {filteredFiles.length > 0 ? (
            filteredFiles.map((file, index) => {
              const isActive = activePath ? activePath === file.path : index === 0
              return (
                <a
                  key={file.path}
                  href={`#${toDiffSectionId(file.path)}`}
                  onClick={() => setActivePath(file.path)}
                  aria-current={isActive ? 'true' : undefined}
                  data-active={isActive ? 'true' : 'false'}
                  className={[
                    'block rounded-md border px-3 py-2 font-mono text-xs',
                    isActive
                      ? 'border-primary/50 bg-primary/10 text-foreground'
                      : 'border-border/50 text-muted-foreground hover:text-foreground',
                  ].join(' ')}
                >
                  {file.path}
                </a>
              )
            })
          ) : (
            <div className="rounded-md border border-dashed border-border/50 px-3 py-2 text-xs text-muted-foreground">
              {keyword.trim() ? t('skillCompare.noFilesFound') : t('skillCompare.noFilesFound')}
            </div>
          )}
        </nav>
      </aside>

      <main className="min-w-0 flex-1 space-y-6">
        {filteredFiles.length > 0 ? (
          filteredFiles.map((file) => (
            <section key={file.path} id={toDiffSectionId(file.path)} className="rounded-xl border border-border/60 p-4">
              <header className="mb-3 flex items-center justify-between gap-4">
                <div className="font-mono text-sm text-foreground">{file.path}</div>
                <div className="text-xs text-muted-foreground">{getChangeTypeLabel(t, file.changeType)}</div>
              </header>

              {file.binary ? (
                <div className="text-sm text-muted-foreground">{t('skillCompare.binaryFile')}</div>
              ) : file.truncated ? (
                <div className="text-sm text-muted-foreground">{t('skillCompare.truncatedFile')}</div>
              ) : (
                <div className="overflow-hidden rounded-md border border-border/40 font-mono text-sm">
                  {file.hunks.flatMap((hunk) => hunk.lines).map((line, index) => (
                    <div
                      key={`${file.path}-${index}`}
                      className={[
                        'grid grid-cols-[56px_56px_1fr] gap-0',
                        line.type === 'ADD' ? 'bg-emerald-50 dark:bg-emerald-950/30' : '',
                        line.type === 'DELETE' ? 'bg-rose-50 dark:bg-rose-950/30' : '',
                      ].join(' ')}
                    >
                      <span className="select-none border-r border-border/30 px-2 py-0.5 text-right text-xs text-muted-foreground">
                        {line.oldLineNumber ?? ''}
                      </span>
                      <span className="select-none border-r border-border/30 px-2 py-0.5 text-right text-xs text-muted-foreground">
                        {line.newLineNumber ?? ''}
                      </span>
                      <span
                        className={[
                          'whitespace-pre overflow-x-auto px-3 py-0.5',
                          line.type === 'ADD' ? 'text-emerald-700 dark:text-emerald-400' : '',
                          line.type === 'DELETE' ? 'text-rose-700 dark:text-rose-400' : '',
                          line.type === 'CONTEXT' ? 'text-foreground' : '',
                        ].join(' ')}
                      >
                        {line.type === 'ADD' ? '+' : line.type === 'DELETE' ? '-' : ' '}
                        {line.content}
                      </span>
                    </div>
                  ))}
                </div>
              )}
            </section>
          ))
        ) : (
          <div className="rounded-xl border border-border/60 p-8 text-sm text-muted-foreground">
            {t('skillCompare.noFilesFound')}
          </div>
        )}
      </main>
    </div>
  )
}
