import {
  useId,
  useLayoutEffect,
  useRef,
  useState,
  type CSSProperties,
} from 'react'
import * as DialogPrimitive from '@radix-ui/react-dialog'
import { Check, ChevronDown, ChevronRight, ShieldCheck, X } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Button } from '@/shared/ui/button'
import { getPortalContainer } from '@/shared/lib/portal-container'
import { cn } from '@/shared/lib/utils'
import { TOKEN_CAPABILITIES } from './token-capabilities'
import { getPermissionPanelPosition } from './token-permission-position'

interface TokenPermissionDetailsProps {
  scopes: readonly string[]
  name?: string
  legacyCompat?: boolean
  showAll?: boolean
  disabled?: boolean
}

/** Anchored, focus-managed details on desktop; a bounded bottom sheet on small screens. */
export function TokenPermissionDetails({
  scopes,
  name,
  legacyCompat,
  showAll = false,
  disabled,
}: TokenPermissionDetailsProps) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const [showIdentifiers, setShowIdentifiers] = useState(false)
  const [position, setPosition] = useState({ left: 16, top: 16, width: 320, height: 380, mobile: false })
  const anchor = useRef<HTMLButtonElement>(null)
  const content = useRef<HTMLDivElement>(null)
  const identifiersId = useId()
  const known = TOKEN_CAPABILITIES.filter(
    ({ scope }) => showAll || scopes.includes(scope),
  )
  const extra = scopes.filter(
    (scope) => !TOKEN_CAPABILITIES.some((item) => item.scope === scope),
  )

  useLayoutEffect(() => {
    if (!open) return
    const update = () => {
      if (!anchor.current) return
      const viewport = window.visualViewport
      const next = getPermissionPanelPosition(
        anchor.current.getBoundingClientRect(),
        {
          left: viewport?.offsetLeft ?? 0,
          top: viewport?.offsetTop ?? 0,
          width: viewport?.width ?? window.innerWidth,
          height: viewport?.height ?? window.innerHeight,
        },
        content.current?.getBoundingClientRect().height || undefined,
      )
      setPosition((previous) =>
        Object.keys(next).every((key) => previous[key as keyof typeof next] === next[key as keyof typeof next])
          ? previous : next,
      )
    }
    update()
    const observer =
      typeof ResizeObserver === 'undefined'
        ? undefined
        : new ResizeObserver(update)
    if (content.current) observer?.observe(content.current)
    if (anchor.current) observer?.observe(anchor.current)
    window.visualViewport?.addEventListener('resize', update)
    window.visualViewport?.addEventListener('scroll', update)
    window.addEventListener('resize', update)
    window.addEventListener('scroll', update, true)
    return () => {
      observer?.disconnect()
      window.visualViewport?.removeEventListener('resize', update)
      window.visualViewport?.removeEventListener('scroll', update)
      window.removeEventListener('resize', update)
      window.removeEventListener('scroll', update, true)
    }
  }, [open])

  return (
    <DialogPrimitive.Root
      open={open}
      onOpenChange={(value) => {
        setOpen(value)
        if (!value) setShowIdentifiers(false)
      }}
    >
      <DialogPrimitive.Trigger asChild>
        <Button
          ref={anchor}
          type="button"
          variant="ghost"
          size="sm"
          disabled={disabled}
          className={cn(
            'h-7 text-xs',
            showAll
              ? 'px-1 text-info hover:text-info'
              : '-ml-2 gap-1 text-muted-foreground',
          )}
        >
          {t(
            showAll ? 'token.permissionsHelp' : 'token.viewCapabilitiesCount',
            { count: scopes.length },
          )}
          {!showAll && (
            <ChevronRight aria-hidden="true" className="h-3.5 w-3.5" />
          )}
        </Button>
      </DialogPrimitive.Trigger>
      <DialogPrimitive.Portal container={getPortalContainer()}>
        <DialogPrimitive.Overlay className="fixed inset-0 z-[60] bg-black/25 sm:bg-transparent" />
        <DialogPrimitive.Content
          ref={content}
          data-mobile={position.mobile}
          onEscapeKeyDown={(event) => event.stopPropagation()}
          style={
            {
              '--permission-left': `${position.left}px`,
              '--permission-top': `${position.top}px`,
              '--permission-height': `${position.height}px`,
              '--permission-width': `${position.width}px`,
            } as CSSProperties
          }
          className="fixed left-[var(--permission-left)] top-[var(--permission-top)] z-[70] flex max-h-[var(--permission-height)] w-[var(--permission-width)] flex-col overflow-hidden rounded-xl border bg-card text-card-foreground shadow-xl outline-none data-[mobile=true]:rounded-b-none data-[mobile=true]:rounded-t-2xl"
        >
          <div
            className={cn("mx-auto mt-2 h-1 w-9 shrink-0 rounded-full bg-muted-foreground/25", !position.mobile && "hidden")}
            aria-hidden="true"
          />
          <div className="shrink-0 border-b px-3 py-3 pr-11">
            <DialogPrimitive.Title className="flex items-center gap-2 text-sm leading-5 font-semibold">
              <ShieldCheck aria-hidden="true" className="h-3.5 w-3.5 text-info" />
              {t(
                showAll
                  ? 'token.permissionsHelp'
                  : 'token.effectiveCapabilities',
              )}
            </DialogPrimitive.Title>
            <DialogPrimitive.Description className={cn(
              "mt-1 line-clamp-2 break-words text-xs leading-4 text-muted-foreground",
              position.height < 260 && "sr-only",
            )}>
              {showAll ? t('token.permissionsHelpDescription') : name}
            </DialogPrimitive.Description>
            <DialogPrimitive.Close asChild>
              <Button
                type="button"
                size="icon"
                variant="ghost"
                className="absolute right-1.5 top-1.5 h-9 w-9 sm:h-7 sm:w-7"
                aria-label={t('dialog.close')}
              >
                <X aria-hidden="true" className="h-3.5 w-3.5" />
              </Button>
            </DialogPrimitive.Close>
          </div>
          <div
            id={identifiersId}
            className="min-h-0 flex-1 overflow-y-auto overscroll-contain px-3 py-1"
          >
            <ul className="divide-y">
              {known.map(({ scope, label }) => (
                <li key={scope} className="py-0.5">
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-xs font-medium leading-4">
                      {t(`token.capabilityLabels.${label}`)}
                    </span>
                    {scopes.includes(scope) ? (
                      <span className="flex shrink-0 items-center gap-1 text-xs leading-4 text-success">
                        <Check aria-hidden="true" className="h-3 w-3" />
                        {t('token.permissionSelected')}
                      </span>
                    ) : (
                      <span className="shrink-0 text-xs leading-4 text-muted-foreground">
                        {t('token.permissionNotSelected')}
                      </span>
                    )}
                  </div>
                  <p className="mt-0.5 text-xs leading-4 text-muted-foreground">
                    {t(`createToken.capabilityDescriptions.${label}`)}
                  </p>
                  {showIdentifiers && (
                    <code className="mt-0.5 block break-all text-xs leading-4 text-muted-foreground">
                      {scope}
                    </code>
                  )}
                </li>
              ))}
            </ul>
            {extra.length > 0 && (
              <ul className="border-t">
                {extra.map((scope) => (
                  <li key={scope} className="py-0.5">
                    <p className="break-all text-xs font-medium leading-4">
                      {scope === 'suite:legacy-write'
                        ? t('token.legacySuiteCapability')
                        : scope}
                    </p>
                    <p className="mt-0.5 text-xs leading-4 text-muted-foreground">
                      {t('token.additionalCapabilityDescription')}
                    </p>
                    {showIdentifiers && scope === 'suite:legacy-write' && (
                      <code className="mt-0.5 block text-xs leading-4 text-muted-foreground">
                        {scope}
                      </code>
                    )}
                  </li>
                ))}
              </ul>
            )}
            {legacyCompat && (
              <p className="my-2 rounded-md bg-warning/10 p-2 text-xs leading-4 text-warning-foreground dark:text-warning">
                {t('token.legacyCapabilityExplanation')}
              </p>
            )}
            <p className="my-1 rounded-md bg-muted/50 px-2 py-1.5 text-xs leading-4 text-muted-foreground">
              {t('createToken.boundaryHint')}
            </p>
          </div>
          <div className="shrink-0 border-t px-3 pb-[max(0.375rem,env(safe-area-inset-bottom))] pt-1.5">
            <Button
              type="button"
              variant="ghost"
              size="sm"
              className="-ml-1 h-9 text-xs sm:h-7"
              aria-expanded={showIdentifiers}
              aria-controls={identifiersId}
              onClick={() => setShowIdentifiers((value) => !value)}
            >
              <ChevronDown
                aria-hidden="true"
                className={cn('h-3.5 w-3.5', !showIdentifiers && '-rotate-90')}
              />
              {t('token.showCapabilityIdentifiers')}
            </Button>
          </div>
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  )
}
