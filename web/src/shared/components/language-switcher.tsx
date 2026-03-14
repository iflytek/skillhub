import { useTranslation } from 'react-i18next'
import { Button } from '@/shared/ui/button'
import { cn } from '@/shared/lib/utils'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/shared/ui/dropdown-menu'
import { Globe } from 'lucide-react'

interface LanguageSwitcherProps {
  className?: string
}

export function LanguageSwitcher({ className }: LanguageSwitcherProps) {
  const { i18n } = useTranslation()

  const languages = [
    { code: 'zh', name: '中文' },
    { code: 'en', name: 'English' },
  ]

  // 获取当前语言的主要代码（去掉地区代码）
  const currentLangCode = i18n.language?.split('-')[0] || 'zh'
  const currentLanguage = languages.find((lang) => lang.code === currentLangCode) || languages[0]

  const changeLanguage = (langCode: string) => {
    i18n.changeLanguage(langCode)
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          variant="ghost"
          size="sm"
          className={cn('gap-2 text-muted-foreground hover:text-foreground', className)}
        >
          <Globe className="h-4 w-4" />
          <span className="text-sm text-inherit">{currentLanguage.name}</span>
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        {languages.map((lang) => (
          <DropdownMenuItem
            key={lang.code}
            onClick={() => changeLanguage(lang.code)}
            className={currentLangCode === lang.code ? 'bg-accent' : ''}
          >
            {lang.name}
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
