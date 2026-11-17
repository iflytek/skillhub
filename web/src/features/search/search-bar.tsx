import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Loader2, Search, X } from 'lucide-react'
import { MAX_SEARCH_INPUT_LENGTH } from '@/shared/lib/search-query'
import { Input } from '@/shared/ui/input'
import { Button } from '@/shared/ui/button'
import { cn } from '@/shared/lib/utils'

interface SearchBarProps {
  variant?: 'default' | 'market'
  analyticsId?: string
  defaultValue?: string
  value?: string
  placeholder?: string
  isSearching?: boolean
  onChange?: (query: string) => void
  onSearch?: (query: string) => void
}

/**
 * Shared search bar used by search-driven pages and landing surfaces.
 *
 * It supports both controlled and uncontrolled usage so page-level containers can decide whether
 * query text should be driven from URL state or local form state.
 */
export function SearchBar({ defaultValue = '', value, placeholder, isSearching = false, onChange, onSearch, analyticsId, variant = 'default' }: SearchBarProps) {
  const { t } = useTranslation()
  const isMarket = variant === 'market'
  const [query, setQuery] = useState(defaultValue)
  const isControlled = value !== undefined
  const currentQuery = isControlled ? value : query

  useEffect(() => {
    if (!isControlled) {
      setQuery(defaultValue)
    }
  }, [defaultValue, isControlled])

  const handleChange = (nextQuery: string) => {
    if (!isControlled) {
      setQuery(nextQuery)
    }
    onChange?.(nextQuery)
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (onSearch) {
      onSearch(currentQuery)
    }
  }

  const handleClear = () => {
    handleChange('')
  }

  return (
    <form data-analytics-id={onSearch ? analyticsId : undefined} onSubmit={handleSubmit} className={cn('flex items-center gap-2 rounded-lg border border-border/60 bg-card', isMarket ? 'h-14 px-3 py-2 focus-within:border-ring sm:h-16 sm:px-4 sm:py-3' : 'p-1.5 shadow-[var(--shadow-card)]')}>
      <div className="relative min-w-0 flex-1">
        <Search aria-hidden="true" className={cn('absolute top-1/2 -translate-y-1/2 text-muted-foreground pointer-events-none', isMarket ? 'left-1 h-4 w-4 sm:h-5 sm:w-5' : 'left-3 h-5 w-5')} />
        <Input
          type="text"
          value={currentQuery}
          onChange={(e) => handleChange(e.target.value)}
          maxLength={MAX_SEARCH_INPUT_LENGTH}
          placeholder={placeholder || t('searchBar.placeholder')}
          aria-label={placeholder || t('searchBar.placeholder')}
          className={cn('pr-10 border-0 bg-transparent focus-visible:ring-0 focus-visible:ring-offset-0', isMarket ? 'h-9 pl-8 text-sm sm:pl-10 sm:text-base' : 'h-10 pl-10')}
        />
        {currentQuery ? (
          <button data-analytics-id={analyticsId ? 'search.query.clear' : undefined}
            type="button"
            onClick={handleClear}
            className="absolute right-3 top-1/2 inline-flex h-7 w-7 -translate-y-1/2 items-center justify-center rounded-full text-muted-foreground transition-colors hover:bg-secondary/70 hover:text-foreground"
            aria-label={t('searchBar.clear')}
            title={t('searchBar.clear')}
          >
            <X className="h-4 w-4" />
          </button>
        ) : null}
      </div>
      <Button type="submit" size={isMarket ? 'lg' : 'default'} className={cn('rounded-md', isMarket ? 'h-8 min-w-[4.5rem] px-3 sm:h-9 sm:min-w-24' : 'px-5 min-w-24')} disabled={isSearching}>
        {isSearching && <Loader2 aria-hidden="true" className="h-4 w-4 animate-spin" />}
        {t('searchBar.button')}
      </Button>
    </form>
  )
}
