import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Input } from '@/shared/ui/input'
import { Button } from '@/shared/ui/button'

interface SearchBarProps {
  defaultValue?: string
  value?: string
  placeholder?: string
  onChange?: (query: string) => void
  onSearch?: (query: string) => void
}

export function SearchBar({ defaultValue = '', value, placeholder, onChange, onSearch }: SearchBarProps) {
  const { t } = useTranslation()
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

  return (
    <form onSubmit={handleSubmit} className="flex gap-3 glass-strong p-2 rounded-xl">
      <div className="relative flex-1">
        <svg
          className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-muted-foreground pointer-events-none"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
          />
        </svg>
        <Input
          type="text"
          value={currentQuery}
          onChange={(e) => handleChange(e.target.value)}
          placeholder={placeholder || t('searchBar.placeholder')}
          className="pl-10 border-0 bg-transparent focus-visible:ring-0 focus-visible:ring-offset-0 h-12"
        />
      </div>
      <Button type="submit" size="lg" className="px-8">
        {t('searchBar.button')}
      </Button>
    </form>
  )
}
