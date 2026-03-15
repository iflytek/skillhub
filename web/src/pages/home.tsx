import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { SearchBar } from '@/features/search/search-bar'
import { SkillCard } from '@/features/skill/skill-card'
import { SkeletonList } from '@/shared/components/skeleton-loader'
import { useSearchSkills } from '@/shared/hooks/use-skill-queries'
import { Button } from '@/shared/ui/button'
import { Check, Copy, Terminal, Settings, PackageOpen } from 'lucide-react'
import { useState, useMemo } from 'react'

function getAppBaseUrl(): string {
  if (typeof window === 'undefined') {
    return 'https://skill.xfyun.cn'
  }
  const runtimeConfig = window.__SKILLHUB_RUNTIME_CONFIG__
  if (runtimeConfig?.appBaseUrl) {
    return runtimeConfig.appBaseUrl
  }
  return `${window.location.protocol}//${window.location.host}`
}

function CopyButton({ text }: { text: string }) {
  const { t } = useTranslation()
  const [copied, setCopied] = useState(false)

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(text)
      setCopied(true)
      window.setTimeout(() => setCopied(false), 2000)
    } catch (err) {
      console.error('Failed to copy:', err)
    }
  }

  return (
    <Button
      type="button"
      variant="ghost"
      size="icon"
      onClick={handleCopy}
      title={copied ? t('copyButton.copied') || 'Copied' : t('copyButton.copy') || 'Copy'}
      aria-label={copied ? t('copyButton.copied') || 'Copied' : t('copyButton.copy') || 'Copy'}
      className="h-8 w-8"
    >
      {copied ? <Check className="h-4 w-4" /> : <Copy className="h-4 w-4" />}
    </Button>
  )
}

function CodeBlock({ code }: { code: string }) {
  return (
    <div className="relative group">
      <div className="flex items-center justify-between px-4 py-2 bg-slate-900 border-b border-slate-700 rounded-t-lg">
        <span className="text-xs text-slate-400 font-mono">bash</span>
        <CopyButton text={code} />
      </div>
      <pre className="p-4 bg-slate-950 rounded-b-lg overflow-x-auto">
        <code className="font-mono text-sm text-slate-200">{code}</code>
      </pre>
    </div>
  )
}

function QuickStartSection() {
  const { t } = useTranslation()
  const baseUrl = useMemo(() => getAppBaseUrl(), [])

  const steps = [
    {
      icon: <Settings className="h-6 w-6" />,
      title: t('home.quickStart.steps.configureEnv.title'),
      description: t('home.quickStart.steps.configureEnv.description'),
      code: `# Linux/macOS
export CLAWHUB_SITE=${baseUrl}
export CLAWHUB_REGISTRY=${baseUrl}

# Windows PowerShell
$env:CLAWHUB_SITE = '${baseUrl}'
$env:CLAWHUB_REGISTRY = '${baseUrl}'`,
    },
    {
      icon: <Terminal className="h-6 w-6" />,
      title: t('home.quickStart.steps.installSkills.title'),
      description: t('home.quickStart.steps.installSkills.description'),
      code: t('home.quickStart.steps.installSkills.code'),
    },
    {
      icon: <PackageOpen className="h-6 w-6" />,
      title: t('home.quickStart.steps.publishSkills.title'),
      description: t('home.quickStart.steps.publishSkills.description'),
      code: t('home.quickStart.steps.publishSkills.code'),
    },
  ]

  return (
    <section className="space-y-8 py-12">
      <div className="text-center space-y-4">
        <h2 className="text-3xl md:text-4xl font-bold font-heading text-foreground">
          {t('home.quickStart.title')}
          <span className="block text-lg font-normal text-muted-foreground mt-2">
            {t('home.quickStart.subtitle')}
          </span>
        </h2>
        <p className="text-muted-foreground max-w-2xl mx-auto">
          {t('home.quickStart.description')}
        </p>
      </div>

      <div className="grid md:grid-cols-1 gap-6 max-w-4xl mx-auto">
        {steps.map((step, idx) => (
          <div
            key={idx}
            className="relative p-6 rounded-2xl bg-card border border-border hover:border-cyan-500/50 transition-all duration-300"
          >
            <div className="flex items-start gap-4">
              <div className="flex-shrink-0 p-3 rounded-xl bg-cyan-500/10 text-cyan-400">
                {step.icon}
              </div>
              <div className="flex-1 space-y-4">
                <div>
                  <h3 className="text-xl font-bold text-foreground">
                    {step.title}
                  </h3>
                  <p className="text-sm text-muted-foreground">
                    {step.description}
                  </p>
                </div>
                <CodeBlock code={step.code} />
              </div>
            </div>
          </div>
        ))}
      </div>

      <div className="text-center pt-4">
        <div className="inline-flex items-center gap-2 px-4 py-2 rounded-full bg-yellow-500/10 border border-yellow-500/30">
          <span className="text-sm text-yellow-300">
            {t('home.quickStart.tip')}
          </span>
        </div>
      </div>
    </section>
  )
}

