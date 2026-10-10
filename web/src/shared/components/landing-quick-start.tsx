import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Check, ChevronRight, Copy, Download, FileText, Search } from 'lucide-react'
import { buildApiUrl, WEB_API_PREFIX } from '@/api/client'
import { copyToClipboard, useCopyToClipboard } from '@/shared/lib/clipboard'
import { resolvePublicRegistryUrl } from '@/shared/lib/registry-url'
import { toast } from '@/shared/lib/toast'

type AccessMode = 'agent' | 'cli' | 'web'
type AgentView = 'registry' | 'discovery'

interface AccessModeOption {
  id: AccessMode
  number: string
  titleKey: string
  descriptionKey: string
}

const ACCESS_MODES: AccessModeOption[] = [
  { id: 'agent', number: '01', titleKey: 'agent.title', descriptionKey: 'agent.description' },
  { id: 'cli', number: '02', titleKey: 'cli.title', descriptionKey: 'cli.description' },
  { id: 'web', number: '03', titleKey: 'web.title', descriptionKey: 'web.description' },
]

function getRegistryUrl(): string {
  if (typeof window === 'undefined') {
    return 'https://skill.xfyun.cn'
  }

  return resolvePublicRegistryUrl(
    window.__SKILLHUB_RUNTIME_CONFIG__?.appBaseUrl,
    `${window.location.protocol}//${window.location.host}`,
  )
}

