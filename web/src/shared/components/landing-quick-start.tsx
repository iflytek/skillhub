import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Bot, Check, Copy, UserRound } from 'lucide-react'
import { useCopyToClipboard } from '@/shared/lib/clipboard'

type LandingQuickStartTabId = 'agent' | 'human'

interface LandingQuickStartTab {
  id: LandingQuickStartTabId
  label: string
  description: string
  command: string
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
      className="absolute right-2.5 top-1/2 flex h-9 w-9 -translate-y-1/2 items-center justify-center rounded-xl border bg-white transition-colors hover:bg-slate-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 cursor-pointer"
      style={{ borderColor: 'hsl(var(--border))', color: 'hsl(var(--foreground))' }}
    >
      {copied ? <Check className="h-4 w-4" /> : <Copy className="h-4 w-4" />}
    </button>
  )
}

export function LandingQuickStartSection() {
  const { t } = useTranslation()
  const [activeTab, setActiveTab] = useState<LandingQuickStartTabId>('agent')

  const tabs: LandingQuickStartTab[] = [
    {
      id: 'agent',
      label: t('landing.quickStart.tabs.agent'),
      description: t('landing.quickStart.agent.description'),
      command: t('landing.quickStart.agent.command'),
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
    <section className="relative z-10 w-full px-6 py-14 md:py-16" style={{ background: 'var(--bg-page, hsl(var(--background)))' }}>
      <div className="max-w-4xl mx-auto">
        <div className="text-center mb-7 md:mb-8">
          <h2 className="text-3xl md:text-4xl font-bold tracking-tight mb-3" style={{ color: 'hsl(var(--foreground))' }}>
            {t('landing.quickStart.title')}
          </h2>
          <p className="text-base md:text-lg max-w-2xl mx-auto leading-relaxed" style={{ color: 'hsl(var(--text-secondary))' }}>
            {t('landing.quickStart.description', { defaultValue: t('landing.quickStart.subtitle') })}
          </p>
        </div>

        <div
          className="mx-auto max-w-2xl rounded-[28px] border bg-white p-3 shadow-[0_24px_60px_-28px_rgba(15,23,42,0.25)]"
          style={{ borderColor: 'hsl(var(--border-card))' }}
        >
          <div
            className="grid grid-cols-2 gap-2 rounded-2xl p-1.5"
            style={{ background: 'linear-gradient(180deg, rgba(248,250,252,0.98) 0%, rgba(241,245,249,0.92) 100%)' }}
          >
            {tabs.map((tab) => {
              const isActive = tab.id === currentTab.id
              const Icon = tab.id === 'agent' ? Bot : UserRound

              return (
                <button
                  key={tab.id}
                  type="button"
                  onClick={() => setActiveTab(tab.id)}
                  aria-pressed={isActive}
                  className="flex min-h-11 items-center justify-center gap-2 rounded-[14px] px-4 py-3 text-base font-medium transition-all duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 cursor-pointer"
                  style={{
                    background: isActive ? 'rgba(255,255,255,0.96)' : 'transparent',
                    color: isActive ? 'hsl(var(--foreground))' : 'hsl(var(--muted-foreground))',
                    boxShadow: isActive ? '0 6px 18px rgba(15, 23, 42, 0.08)' : 'none',
                  }}
                >
                  <Icon className="h-4 w-4" strokeWidth={1.75} />
                  <span>{tab.label}</span>
                </button>
              )
            })}
          </div>

          <div className="px-4 pb-4 pt-8 md:px-8 md:pb-6 md:pt-9">
            <p
              className="mx-auto mb-6 max-w-xl text-center text-base font-medium leading-relaxed md:text-lg"
              style={{ color: 'hsl(var(--foreground))' }}
            >
              {currentTab.description}
            </p>

            <div
              className="relative rounded-2xl border bg-slate-50/90 px-4 py-3 pr-14 shadow-[inset_0_1px_0_rgba(255,255,255,0.7)]"
              style={{ borderColor: 'hsl(var(--border))' }}
            >
              <div className="overflow-x-auto whitespace-nowrap">
                <code
                  className="font-mono text-sm md:text-base"
                  style={{ color: currentTab.id === 'agent' ? '#16A34A' : '#0F172A' }}
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
