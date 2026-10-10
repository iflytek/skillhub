import { Moon, Sun } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useTheme } from '@/shared/hooks/use-theme'
import { cn } from '@/shared/lib/utils'

interface ThemeToggleProps {
  className?: string
}

export function ThemeToggle({ className }: ThemeToggleProps) {
  const { t } = useTranslation()
  const { theme, toggleTheme } = useTheme()
  const isDark = theme === 'dark'
  const label = isDark ? t('theme.switchToLight') : t('theme.switchToDark')
  const accessibleName = t('theme.darkMode')

  return (
    <button
      type="button"
      role="switch"
      aria-label={accessibleName}
      aria-checked={isDark}
      title={label}
      onClick={toggleTheme}
      className={cn(
        'group relative inline-flex h-6 w-11 shrink-0 items-center rounded-full border border-border/50 bg-muted/60 px-0.5 text-muted-foreground transition-[background-color,border-color] duration-150 hover:border-border focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-ring/15',
        className,
      )}
    >
      <span
        aria-hidden="true"
        className={cn(
          'absolute left-0.5 top-0.5 h-5 w-5 rounded-full border border-border/50 bg-card shadow-[0_1px_2px_0_rgb(0_0_0/0.12)] transition-[transform,box-shadow] duration-150 ease-out motion-reduce:transition-none',
          isDark && 'translate-x-[20px]',
        )}
      />
      <span className="relative z-10 inline-flex h-5 w-5 items-center justify-center">
        <Sun
          aria-hidden="true"
          className={cn('h-3 w-3 transition-colors duration-150', !isDark && 'text-foreground')}
        />
      </span>
      <span className="relative z-10 inline-flex h-5 w-5 items-center justify-center">
        <Moon
          aria-hidden="true"
          className={cn('h-3 w-3 transition-colors duration-150', isDark && 'text-foreground')}
        />
      </span>
    </button>
  )
}