function AgentAccessPanel() {
  const { t } = useTranslation()
  const [activeView, setActiveView] = useState<AgentView>('registry')
  const [copied, copy] = useCopyToClipboard()
  const registryUrl = useMemo(getRegistryUrl, [])
  const instruction = t('landing.experience.quickStart.agent.instruction', { url: `${registryUrl}/registry/skill.md` })

  return (
    <div className="min-h-[340px]">
      <div className="flex flex-col gap-4 border-b border-border/70 pb-5 sm:flex-row sm:items-center sm:justify-between">
        <div className="inline-flex w-fit items-center gap-1 border-b border-border/70" role="tablist" aria-label={t('landing.experience.quickStart.agent.tablist')}>
          {([
            ['registry', t('landing.experience.quickStart.agent.registryTab')],
            ['discovery', t('landing.experience.quickStart.agent.discoveryTab')],
          ] as const).map(([id, label]) => (
            <button
              key={id}
              type="button"
              role="tab"
              aria-selected={activeView === id}
              onClick={() => setActiveView(id)}
              className={`relative px-4 py-2.5 text-xs font-semibold transition-[color,background-color] duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 ${activeView === id ? 'bg-background/70 text-foreground' : 'text-muted-foreground hover:bg-background/40 hover:text-foreground'}`}
            >
              {label}
              <span className={`absolute inset-x-0 -bottom-px h-0.5 origin-center bg-foreground transition-transform duration-200 ${activeView === id ? 'scale-x-100' : 'scale-x-0'}`} aria-hidden />
            </button>
          ))}
        </div>
        <span className="inline-flex items-center gap-2 text-xs text-muted-foreground">
          <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-emerald-500" />
          {t('landing.experience.quickStart.agent.live')}
        </span>
      </div>

      {activeView === 'registry' ? (
        <div className="animate-fade-up py-9">
          <p className="mb-5 text-xs text-muted-foreground">{t('landing.experience.quickStart.agent.instructionLead')}</p>
          <div className="flex flex-col gap-5 border-y border-border/70 py-5 sm:flex-row sm:items-start">
            <span className="mt-1 hidden h-12 w-1 flex-shrink-0 bg-foreground sm:block" aria-hidden />
            <p className="min-w-0 flex-1 text-sm leading-7 text-foreground">
              {t('landing.experience.quickStart.agent.instructionPrefix')} <span className="break-all text-blue-600 underline decoration-blue-300 underline-offset-4">{registryUrl}/registry/skill.md</span> {t('landing.experience.quickStart.agent.instructionSuffix')}
            </p>
            <button
              type="button"
              onClick={() => {
                void copy(instruction).catch(() => {
                  toast.error(t('landing.experience.quickStart.copyErrorTitle'), t('landing.experience.quickStart.agent.copyErrorDescription'))
                })
              }}
              className="inline-flex w-fit flex-shrink-0 items-center gap-2 rounded-lg bg-foreground px-4 py-2.5 text-xs font-semibold text-background shadow-sm transition-[transform,opacity,box-shadow] duration-150 hover:-translate-y-px hover:opacity-90 hover:shadow-md active:translate-y-0 active:scale-[0.98] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 motion-reduce:transform-none"
            >
              {copied ? <Check className="h-3.5 w-3.5" /> : <Copy className="h-3.5 w-3.5" />}
              {copied ? t('landing.experience.quickStart.copied') : t('landing.experience.quickStart.agent.copyInstruction')}
            </button>
          </div>
          <div className="mt-6 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
            {[
              t('landing.experience.quickStart.agent.steps.read'),
              t('landing.experience.quickStart.agent.steps.configure'),
              t('landing.experience.quickStart.agent.steps.discover'),
            ].map((step, index) => (
              <div key={step} className="flex items-center gap-2">
                {index > 0 ? <span className="h-px w-6 bg-border" /> : null}
                <span>{step}</span>
              </div>
            ))}
          </div>
        </div>
      ) : (
        <div className="animate-fade-up py-8">
          <div className="mb-7 flex justify-end">
            <span className="border-b border-foreground pb-2 text-sm text-foreground">{t('landing.experience.quickStart.agent.prompt')}</span>
          </div>
          <div className="relative space-y-0 pl-7 before:absolute before:bottom-3 before:left-[5px] before:top-3 before:w-px before:bg-border">
            {[
              [t('landing.experience.quickStart.agent.discovery.intentTitle'), t('landing.experience.quickStart.agent.discovery.intentDescription')],
              [t('landing.experience.quickStart.agent.discovery.searchTitle'), t('landing.experience.quickStart.agent.discovery.searchDescription')],
              [t('landing.experience.quickStart.agent.discovery.readTitle'), t('landing.experience.quickStart.agent.discovery.readDescription')],
            ].map(([title, description], index) => (
              <div key={title} className={`relative grid grid-cols-1 gap-1 border-b border-border/60 py-3.5 sm:grid-cols-[9rem_1fr] animate-fade-up delay-${index + 1}`}>
                <span className="absolute -left-[26px] top-[20px] h-2.5 w-2.5 rounded-full border-2 border-background bg-muted-foreground ring-1 ring-border" />
                <strong className="text-xs font-semibold text-foreground">{title}</strong>
                <span className="text-xs text-muted-foreground">{description}</span>
              </div>
            ))}
          </div>
          <p className="mt-6 border-t-2 border-foreground pt-5 text-sm leading-7 text-foreground">
            <strong className="text-emerald-700 dark:text-emerald-400">{t('landing.experience.quickStart.agent.answerLead')}</strong>{t('landing.experience.quickStart.agent.answerTail')}
          </p>
        </div>
      )}
    </div>
  )
}

