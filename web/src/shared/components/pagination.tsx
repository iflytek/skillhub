import { useTranslation } from 'react-i18next'
import { Button } from '@/shared/ui/button'

interface PaginationProps {
  page: number
  totalPages: number
  onPageChange: (page: number) => void
}

type PageItem = number | 'ellipsis'

/**
 * Builds the list of page slots to render. Always shows the first and last page,
 * the current page, and one neighbour on each side, collapsing the rest into
 * ellipsis markers. Pages are 0-indexed internally; labels are 1-indexed.
 */
export function buildPageItems(current: number, totalPages: number): PageItem[] {
  if (totalPages <= 7) {
    return Array.from({ length: totalPages }, (_, i) => i)
  }

  const items: PageItem[] = []
  const first = 0
  const last = totalPages - 1
  const start = Math.max(first + 1, current - 1)
  const end = Math.min(last - 1, current + 1)

  items.push(first)
  if (start > first + 1) {
    items.push('ellipsis')
  }
  for (let i = start; i <= end; i += 1) {
    items.push(i)
  }
  if (end < last - 1) {
    items.push('ellipsis')
  }
  items.push(last)

  return items
}

export function Pagination({ page, totalPages, onPageChange }: PaginationProps) {
  const { t } = useTranslation()
  const pageItems = buildPageItems(page, totalPages)

  return (
    <nav aria-label={t('pagination.label')} className="flex flex-wrap items-center justify-center gap-2 py-4 sm:gap-3">
      <Button
        variant="outline"
        size="sm"
        onClick={() => onPageChange(page - 1)}
        disabled={page <= 0}
        className="min-w-0 flex-1 sm:flex-none sm:min-w-[90px]"
      >
        {t('pagination.prev')}
      </Button>

      <div className="order-3 flex w-full items-center justify-center gap-1.5 sm:order-none sm:w-auto">
        {pageItems.map((item, index) =>
          item === 'ellipsis' ? (
            <span
              key={`ellipsis-${index}`}
              className="px-2 text-sm text-muted-foreground select-none"
              aria-hidden="true"
            >
              …
            </span>
          ) : (
            <Button
              key={item}
              type="button"
              variant={item === page ? 'default' : 'ghost'}
              size="sm"
              onClick={() => onPageChange(item)}
              aria-label={t('pagination.goToPage', { page: item + 1 })}
              aria-current={item === page ? 'page' : undefined}
              className="min-w-[2.25rem] h-9 px-2"
            >
              {item + 1}
            </Button>
          ),
        )}
      </div>

      <Button
        variant="outline"
        size="sm"
        onClick={() => onPageChange(page + 1)}
        disabled={page >= totalPages - 1}
        className="min-w-0 flex-1 sm:flex-none sm:min-w-[90px]"
      >
        {t('pagination.next')}
      </Button>
    </nav>
  )
}
