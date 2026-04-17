import { useState, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { Bot, Check, Copy, UserRound } from 'lucide-react'
import { useCopyToClipboard } from '@/shared/lib/clipboard'
import { cn } from '@/shared/lib/utils'

type LandingQuickStartTabId = 'agent' | 'human'

interface LandingQuickStartTab {
  id: LandingQuickStartTabId
  label: string
  description: string
  command: string
}

/**
 * Get the base URL for the application.
 * Prefers the runtime config if set and not localhost.
 * Falls back to the current page origin.
 */
function getAppBaseUrl(): string {
  if (typeof window === 'undefined') {
    return ''
  }
  const runtimeConfig = window.__SKILLHUB_RUNTIME_CONFIG__
  const configuredUrl = runtimeConfig?.appBaseUrl
  // Use configured URL only if it's set and not localhost
  if (configuredUrl && !configuredUrl.includes('localhost')) {
    return configuredUrl
  }
  // Fallback to current page origin
  return `${window.location.protocol}//${window.location.host}`
}

function CompactCopyButton({ text }: { text: string }) {
  const { t } = useTranslation()
  const [copied, copy] = useCopyToClipboard()

  const handleCopy = async () => {
    try {
      await copy(text)
    } catch (err) {
      console.error('Failed to copy:', err)
    }
  }

  const label = copied ? (t('copyButton.copied') || 'Copied') : (t('copyButton.copy') || 'Copy')

  return (
    <button
      type="button"
      onClick={handleCopy}
      aria-label={label}
      title={label}
      className="absolute right-2.5 top-1/2 flex h-9 w-9 -translate-y-1/2 items-center justify-center rounded-xl border border-border bg-card text-foreground transition-colors hover:bg-secondary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 cursor-pointer"
    >
      {copied ? <Check className="h-4 w-4" /> : <Copy className="h-4 w-4" />}
    </button>
  )
}

export function LandingQuickStartSection() {
  const { t } = useTranslation()
  const [activeTab, setActiveTab] = useState<LandingQuickStartTabId>('agent')
  const baseUrl = useMemo(() => getAppBaseUrl(), [])

  // Build dynamic agent command with actual registry URL
  const agentCommand = t('landing.quickStart.agent.commandTemplate', {
    defaultValue: t('landing.quickStart.agent.command'),
    url: `${baseUrl}/registry/skill.md`,
  })

  const tabs: LandingQuickStartTab[] = [
    {
      id: 'agent',
      label: t('landing.quickStart.tabs.agent'),
      description: t('landing.quickStart.agent.description'),
      command: agentCommand,
    },
    {
      id: 'human',
      label: t('landing.quickStart.tabs.human'),
      description: t('landing.quickStart.human.description'),
      command: t('landing.quickStart.human.command'),
    },
  ]

  const currentTab = tabs.find((tab) => tab.id === activeTab) ?? tabs[0]

  return (
    <section className="relative z-10 w-full bg-background px-6 py-8 md:py-10">
      <div className="max-w-4xl mx-auto">
        <div className="text-center mb-5 md:mb-6">
          <h2 className="text-3xl md:text-4xl font-bold tracking-tight text-foreground mb-3">
            {t('landing.quickStart.title')}
          </h2>
          <p className="text-base md:text-lg max-w-2xl mx-auto text-content-secondary leading-relaxed">
            {t('landing.quickStart.description', { defaultValue: t('landing.quickStart.subtitle') })}
          </p>
        </div>

        <div className="mx-auto max-w-2xl rounded-[28px] border border-stroke-subtle bg-card p-3 shadow-card">
          <div className="grid grid-cols-2 gap-2 rounded-2xl bg-secondary/70 p-1.5">
            {tabs.map((tab) => {
              const isActive = tab.id === currentTab.id
              const Icon = tab.id === 'agent' ? Bot : UserRound

              return (
                <button
                  key={tab.id}
                  type="button"
                  onClick={() => setActiveTab(tab.id)}
                  aria-pressed={isActive}
                  className={cn(
                    'flex min-h-11 items-center justify-center gap-2 rounded-[14px] px-4 py-3 text-base font-medium transition-all duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 cursor-pointer',
                    isActive
                      ? 'bg-background text-foreground shadow-sm'
                      : 'text-muted-foreground hover:text-foreground',
                  )}
                >
                  <Icon className="h-4 w-4" strokeWidth={1.75} />
                  <span>{tab.label}</span>
                </button>
              )
            })}
          </div>

          <div className="px-4 pb-4 pt-8 md:px-8 md:pb-6 md:pt-9">
            <p className="mx-auto mb-6 max-w-xl text-center text-base font-medium text-foreground leading-relaxed md:text-lg">
              {currentTab.description}
            </p>

            <div className="relative rounded-2xl border border-border bg-secondary/60 px-4 py-3 pr-14 shadow-[inset_0_1px_0_hsl(var(--background)/0.8)]">
              <div className="overflow-x-auto whitespace-nowrap">
                <code
                  className={cn(
                    'font-mono text-sm md:text-base',
                    currentTab.id === 'agent' ? 'text-state-success' : 'text-foreground',
                  )}
                >
                  {currentTab.command}
                </code>
              </div>
              <CompactCopyButton text={currentTab.command} />
            </div>
          </div>
        </div>
      </div>
    </section>
  )
}