function CliAccessPanel() {
  const { t } = useTranslation()
  const [copiedIdx, setCopiedIdx] = useState(-1)
  const registryUrl = useMemo(getRegistryUrl, [])

  const handleCopy = (text: string, idx: number) => {
    void copyToClipboard(text)
      .then(() => {
        setCopiedIdx(idx)
        window.setTimeout(() => setCopiedIdx((prev) => (prev === idx ? -1 : prev)), 2000)
      })
      .catch(() => {
        toast.error(t('landing.experience.quickStart.copyErrorTitle'), t('landing.experience.quickStart.cli.copyErrorDescription'))
      })
  }

  return (
    <div className="flex min-h-[380px] flex-col overflow-hidden rounded-2xl border border-border/70 shadow-sm">
      {/* Terminal title bar */}
      <div className="flex items-center justify-between border-b border-border/60 bg-white px-4 py-3 dark:bg-neutral-900">
        <div className="flex items-center gap-2">
          <div className="flex gap-1.5">
            <span className="h-3 w-3 rounded-full bg-[#ff5f57]" />
            <span className="h-3 w-3 rounded-full bg-[#febc2e]" />
            <span className="h-3 w-3 rounded-full bg-[#28c840]" />
          </div>
          <span className="ml-2 font-mono text-xs text-muted-foreground">Terminal — skillhub</span>
        </div>
        <div className="flex items-center gap-2">
          <span className="font-mono text-[10px] text-muted-foreground">zsh</span>
          <span className="text-[10px] text-border">·</span>
          <span className="font-mono text-[10px] text-muted-foreground">80×24</span>
        </div>
      </div>

      {/* Terminal content */}
      <div className="flex-1 overflow-auto bg-[#fafafa] p-4 font-mono text-[12px] leading-[1.7] dark:bg-neutral-950">
        {/* Version check */}
        <div className="mb-3">
          <div className="flex items-start gap-2">
            <span className="select-none font-bold text-emerald-600 dark:text-emerald-400">$</span>
            <div className="flex-1">
              <span className="text-neutral-800 dark:text-neutral-200">npx -y @astron-team/skillhub@0.1.12 --version</span>
              <button
                type="button"
                onClick={() => handleCopy('npx -y @astron-team/skillhub@0.1.12 --version', 0)}
                className="ml-2 inline-flex items-center gap-0.5 rounded px-1.5 py-0.5 text-[10px] text-muted-foreground align-middle transition-[color,background-color,transform] hover:bg-neutral-100 hover:text-foreground active:scale-95 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring dark:hover:bg-neutral-800"
              >
                {copiedIdx === 0 ? <Check className="h-3 w-3" /> : <Copy className="h-3 w-3" />}
                {copiedIdx === 0 ? t('landing.experience.quickStart.copied') : t('landing.experience.quickStart.copy')}
              </button>
            </div>
          </div>
          <div className="pl-5 text-muted-foreground">SkillHub CLI 0.1.12</div>
        </div>

        {/* Search */}
        <div className="mb-3">
          <div className="flex items-start gap-2">
            <span className="select-none font-bold text-emerald-600 dark:text-emerald-400">$</span>
            <div className="flex-1">
              <span className="text-neutral-800 dark:text-neutral-200">npx -y @astron-team/skillhub@0.1.12 search weather \</span>
              <button
                type="button"
                onClick={() => handleCopy(`npx -y @astron-team/skillhub@0.1.12 search weather --registry ${registryUrl} --limit 5`, 1)}
                className="ml-2 inline-flex items-center gap-0.5 rounded px-1.5 py-0.5 text-[10px] text-muted-foreground align-middle transition-[color,background-color,transform] hover:bg-neutral-100 hover:text-foreground active:scale-95 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring dark:hover:bg-neutral-800"
              >
                {copiedIdx === 1 ? <Check className="h-3 w-3" /> : <Copy className="h-3 w-3" />}
                {copiedIdx === 1 ? t('landing.experience.quickStart.copied') : t('landing.experience.quickStart.copy')}
              </button>
            </div>
          </div>
          <div className="pl-5 text-neutral-600 dark:text-neutral-400">
            <span className="text-muted-foreground">{'  --registry'} </span>
            <span className="break-all text-blue-600 dark:text-blue-400">{registryUrl}</span> \
          </div>
          <div className="pl-5 text-neutral-600 dark:text-neutral-400">
            <span className="text-muted-foreground">{'  --limit'} </span>
            <span className="text-neutral-800 dark:text-neutral-200">5</span>
          </div>
          <div className="mt-1 space-y-0.5 pl-5">
            <div className="text-[11px] text-muted-foreground">{t('landing.experience.quickStart.cli.skillsFound')}</div>
            <div className="flex gap-4">
              <span className="text-blue-600 dark:text-blue-400">@global/weather</span>
              <span className="text-muted-foreground">v1.3.0</span>
              <span className="text-neutral-600 dark:text-neutral-400">{t('landing.experience.demoSkills.weather')}</span>
            </div>
            <div className="flex gap-4">
              <span className="text-blue-600 dark:text-blue-400">@global/forecast</span>
              <span className="text-muted-foreground">v2.1.0</span>
              <span className="text-neutral-600 dark:text-neutral-400">{t('landing.experience.quickStart.cli.forecastSummary')}</span>
            </div>
          </div>
        </div>

        {/* Install */}
        <div className="mb-3">
          <div className="flex items-start gap-2">
            <span className="select-none font-bold text-emerald-600 dark:text-emerald-400">$</span>
            <div className="flex-1">
              <span className="text-neutral-800 dark:text-neutral-200">npx -y @astron-team/skillhub@0.1.12 install @global/weather \</span>
              <button
                type="button"
                onClick={() => handleCopy(`npx -y @astron-team/skillhub@0.1.12 install @global/weather --dir ./skills --registry ${registryUrl}`, 2)}
                className="ml-2 inline-flex items-center gap-0.5 rounded px-1.5 py-0.5 text-[10px] text-muted-foreground align-middle transition-[color,background-color,transform] hover:bg-neutral-100 hover:text-foreground active:scale-95 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring dark:hover:bg-neutral-800"
              >
                {copiedIdx === 2 ? <Check className="h-3 w-3" /> : <Copy className="h-3 w-3" />}
                {copiedIdx === 2 ? t('landing.experience.quickStart.copied') : t('landing.experience.quickStart.copy')}
              </button>
            </div>
          </div>
          <div className="pl-5 text-neutral-600 dark:text-neutral-400">
            <span className="text-muted-foreground">{'  --dir'} </span>
            <span className="text-neutral-800 dark:text-neutral-200">./skills</span> \
          </div>
          <div className="pl-5 text-neutral-600 dark:text-neutral-400">
            <span className="text-muted-foreground">{'  --registry'} </span>
            <span className="break-all text-blue-600 dark:text-blue-400">{registryUrl}</span>
          </div>
          <div className="mt-1 flex items-center gap-1.5 pl-5 text-[11px] text-emerald-600 dark:text-emerald-400">
            <Check className="h-3 w-3" strokeWidth={2.5} />
            <span>{t('landing.experience.quickStart.cli.installed')}</span>
          </div>
        </div>

        {/* Cursor */}
        <div className="flex items-center gap-2">
          <span className="select-none font-bold text-emerald-600 dark:text-emerald-400">$</span>
          <span className="inline-block h-3.5 w-2 animate-pulse bg-neutral-700 dark:bg-neutral-400" />
        </div>
      </div>

      {/* Bottom status bar */}
      <div className="flex flex-wrap items-center justify-between gap-2 border-t border-border/60 bg-white px-4 py-2.5 text-[11px] dark:bg-neutral-900">
        <div className="flex min-w-0 flex-wrap items-center gap-x-3 gap-y-1 text-muted-foreground">
          <span className="flex items-center gap-1">
            <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
            {t('landing.experience.quickStart.cli.connected')}
          </span>
          <span className="text-border">·</span>
          <span className="break-all font-mono">registry: {registryUrl}</span>
        </div>
        <a href="https://github.com/iflytek/skillhub/tree/main/cli" target="_blank" rel="noreferrer" className="group flex items-center gap-1 font-medium text-blue-600 transition-colors hover:text-blue-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring dark:text-blue-400 dark:hover:text-blue-300">
          {t('landing.experience.quickStart.cli.docs')}
          <ChevronRight className="h-3 w-3 transition-transform group-hover:translate-x-0.5 motion-reduce:transform-none" />
        </a>
      </div>
    </div>
  )
}

