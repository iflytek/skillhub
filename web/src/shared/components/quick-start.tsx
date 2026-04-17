import { useTranslation } from 'react-i18next'
import { Check, Copy, Settings, Download, Upload } from 'lucide-react'
import { useMemo } from 'react'
import { useCopyToClipboard } from '@/shared/lib/clipboard'

function getAppBaseUrl(): string {
  if (typeof window === 'undefined') {
    return 'https://skill.xfyun.cn'
  }
  const runtimeConfig = (window as unknown as Record<string, unknown>).__SKILLHUB_RUNTIME_CONFIG__ as { appBaseUrl?: string } | undefined
  const configuredUrl = runtimeConfig?.appBaseUrl
  // Use configured URL only if it's set and not localhost
  if (configuredUrl && !configuredUrl.includes('localhost')) {
    return configuredUrl
  }
  // Fallback to current page origin
  return `${window.location.protocol}//${window.location.host}`
}

function CopyButton({ text }: { text: string }) {
  const { t } = useTranslation()
  const [copied, copy] = useCopyToClipboard()

  const handleCopy = async () => {
    try {
      await copy(text)
    } catch (err) {
      console.error('Failed to copy:', err)
    }
  }

  return (
    <button
      type="button"
      onClick={handleCopy}
      className="ml-4 flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs text-current transition-colors hover:bg-white/10"
      title={copied ? (t('copyButton.copied') || 'Copied') : (t('copyButton.copy') || 'Copy')}
    >
      {copied ? <Check className="w-3.5 h-3.5" /> : <Copy className="w-3.5 h-3.5" />}
      {copied ? t('copyButton.copied') : t('copyButton.copy')}
    </button>
  )
}

function CodeLine({ line }: { line: string }) {
  if (line.startsWith('#')) {
    return <span className="opacity-70">{line}</span>
  }
  if (line.startsWith('export')) {
    return (
      <>
        <span className="text-primary">export</span>
        <span>{line.slice(6)}</span>
      </>
    )
  }
  if (line.startsWith('$env:')) {
    const eqIdx = line.indexOf('=')
    return (
      <>
        <span className="text-primary">{line.slice(0, eqIdx).trim()}</span>
        <span>{` = ${line.slice(eqIdx + 1).trim()}`}</span>
      </>
    )
  }
  if (line.startsWith('clawhub')) {
    return (
      <>
        <span className="text-primary">clawhub</span>
        <span>{line.slice(7)}</span>
      </>
    )
  }
  return <span>{line}</span>
}

interface CodeBlockProps {
  icon: React.ReactNode
  iconClassName: string
  title: string
  description: string
  code: string
}

function CodeBlock({ icon, iconClassName, title, description, code }: CodeBlockProps) {
  return (
    <div className="code-block overflow-hidden">
      <div className="flex items-center justify-between border-b border-border/40 px-6 py-4">
        <div className="flex items-center gap-3">
          <div className={`flex h-9 w-9 items-center justify-center rounded-lg ${iconClassName}`}>
            {icon}
          </div>
          <div>
            <div className="text-sm font-medium">{title}</div>
            <div className="text-xs opacity-70">
              {description}
            </div>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <span className="w-3 h-3 rounded-full bg-red-500/80" />
          <span className="w-3 h-3 rounded-full bg-amber-500/80" />
          <span className="w-3 h-3 rounded-full bg-emerald-500/80" />
          <CopyButton text={code} />
        </div>
      </div>
      <div className="whitespace-pre-wrap p-6 font-mono text-sm leading-relaxed">
        {code.split('\n').map((line, i) => (
          <div key={i}>
            <CodeLine line={line} />
          </div>
        ))}
      </div>
    </div>
  )
}

interface QuickStartProps {
  /** 'landing' uses full-width section with centered title; 'page' uses inline layout */
  variant?: 'landing' | 'page'
  /** i18n namespace prefix, e.g. 'landing' or 'home' */
  ns?: string
}

export function QuickStartSection({ variant = 'page', ns = 'landing' }: QuickStartProps) {
  const { t } = useTranslation()
  const baseUrl = useMemo(() => getAppBaseUrl(), [])

  const envCode = `# Linux/macOS
export CLAWHUB_SITE=${baseUrl}
export CLAWHUB_REGISTRY=${baseUrl}

# Windows PowerShell
$env:CLAWHUB_SITE = '${baseUrl}'
$env:CLAWHUB_REGISTRY = '${baseUrl}'`

  const installCode = t(`${ns}.quickStart.steps.installSkills.code`, {
    defaultValue: '# 搜索技能\nclawhub search <keyword>\n\n# 安装技能\nclawhub install <skill>',
  })

  const publishCode = t(`${ns}.quickStart.steps.publishSkills.code`, {
    defaultValue: '# 发布技能\nclawhub publish\n\n# 或使用网页界面\n# 点击"发布技能"',
  })

  const steps: CodeBlockProps[] = [
    {
      icon: <Settings className="w-4 h-4" strokeWidth={1.5} />,
      iconClassName: 'bg-primary/15 text-primary',
      title: t(`${ns}.quickStart.steps.configureEnv.title`),
      description: t(`${ns}.quickStart.steps.configureEnv.description`),
      code: envCode,
    },
    {
      icon: <Download className="w-4 h-4" strokeWidth={1.5} />,
      iconClassName: 'bg-secondary/90 text-foreground',
      title: t(`${ns}.quickStart.steps.installSkills.title`),
      description: t(`${ns}.quickStart.steps.installSkills.description`),
      code: installCode,
    },
    {
      icon: <Upload className="w-4 h-4" strokeWidth={1.5} />,
      iconClassName: 'bg-accent/15 text-accent',
      title: t(`${ns}.quickStart.steps.publishSkills.title`),
      description: t(`${ns}.quickStart.steps.publishSkills.description`),
      code: publishCode,
    },
  ]

  if (variant === 'landing') {
    return (
      <section className="relative z-10 w-full bg-background px-6 py-20 md:py-24">
        <div className="max-w-4xl mx-auto">
          <div className="text-center mb-10">
            <h2 className="mb-3 text-3xl font-bold tracking-tight text-foreground md:text-4xl">
              {t(`${ns}.quickStart.title`)}
            </h2>
            <p className="mb-2 text-sm font-medium uppercase tracking-wider text-muted-foreground">
              Quick Start
            </p>
            <p className="mx-auto max-w-2xl text-base leading-relaxed text-muted-foreground md:text-lg">
              {t(`${ns}.quickStart.description`, { defaultValue: t(`${ns}.quickStart.subtitle`) })}
            </p>
          </div>
          <div className="space-y-6">
            {steps.map((step, idx) => (
              <CodeBlock key={idx} {...step} />
            ))}
          </div>
        </div>
      </section>
    )
  }

  return (
    <section className="space-y-6 animate-fade-up">
      <div>
        <h2 className="mb-2 text-3xl font-bold tracking-tight text-foreground">
          {t(`${ns}.quickStart.title`)}
        </h2>
        <p className="text-muted-foreground">
          {t(`${ns}.quickStart.description`, { defaultValue: t(`${ns}.quickStart.subtitle`) })}
        </p>
      </div>
      <div className="space-y-6">
        {steps.map((step, idx) => (
          <CodeBlock key={idx} {...step} />
        ))}
      </div>
    </section>
  )
}
