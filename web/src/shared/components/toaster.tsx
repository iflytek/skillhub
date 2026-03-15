import { Toaster as Sonner } from 'sonner'
import { CENTER_TOASTER_ID } from '@/shared/lib/toast'

export function Toaster() {
  return (
    <>
      <Sonner
        position="top-right"
        toastOptions={{
          classNames: {
            toast: 'glass-strong border border-border/40',
            title: 'text-foreground font-semibold',
            description: 'text-muted-foreground',
            actionButton: 'bg-primary text-primary-foreground',
            cancelButton: 'bg-muted text-muted-foreground',
            error: 'border-destructive/40',
            success: 'border-emerald-500/40',
            warning: 'border-amber-500/40',
            info: 'border-blue-500/40',
          },
        }}
      />
      <Sonner
        id={CENTER_TOASTER_ID}
        position="top-center"
        className="!left-1/2 !right-auto !top-4 !-translate-x-1/2"
        offset={16}
        mobileOffset={16}
        toastOptions={{
          classNames: {
            toast: 'glass-strong mx-auto w-fit max-w-[min(100vw-2rem,32rem)] border border-border/40',
            title: 'text-foreground font-semibold text-center',
            description: 'text-muted-foreground text-center',
            actionButton: 'bg-primary text-primary-foreground',
            cancelButton: 'bg-muted text-muted-foreground',
            error: 'border-destructive/40',
            success: 'border-emerald-500/40',
            warning: 'border-amber-500/40',
            info: 'border-blue-500/40',
          },
        }}
      />
    </>
  )
}
