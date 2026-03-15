import { useNavigate } from '@tanstack/react-router'
import { ArrowLeft } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Button } from '@/shared/ui/button'

interface DashboardPageHeaderProps {
  title: string
  subtitle?: string
  actions?: React.ReactNode
}

export function DashboardPageHeader({ title, subtitle, actions }: DashboardPageHeaderProps) {
  const { t } = useTranslation()
  const navigate = useNavigate()

  return (
    <div className="space-y-4">
      <Button variant="ghost" className="px-0 text-muted-foreground hover:text-foreground" onClick={() => navigate({ to: '/dashboard' })}>
        <ArrowLeft className="mr-2 h-4 w-4" />
        {t('dashboard.backToDashboard')}
      </Button>
      <div className="flex items-center justify-between gap-4">
        <div>
          <h1 className="text-4xl font-bold font-heading mb-2">{title}</h1>
          {subtitle ? <p className="text-muted-foreground text-lg">{subtitle}</p> : null}
        </div>
        {actions}
      </div>
    </div>
  )
}
