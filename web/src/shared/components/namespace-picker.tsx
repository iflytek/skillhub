import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '@/shared/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/shared/ui/dialog'
import { Input } from '@/shared/ui/input'
import { useDebounce } from '@/shared/hooks/use-debounce'
import { useMyNamespacesPage } from '@/shared/hooks/use-namespace-queries'

const PAGE_SIZE = 20

interface NamespacePickerProps {
  id?: string
  accessibleLabel?: string
  value: string
  onValueChange: (slug: string) => void
  status?: 'ACTIVE' | 'FROZEN' | 'ARCHIVED'
  disabled?: boolean
  emptyValueLabel?: string
}

/**
 * Server-paged namespace selector that keeps request and render size bounded.
 */
export function NamespacePicker({
  id,
  accessibleLabel,
  value,
  onValueChange,
  status,
  disabled = false,
  emptyValueLabel,
}: NamespacePickerProps) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const debouncedSearch = useDebounce(search.trim(), 300)
  const triggerText = value ? `@${value}` : emptyValueLabel ?? t('namespacePicker.placeholder')
  const query = useMyNamespacesPage({
    page,
    size: PAGE_SIZE,
    ...(status ? { status } : {}),
    ...(debouncedSearch ? { q: debouncedSearch } : {}),
  }, open)
  const totalPages = query.data ? Math.max(Math.ceil(query.data.total / query.data.size), 1) : 1

  useEffect(() => {
    setPage(0)
  }, [debouncedSearch, status])

  const selectNamespace = (slug: string) => {
    onValueChange(slug)
    setOpen(false)
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button
          id={id}
          type="button"
          variant="outline"
          disabled={disabled}
          aria-label={accessibleLabel ? `${accessibleLabel}: ${triggerText}` : undefined}
          className="w-full justify-start"
        >
          {triggerText}
        </Button>
      </DialogTrigger>
      <DialogContent aria-label={t('namespacePicker.title')}>
        <DialogHeader>
          <DialogTitle>{t('namespacePicker.title')}</DialogTitle>
          <DialogDescription>{t('namespacePicker.description')}</DialogDescription>
        </DialogHeader>

        <Input
          type="search"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          aria-label={t('namespacePicker.search')}
          placeholder={t('namespacePicker.searchPlaceholder')}
        />

        <div className="min-h-44 space-y-2">
          {emptyValueLabel ? (
            <button
              type="button"
              onClick={() => selectNamespace('')}
              className="flex w-full items-center rounded-lg border border-border px-4 py-3 text-left text-sm font-medium hover:bg-secondary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              {emptyValueLabel}
            </button>
          ) : null}
          {query.isLoading ? (
            <p className="py-8 text-center text-sm text-muted-foreground">{t('namespacePicker.loading')}</p>
          ) : query.error ? (
            <div className="space-y-3 py-8 text-center">
              <p className="text-sm text-destructive">{t('namespacePicker.error')}</p>
              <Button type="button" size="sm" variant="outline" onClick={() => query.refetch()}>
                {t('namespacePicker.retry')}
              </Button>
            </div>
          ) : query.data?.items.length ? (
            query.data.items.map((namespace) => (
              <button
                key={namespace.id}
                type="button"
                aria-label={`${namespace.displayName} (@${namespace.slug})`}
                onClick={() => selectNamespace(namespace.slug)}
                className="flex w-full items-center justify-between rounded-lg border border-border px-4 py-3 text-left text-sm hover:bg-secondary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                <span className="font-medium">{namespace.displayName}</span>
                <span className="text-muted-foreground">@{namespace.slug}</span>
              </button>
            ))
          ) : (
            <p className="py-8 text-center text-sm text-muted-foreground">{t('namespacePicker.empty')}</p>
          )}
        </div>

        <div className="flex items-center justify-between gap-3">
          <Button
            type="button"
            size="sm"
            variant="outline"
            disabled={page === 0}
            onClick={() => setPage((current) => Math.max(current - 1, 0))}
          >
            {t('namespacePicker.previous')}
          </Button>
          <span className="text-xs text-muted-foreground">
            {t('namespacePicker.page', { current: page + 1, total: totalPages })}
          </span>
          <Button
            type="button"
            size="sm"
            variant="outline"
            disabled={page + 1 >= totalPages}
            onClick={() => setPage((current) => current + 1)}
          >
            {t('namespacePicker.next')}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  )
}
