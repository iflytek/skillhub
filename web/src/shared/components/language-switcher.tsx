import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '@/shared/ui/button'
import { cn } from '@/shared/lib/utils'
import { ChevronDown, Globe } from 'lucide-react'

interface LanguageSwitcherProps {
  className?: string
}

/**
 * Locale picker for the app shell.
 *
 * Intentionally avoids Radix DropdownMenu portals: the switcher lives in the
 * global header and re-renders with every search/navigation update. Body portals
 * from that path race React 19 reconciliation (removeChild / insertBefore).
 * Pattern matches UserMenu (absolute in-tree menu).
 */
export function LanguageSwitcher({ className }: LanguageSwitcherProps) {
  const { i18n } = useTranslation()
  const rootRef = useRef<HTMLDivElement | null>(null)
  const [open, setOpen] = useState(false)

  const languages = [
    { code: 'zh', name: '中文' },
    { code: 'en', name: 'English' },
  ]

  // 获取当前语言的主要代码（去掉地区代码）
  const currentLangCode = i18n.language?.split('-')[0] || 'zh'
  const currentLanguage = languages.find((lang) => lang.code === currentLangCode) || languages[0]

  useEffect(() => {
    if (!open) {
      return
    }

    const handlePointerDown = (event: MouseEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) {
        setOpen(false)
      }
    }

    document.addEventListener('mousedown', handlePointerDown)
    return () => {
      document.removeEventListener('mousedown', handlePointerDown)
    }
  }, [open])

  const changeLanguage = (langCode: string) => {
    void i18n.changeLanguage(langCode)
    setOpen(false)
  }

  return (
    <div ref={rootRef} className="relative">
      <Button
        type="button"
        variant="ghost"
        size="sm"
        aria-expanded={open}
        aria-haspopup="menu"
        className={cn('cursor-pointer gap-2 text-muted-foreground hover:text-foreground', className)}
        onClick={() => setOpen((current) => !current)}
      >
        <Globe className="h-4 w-4" />
        <span className="text-sm text-inherit">{currentLanguage.name}</span>
        <ChevronDown className="h-3.5 w-3.5 opacity-70" />
      </Button>
      {open ? (
        <div className="absolute right-0 top-full z-50 pt-2">
          <div
            role="menu"
            className="flex min-w-[9rem] flex-col gap-1.5 rounded-md border bg-popover p-2 text-popover-foreground shadow-md"
          >
            {languages.map((lang) => (
              <button
                key={lang.code}
                type="button"
                role="menuitem"
                onClick={() => changeLanguage(lang.code)}
                className={cn(
                  'cursor-pointer rounded-md px-3 py-2 text-left text-sm outline-none transition-colors hover:bg-accent hover:text-accent-foreground',
                  currentLangCode === lang.code ? 'bg-accent' : ''
                )}
              >
                {lang.name}
              </button>
            ))}
          </div>
        </div>
      ) : null}
    </div>
  )
}
