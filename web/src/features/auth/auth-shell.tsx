import type React from 'react'
import { ArrowLeft } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { BrandMark } from '@/shared/components/brand-mark'
import { withBasePath } from '@/shared/lib/base-path'
import { cn } from '@/shared/lib/utils'

interface AuthShellProps {
  children: React.ReactNode
  className?: string
}

/** Shared theme-aware product introduction and form layout for login and registration. */
export function AuthShell({ children, className }: AuthShellProps) {
  const { t } = useTranslation()

  return (
    <section className={cn('relative w-full', className)}>
      <a
        href={withBasePath('/')}
        className="absolute left-5 top-5 z-20 inline-flex items-center gap-2 text-sm font-medium text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-sky-500 xl:hidden"
      >
        <ArrowLeft aria-hidden="true" className="h-4 w-4" />
        {t('authShell.backHome')}
      </a>
      <div className="grid min-h-svh w-full bg-background xl:grid-cols-[53fr_47fr]">
        <aside className="sticky top-0 hidden h-svh overflow-hidden border-r border-slate-200 bg-slate-50/60 text-zinc-950 dark:border-slate-800 dark:bg-slate-950 dark:text-zinc-50 xl:block">
          <a
            href={withBasePath('/')}
            className="absolute left-12 top-10 z-20 inline-flex items-center gap-2 text-sm font-medium text-zinc-700 transition-colors hover:text-zinc-950 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-current dark:text-zinc-300 dark:hover:text-white 2xl:left-16"
          >
            <ArrowLeft aria-hidden="true" className="h-4 w-4" />
            {t('authShell.backHome')}
          </a>

          <div className="relative z-10 px-12 pt-28 2xl:px-16">
            <div className="flex items-center gap-3">
              <BrandMark className="h-8 w-8" alt="" />
              <span className="text-lg font-semibold tracking-tight">SkillHub</span>
            </div>
            <h2 className="mt-7 text-[clamp(4.5rem,6.5vw,6rem)] font-black leading-none tracking-[-0.04em]">SkillHub</h2>
            <p className="mt-5 max-w-[420px] whitespace-pre-line text-2xl font-semibold leading-snug tracking-tight">{t('authShell.heroTitle')}</p>
            <p className="mt-4 max-w-[350px] whitespace-pre-line text-sm leading-6 text-zinc-600 dark:text-zinc-400">{t('authShell.heroSubtitle')}</p>
          </div>

          <div className="pointer-events-none absolute bottom-[3%] -right-[3%] h-[60%] w-[78%]" aria-hidden="true">
            <img src={withBasePath('/login-skill-art-light.png')} alt="" width="1484" height="1060" className="h-full w-full object-contain dark:hidden" />
            <img src={withBasePath('/login-skill-art-dark.png')} alt="" width="1484" height="1060" className="hidden h-full w-full object-contain dark:block" />
          </div>

          <p className="absolute bottom-9 left-12 z-10 whitespace-pre-line text-[10px] font-medium uppercase leading-4 tracking-[0.28em] text-slate-500 dark:text-slate-400 2xl:left-16">{t('authShell.shortTagline')}</p>
        </aside>

        <div className="flex min-h-svh items-start justify-center px-5 pb-8 pt-20 sm:px-10 xl:px-12 xl:py-24">
          <div className="w-full max-w-[500px]">
            {children}
          </div>
        </div>
      </div>
    </section>
  )
}