function WebAccessPanel() {
  const { t } = useTranslation()

  return (
    <div className="min-h-[340px]">
      <div className="grid gap-6 md:grid-cols-[11rem_minmax(0,1fr)]">
        <div className="border-b border-border/70 pb-5 md:border-b-0 md:border-r md:pb-0 md:pr-5">
          <div className="mb-4 flex items-center justify-between">
            <strong className="text-xs font-semibold text-foreground">{t('landing.experience.quickStart.web.marketplace')}</strong>
            <span className="text-[10px] text-muted-foreground">{t('landing.experience.quickStart.web.publicSkills')}</span>
          </div>
          <div className="mb-3 flex items-center gap-2 border-b border-border/70 pb-2.5 text-[11px] text-muted-foreground">
            <Search className="h-3.5 w-3.5" />
            {t('landing.experience.quickStart.web.search')}
          </div>
          {[
            ['W', 'weather', '@global'],
            ['G', 'git-helper', '@devtools'],
            ['D', 'diagram-maker', '@global'],
          ].map(([letter, name, namespace], index) => (
            <div key={name} className={`flex items-center gap-2.5 border-b border-border/50 py-3 ${index === 0 ? 'text-foreground' : 'text-muted-foreground'}`}>
              <span className={`flex h-7 w-7 items-center justify-center rounded-md text-[10px] font-bold ${index === 0 ? 'bg-indigo-50 text-indigo-600 dark:bg-indigo-500/10 dark:text-indigo-300' : 'bg-secondary'}`}>{letter}</span>
              <span className="min-w-0">
                <strong className="block truncate text-[11px] font-semibold">{name}</strong>
                <span className="font-mono text-[9px]">{namespace}</span>
              </span>
            </div>
          ))}
        </div>

        <div className="py-1">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
            <div>
              <span className="inline-flex items-center gap-1.5 text-[10px] font-medium text-emerald-700 dark:text-emerald-400">
                <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />{t('landing.experience.quickStart.web.status')}
              </span>
              <h3 className="mt-2 text-xl font-semibold tracking-tight text-foreground">weather <span className="font-mono text-xs font-normal text-muted-foreground">@global</span></h3>
              <p className="mt-1 text-xs text-muted-foreground">{t('landing.experience.quickStart.web.summary')}</p>
            </div>
            <a href={buildApiUrl(`${WEB_API_PREFIX}/skills/global/weather/download`)} className="inline-flex w-fit items-center gap-2 rounded-lg bg-foreground px-4 py-2.5 text-xs font-semibold text-background shadow-sm transition-[transform,opacity,box-shadow] duration-150 hover:-translate-y-px hover:opacity-90 hover:shadow-md active:translate-y-0 active:scale-[0.98] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 motion-reduce:transform-none">
              <Download className="h-3.5 w-3.5" />{t('landing.experience.quickStart.web.download')}
            </a>
          </div>

          <div className="mt-6 grid gap-5 border-t border-border/70 pt-5 sm:grid-cols-[minmax(0,1fr)_8rem]">
            <div>
              <div className="mb-3 flex items-center gap-2 text-xs font-semibold text-foreground">
                <FileText className="h-3.5 w-3.5" />
                {t('landing.experience.quickStart.web.readTitle')}
              </div>
              <p className="text-[11px] leading-6 text-muted-foreground">
                {t('landing.experience.quickStart.web.readDescription')}
              </p>
            </div>
            <dl className="space-y-3 text-[10px]">
              <div className="flex justify-between border-b border-border/60 pb-2"><dt className="text-muted-foreground">{t('landing.experience.quickStart.web.version')}</dt><dd className="font-mono text-foreground">v1.3.0</dd></div>
              <div className="flex justify-between border-b border-border/60 pb-2"><dt className="text-muted-foreground">{t('landing.experience.quickStart.web.files')}</dt><dd className="text-foreground">3</dd></div>
              <div className="flex justify-between"><dt className="text-muted-foreground">{t('landing.experience.quickStart.web.format')}</dt><dd className="text-foreground">ZIP</dd></div>
            </dl>
          </div>

          <div className="mt-6 flex flex-wrap gap-x-5 gap-y-2 text-[11px] text-muted-foreground">
            <span>{t('landing.experience.quickStart.web.browse')}</span><span>{t('landing.experience.quickStart.web.preview')}</span><span>{t('landing.experience.quickStart.web.versions')}</span><span>{t('landing.experience.quickStart.web.favorites')}</span>
          </div>
        </div>
      </div>
    </div>
  )
}