export function HomePage() {
  const { t } = useTranslation()
  const navigate = useNavigate()

  const { data: popularSkills, isLoading: isLoadingPopular } = useSearchSkills({
    sort: 'downloads',
    size: 6,
  })

  const { data: latestSkills, isLoading: isLoadingLatest } = useSearchSkills({
    sort: 'newest',
    size: 6,
  })

  const handleSearch = (query: string) => {
    navigate({ to: '/search', search: { q: query, sort: 'relevance', page: 0, starredOnly: false } })
  }

  const handleSkillClick = (namespace: string, slug: string) => {
    navigate({ to: `/space/${namespace}/${slug}` })
  }

  return (
    <div className="space-y-20">
      {/* Hero Section */}
      <div className="text-center space-y-8 py-16 animate-fade-up">
        <div className="space-y-4">
          <h1 className="text-6xl md:text-7xl lg:text-8xl font-bold font-heading text-gradient-hero leading-tight">
            SkillHub
          </h1>
          <p className="text-xl md:text-2xl text-muted-foreground font-light max-w-2xl mx-auto">
            {t('home.subtitle')}
          </p>
          <p className="text-base text-muted-foreground/80 max-w-xl mx-auto">
            {t('home.description')}
          </p>
        </div>

        <div className="max-w-2xl mx-auto animate-fade-up delay-1">
          <SearchBar onSearch={handleSearch} />
        </div>

        <div className="flex items-center justify-center gap-4 animate-fade-up delay-2">
          <Button size="lg" onClick={() => navigate({ to: '/search', search: { q: '', sort: 'relevance', page: 0, starredOnly: false } })}>
            {t('home.browseSkills')}
          </Button>
          <Button size="lg" variant="outline" onClick={() => navigate({ to: '/dashboard/publish' })}>
            {t('home.publishSkill')}
          </Button>
        </div>
      </div>

      {/* Popular Downloads Section */}
      <section className="space-y-6 animate-fade-up">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-3xl font-bold font-heading text-foreground mb-2">{t('home.popularTitle')}</h2>
            <p className="text-muted-foreground">{t('home.popularDescription')}</p>
          </div>
          <Button
            variant="ghost"
            onClick={() => navigate({ to: '/search', search: { q: '', sort: 'downloads', page: 0, starredOnly: false } })}
          >
            {t('home.viewAll')}
          </Button>
        </div>
        {isLoadingPopular ? (
          <SkeletonList count={6} />
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
            {popularSkills?.items.map((skill, idx) => (
              <div key={skill.id} className={`animate-fade-up delay-${Math.min(idx + 1, 6)}`}>
                <SkillCard
                  skill={skill}
                  onClick={() => handleSkillClick(skill.namespace, skill.slug)}
                />
              </div>
            ))}
          </div>
        )}
      </section>

      {/* Latest Releases Section */}
      <section className="space-y-6 animate-fade-up">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-3xl font-bold font-heading text-foreground mb-2">{t('home.latestTitle')}</h2>
            <p className="text-muted-foreground">{t('home.latestDescription')}</p>
          </div>
          <Button
            variant="ghost"
            onClick={() => navigate({ to: '/search', search: { q: '', sort: 'newest', page: 0, starredOnly: false } })}
          >
            {t('home.viewAll')}
          </Button>
        </div>
        {isLoadingLatest ? (
          <SkeletonList count={6} />
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
            {latestSkills?.items.map((skill, idx) => (
              <div key={skill.id} className={`animate-fade-up delay-${Math.min(idx + 1, 6)}`}>
                <SkillCard
                  skill={skill}
                  onClick={() => handleSkillClick(skill.namespace, skill.slug)}
                />
              </div>
            ))}
          </div>
        )}
      </section>

      {/* Quick Start Section */}
      <QuickStartSection />
    </div>
  )
}
