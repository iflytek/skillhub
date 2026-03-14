import { useTranslation } from 'react-i18next'
import { Button } from '@/shared/ui/button'

interface PaginationProps {
  page: number
  totalPages: number
  onPageChange: (page: number) => void
}

export function Pagination({ page, totalPages, onPageChange }: PaginationProps) {
  const { t } = useTranslation()
  return (
    <div className="flex items-center justify-center gap-3 py-4">
      <Button
        variant="outline"
        size="sm"
        onClick={() => onPageChange(page - 1)}
        disabled={page <= 0}
        className="min-w-[90px]"
      >
        {t('pagination.prev')}
      </Button>
      <div className="flex items-center gap-2 px-4 py-1.5 rounded-lg bg-secondary/40 text-sm font-medium text-foreground">
        <span className="text-muted-foreground">{t('pagination.pagePrefix')}</span>
        <span className="text-primary">{page + 1}</span>
        <span className="text-muted-foreground">/</span>
        <span>{totalPages}</span>
        {t('pagination.pageSuffix') && <span className="text-muted-foreground">{t('pagination.pageSuffix')}</span>}
      </div>
      <Button
        variant="outline"
        size="sm"
        onClick={() => onPageChange(page + 1)}
        disabled={page >= totalPages - 1}
        className="min-w-[90px]"
      >
        {t('pagination.next')}
      </Button>
    </div>
  )
}
