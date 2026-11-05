import { useId, type FormEvent } from 'react'
import { Search } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import type { ReviewCenterFilters } from './review-center-filters'
import { REVIEW_KINDS, REVIEW_ORIGINS, REVIEW_STATUSES } from './review-center-filters'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'

interface ReviewFilterOption { value: string; label: string }
interface ReviewCenterFilterFormProps {
  value: ReviewCenterFilters
  namespaces: ReviewFilterOption[]
  namespaceLoading?: boolean
  onChange: (filters: ReviewCenterFilters) => void
  onApply: () => void
  onReset: () => void
}

export const REVIEW_FILTER_TRIGGER_CLASS_NAME = 'h-11 w-full rounded-lg bg-background px-3 text-sm md:h-8 md:w-44'
export const REVIEW_FILTER_CONTENT_CLASS_NAME = 'w-[var(--radix-select-trigger-width)] min-w-0 max-w-[var(--radix-select-trigger-width)] [&_[role=option]]:min-h-11 [&_[role=option]]:rounded-lg [&_[role=option]]:py-1.5 md:[&_[role=option]]:min-h-8 md:[&_[role=option]]:py-1 [&_[data-radix-select-viewport]]:h-auto [&_[data-radix-select-viewport]]:min-w-0 [&_[data-radix-select-viewport]]:p-1.5'

export function ReviewCenterFilterForm({ value, namespaces, namespaceLoading, onChange, onApply, onReset }: ReviewCenterFilterFormProps) {
  const { t } = useTranslation()
  const id = useId()
  const setValue = <K extends keyof ReviewCenterFilters>(key: K, next: ReviewCenterFilters[K]) => onChange({ ...value, [key]: next })
  const submit = (event: FormEvent) => { event.preventDefault(); onApply() }
  const field = (key: 'status' | 'namespace' | 'reviewKind' | 'originType', options: ReviewFilterOption[]) => (
    <div className="min-w-0">
      <label htmlFor={`${id}-${key}`} className="mb-2 block text-xs font-medium text-muted-foreground">{t(`reviewCenter.filters.${key}`)}</label>
      <Select value={value[key]} onValueChange={(next) => setValue(key, next as ReviewCenterFilters[typeof key])} disabled={key === 'namespace' && namespaceLoading}>
        <SelectTrigger id={`${id}-${key}`} data-analytics-id={key === 'status' ? 'review.filter.status.interact' : key === 'namespace' ? 'review.filter.namespace.interact' : key === 'reviewKind' ? 'review.filter.kind.interact' : 'review.filter.origin.interact'} className={REVIEW_FILTER_TRIGGER_CLASS_NAME}><SelectValue /></SelectTrigger>
        <SelectContent sideOffset={8} className={REVIEW_FILTER_CONTENT_CLASS_NAME}>
          {options.map((option) => <SelectItem key={option.value} value={option.value} title={option.label} data-analytics-id={key === 'status' ? 'review.filter.status.select' : key === 'namespace' ? 'review.filter.namespace.select' : key === 'reviewKind' ? 'review.filter.kind.select' : 'review.filter.origin.select'}><span className="block truncate">{option.label}</span></SelectItem>)}
        </SelectContent>
      </Select>
    </div>
  )
  return (
    <form onSubmit={submit} className="rounded-[10px] bg-secondary p-5" aria-label={t('reviewCenter.filters.title')}>
      <h2 className="mb-5 text-[15px] font-semibold">{t('reviewCenter.filters.title')}</h2>
      <div className="space-y-5">
      <div className="grid grid-cols-2 gap-4 md:flex md:flex-wrap md:items-end">
        <div className="col-span-2 min-w-0 md:w-[408px] md:max-w-full">
          <label htmlFor={`${id}-q`} className="mb-2 block text-xs font-medium text-muted-foreground">{t('reviewCenter.filters.q')}</label>
          <div className="relative"><Search aria-hidden="true" className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" /><Input id={`${id}-q`} value={value.q} onChange={(event) => setValue('q', event.target.value)} placeholder={t('reviewCenter.filters.searchPlaceholder')} className="h-11 bg-background pl-9 md:h-8" /></div>
        </div>
        {field('status', REVIEW_STATUSES.map((status) => ({ value: status, label: t(`reviewCenter.status.${status}`) })))}
        {field('namespace', [{ value: 'ALL', label: t('reviewCenter.filters.allScopes') }, ...namespaces])}
      </div>
      <div className="grid grid-cols-2 gap-4 md:flex md:flex-wrap md:items-end">
        {field('reviewKind', REVIEW_KINDS.map((kind) => ({ value: kind, label: t(`reviewCenter.kind.${kind}`) })))}
        {field('originType', REVIEW_ORIGINS.map((origin) => ({ value: origin, label: t(`reviewCenter.origin.${origin}`) })))}
        <div className="col-span-2 mt-2 flex w-full gap-3 md:ml-auto md:mt-0 md:w-auto">
          <Button data-analytics-id="review.filter.reset" type="button" variant="outline" onClick={onReset} className="h-11 flex-1 bg-background md:h-8 md:w-20 md:flex-none">{t('reviewCenter.filters.reset')}</Button>
          <Button data-analytics-id="review.filter.apply" type="submit" className="h-11 flex-1 md:h-8 md:w-[100px] md:flex-none">{t('reviewCenter.filters.apply')}</Button>
        </div>
      </div>
      </div>
    </form>
  )
}
