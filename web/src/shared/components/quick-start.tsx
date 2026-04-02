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
      className="ml-4 text-xs px-3 py-1.5 rounded-lg flex items-center gap-1.5 transition-colors hover:bg-white/10"
      style={{ color: 'var(--code-url, #CBD5E0)' }}
      title={copied ? (t('copyButton.copied') || 'Copied') : (t('copyButton.copy') || 'Copy')}
    >
      {copied ? <Check className="w-3.5 h-3.5" /> : <Copy className="w-3.5 h-3.5" />}
      {copied ? t('copyButton.copied') : t('copyButton.copy')}
    </button>
  )
}

function CodeLine({ line }: { line: string }) {
  if (line.startsWith('#')) {
    return <span style={{ color: 'var(--code-comment, #A0AEC0)' }}>{line}</span>
  }
  if (line.startsWith('export')) {
    return (
      <>
        <span style={{ color: 'var(--code-keyword, #5EEAD4)' }}>export</span>
        <span style={{ color: 'var(--code-url, #CBD5E0)' }}>{line.slice(6)}</span>
      </>
    )
  }
  if (line.startsWith('$env:')) {
    const eqIdx = line.indexOf('=')
    return (
      <>
        <span style={{ color: 'var(--code-keyword, #5EEAD4)' }}>{line.slice(0, eqIdx).trim()}</span>
        <span style={{ color: 'var(--code-url, #CBD5E0)' }}>{` = ${line.slice(eqIdx + 1).trim()}`}</span>
      </>
    )
  }
  if (line.startsWith('clawhub')) {
    return (
      <>
        <span style={{ color: 'var(--code-keyword, #5EEAD4)' }}>clawhub</span>
        <span>{line.slice(7)}</span>
      </>
    )
  }
  return <span>{line}</span>
}

interface CodeBlockProps {
  icon: React.ReactNode
  iconBg: string
  iconColor: string
  title: string
  description: string
  code: string
}

function CodeBlock({ icon, iconBg, iconColor, title, description, code }: CodeBlockProps) {
  return (
    <div className="code-block overflow-hidden">
      <div className="px-6 py-4 border-b flex items-center justify-between" style={{ borderColor: 'rgba(255,255,255,0.06)' }}>
        <div className="flex items-center gap-3">
          <div
            className="w-9 h-9 rounded-lg flex items-center justify-center"
            style={{ background: iconBg, color: iconColor }}
          >
            {icon}
          </div>
          <div>
            <div className="text-sm font-medium text-white">{title}</div>
            <div className="text-xs" style={{ color: 'var(--code-comment, #A0AEC0)' }}>
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
      <div className="p-6 font-mono text-sm leading-relaxed whitespace-pre-wrap" style={{ color: 'var(--code-url, #CBD5E0)' }}>
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
      iconBg: 'rgba(94,234,212,0.15)',
      iconColor: 'var(--code-keyword, #5EEAD4)',
      title: t(`${ns}.quickStart.steps.configureEnv.title`),
      description: t(`${ns}.quickStart.steps.configureEnv.description`),
      code: envCode,
    },
    {
      icon: <Download className="w-4 h-4" strokeWidth={1.5} />,
      iconBg: 'rgba(96,165,250,0.15)',
      iconColor: '#60A5FA',
      title: t(`${ns}.quickStart.steps.installSkills.title`),
      description: t(`${ns}.quickStart.steps.installSkills.description`),
      code: installCode,
    },
    {
      icon: <Upload className="w-4 h-4" strokeWidth={1.5} />,
      iconBg: 'rgba(167,139,250,0.15)',
      iconColor: '#A78BFA',
      title: t(`${ns}.quickStart.steps.publishSkills.title`),
      description: t(`${ns}.quickStart.steps.publishSkills.description`),
      code: publishCode,
    },
  ]

  if (variant === 'landing') {
    return (
      <section className="relative z-10 w-full py-20 md:py-24 px-6" style={{ background: 'var(--bg-page, hsl(var(--background)))' }}>
        <div className="max-w-4xl mx-auto">
          <div className="text-center mb-10">
            <h2 className="text-3xl md:text-4xl font-bold tracking-tight mb-3" style={{ color: 'hsl(var(--foreground))' }}>
              {t(`${ns}.quickStart.title`)}
            </h2>
            <p className="text-sm uppercase tracking-widest font-medium mb-2" style={{ color: 'hsl(var(--muted-foreground))' }}>
              Quick Start
            </p>
            <p className="text-base md:text-lg max-w-2xl mx-auto leading-relaxed" style={{ color: 'hsl(var(--text-secondary))' }}>
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
        <h2 className="text-3xl font-bold tracking-tight mb-2" style={{ color: 'hsl(var(--foreground))' }}>
          {t(`${ns}.quickStart.title`)}
        </h2>
        <p style={{ color: 'hsl(var(--text-secondary))' }}>
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