export function LandingQuickStartSection() {
  const { t } = useTranslation()
  const [activeMode, setActiveMode] = useState<AccessMode>('agent')

  return (
    <section id="quickstart" className="relative z-10 w-full overflow-hidden bg-secondary/70 px-6 py-16 md:py-20">
      <div className="absolute inset-0 bg-dots opacity-40" aria-hidden />
      <div className="relative mx-auto max-w-6xl">
        <div className="mb-12 max-w-3xl">
          <p className="mb-3 text-[11px] font-semibold uppercase tracking-[0.16em] text-muted-foreground">{t('landing.quickStart.title')}</p>
          <h2 className="mb-4 text-3xl font-medium tracking-tight text-foreground md:text-4xl">{t('landing.experience.quickStart.title')}</h2>
          <p className="max-w-2xl text-lg leading-relaxed text-muted-foreground">{t('landing.experience.quickStart.description')}</p>
        </div>

        <div className="grid grid-cols-1 gap-8 lg:grid-cols-[18rem_minmax(0,1fr)] lg:gap-12">
          <div className="border-t border-border/70">
            {ACCESS_MODES.map((mode) => {
              const active = activeMode === mode.id
              return (
                <button
                  key={mode.id}
                  type="button"
                  aria-pressed={active}
                  onClick={() => setActiveMode(mode.id)}
                  className={`group relative flex w-full items-center gap-4 border-b border-border/70 px-2 py-5 text-left transition-[background-color,transform] duration-200 focus-visible:z-10 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-inset ${active ? 'bg-background/65' : 'hover:bg-background/35 active:translate-x-px'}`}
                >
                  <span className={`absolute inset-y-3 left-0 w-0.5 origin-center bg-foreground transition-transform duration-200 ${active ? 'scale-y-100' : 'scale-y-0'}`} aria-hidden />
                  <span className={`font-mono text-[11px] font-semibold transition-colors duration-200 ${active ? 'text-foreground' : 'text-muted-foreground'}`}>{mode.number}</span>
                  <span className="min-w-0 flex-1">
                    <strong className={`block text-sm font-semibold transition-colors ${active ? 'text-foreground' : 'text-muted-foreground group-hover:text-foreground'}`}>{t(`landing.experience.quickStart.modes.${mode.titleKey}`)}</strong>
                    <span className="mt-1 block text-[11px] text-muted-foreground">{t(`landing.experience.quickStart.modes.${mode.descriptionKey}`)}</span>
                  </span>
                  <span className={`h-1.5 w-1.5 rounded-full transition-colors ${active ? 'bg-foreground' : 'bg-border'}`} />
                </button>
              )
            })}
          </div>

          <div key={activeMode} className="animate-fade-up border-t border-border/70 pt-5">
            {activeMode === 'agent' ? <AgentAccessPanel /> : null}
            {activeMode === 'cli' ? <CliAccessPanel /> : null}
            {activeMode === 'web' ? <WebAccessPanel /> : null}
          </div>
        </div>
      </div>
    </section>
  )
}
