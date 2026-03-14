import { Link, useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { SearchBar } from '@/features/search/search-bar'
import { useAuth } from '@/features/auth/use-auth'
import { LanguageSwitcher } from '@/shared/components/language-switcher'
import { UserMenu } from '@/shared/components/user-menu'
import { Button } from '@/shared/ui/button'
import { useEffect, useRef, useState } from 'react'

export function LandingPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { user, isLoading } = useAuth()
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const [stats] = useState({
    skills: '1000+',
    downloads: '50K+',
    teams: '200+',
  })

  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas) return

    const ctx = canvas.getContext('2d')
    if (!ctx) return

    const resize = () => {
      if (canvas) {
        canvas.width = window.innerWidth
        canvas.height = window.innerHeight
      }
    }
    resize()
    window.addEventListener('resize', resize)

    class Particle {
      x: number
      y: number
      vx: number
      vy: number
      radius: number

      constructor(canvasWidth: number, canvasHeight: number) {
        this.x = Math.random() * canvasWidth
        this.y = Math.random() * canvasHeight
        this.vx = (Math.random() - 0.5) * 0.3
        this.vy = (Math.random() - 0.5) * 0.3
        this.radius = Math.random() * 1.5 + 0.5
      }

      update(canvasWidth: number, canvasHeight: number) {
        this.x += this.vx
        this.y += this.vy
        if (this.x < 0 || this.x > canvasWidth) this.vx *= -1
        if (this.y < 0 || this.y > canvasHeight) this.vy *= -1
      }

      draw(context: CanvasRenderingContext2D) {
        context.beginPath()
        context.arc(this.x, this.y, this.radius, 0, Math.PI * 2)
        context.fillStyle = 'rgba(56, 189, 248, 0.6)'
        context.fill()
      }
    }

    const particles: Particle[] = []
    const particleCount = 80

    for (let i = 0; i < particleCount; i++) {
      particles.push(new Particle(canvas.width, canvas.height))
    }

    const connectParticles = () => {
      for (let i = 0; i < particles.length; i++) {
        for (let j = i + 1; j < particles.length; j++) {
          const dx = particles[i].x - particles[j].x
          const dy = particles[i].y - particles[j].y
          const distance = Math.sqrt(dx * dx + dy * dy)

          if (distance < 120) {
            ctx.beginPath()
            const opacity = 0.15 * (1 - distance / 120)
            ctx.strokeStyle = 'rgba(56, 189, 248, ' + opacity + ')'
            ctx.lineWidth = 0.5
            ctx.moveTo(particles[i].x, particles[i].y)
            ctx.lineTo(particles[j].x, particles[j].y)
            ctx.stroke()
          }
        }
      }
    }

    const animate = () => {
      if (!canvas) return
      ctx.clearRect(0, 0, canvas.width, canvas.height)

      particles.forEach(particle => {
        particle.update(canvas.width, canvas.height)
        particle.draw(ctx)
      })

      connectParticles()
      requestAnimationFrame(animate)
    }

    animate()

    return () => {
      window.removeEventListener('resize', resize)
    }
  }, [])

  const handleSearch = (query: string) => {
    navigate({ to: '/search', search: { q: query, sort: 'relevance', page: 0 } })
  }

  const features = [
    {
      icon: '🔒',
      title: t('landing.featuresList.privateDeploy.title'),
      description: t('landing.featuresList.privateDeploy.description'),
    },
    {
      icon: '📦',
      title: t('landing.featuresList.versionControl.title'),
      description: t('landing.featuresList.versionControl.description'),
    },
    {
      icon: '🔍',
      title: t('landing.featuresList.smartSearch.title'),
      description: t('landing.featuresList.smartSearch.description'),
    },
    {
      icon: '👥',
      title: t('landing.featuresList.teamwork.title'),
      description: t('landing.featuresList.teamwork.description'),
    },
    {
      icon: '✅',
      title: t('landing.featuresList.governance.title'),
      description: t('landing.featuresList.governance.description'),
    },
    {
      icon: '⚡',
      title: t('landing.featuresList.cliFirst.title'),
      description: t('landing.featuresList.cliFirst.description'),
    },
  ]

  return (
    <div className="relative min-h-screen overflow-hidden bg-gradient-to-br from-slate-950 via-indigo-950 to-slate-900">
      <header className="sticky top-0 z-50 border-b border-slate-700/30 bg-slate-950/60 backdrop-blur-lg">
        <div className="container mx-auto flex items-center justify-between px-6 py-3">
          <Link to="/" className="text-lg font-bold text-transparent bg-clip-text bg-gradient-to-r from-cyan-300 to-blue-400">
            SkillHub
          </Link>
          <nav className="flex items-center gap-4 [&_button]:text-slate-200 [&_button:hover]:text-cyan-300 [&_svg]:text-slate-300">
            <LanguageSwitcher />
            {isLoading ? null : user ? (
              <UserMenu user={user} />
            ) : (
              <Link
                to="/login"
                search={{ returnTo: '' }}
                className="text-sm text-slate-200 hover:text-cyan-400 transition-colors"
              >
                {t('nav.login')}
              </Link>
            )}
          </nav>
        </div>
      </header>

      <canvas
        ref={canvasRef}
        className="absolute inset-0 pointer-events-none"
        style={{ opacity: 0.4 }}
      />

      <div className="absolute top-0 left-1/4 w-96 h-96 bg-cyan-500/20 rounded-full blur-3xl" style={{ animation: 'pulse 4s ease-in-out infinite' }} />
      <div className="absolute bottom-0 right-1/4 w-96 h-96 bg-violet-500/20 rounded-full blur-3xl" style={{ animation: 'pulse 4s ease-in-out infinite 2s' }} />

      <div className="relative z-10 container mx-auto px-6 py-20">
        <div className="flex flex-col items-center text-center space-y-12 pt-20 pb-32">
          <div className="inline-flex items-center gap-2 px-4 py-2 rounded-full bg-cyan-500/10 border border-cyan-500/30 backdrop-blur-sm animate-fade-in">
            <div className="w-2 h-2 rounded-full bg-cyan-400 animate-pulse" />
            <span className="text-sm text-cyan-300 font-medium">{t('landing.badge')}</span>
          </div>

          <div className="space-y-6 max-w-5xl">
            <h1 className="text-7xl md:text-8xl lg:text-9xl font-black tracking-normal animate-fade-up">
              <span className="block text-transparent bg-clip-text bg-gradient-to-r from-cyan-300 via-blue-400 to-violet-400">
                SkillHub
              </span>
            </h1>
            <p className="text-2xl md:text-3xl text-slate-300 font-light leading-relaxed animate-fade-up delay-1">
              {t('landing.tagline')}
              <span className="text-cyan-400 font-medium">{t('landing.taglineHighlight')}</span>
            </p>
            <p className="text-lg text-slate-400 max-w-2xl mx-auto animate-fade-up delay-2">
              {t('landing.description')}
            </p>
          </div>

          <div className="w-full max-w-3xl animate-fade-up delay-3">
            <div className="relative group">
              <div className="absolute -inset-1 bg-gradient-to-r from-cyan-500 to-violet-500 rounded-2xl blur opacity-25 group-hover:opacity-40 transition duration-500" />
              <div className="relative bg-slate-900/80 backdrop-blur-xl rounded-2xl p-2 border border-slate-700/50">
                <SearchBar onSearch={handleSearch} />
              </div>
            </div>
          </div>

          <div className="flex flex-wrap items-center justify-center gap-4 animate-fade-up delay-4">
            <Button
              size="lg"
              className="bg-gradient-to-r from-cyan-500 to-blue-500 hover:from-cyan-400 hover:to-blue-400 text-white font-semibold px-8 py-6 text-lg rounded-xl shadow-lg shadow-cyan-500/25 hover:shadow-cyan-500/40 transition-all duration-300 hover:scale-105"
              onClick={() => navigate({ to: '/search', search: { q: '', sort: 'relevance', page: 0 } })}
            >
              {t('landing.hero.exploreSkills')}
            </Button>
            <Button
              size="lg"
              variant="outline"
              className="border-2 border-slate-600 hover:border-cyan-500 text-slate-200 hover:text-cyan-300 font-semibold px-8 py-6 text-lg rounded-xl backdrop-blur-sm bg-slate-800/30 hover:bg-slate-800/50 transition-all duration-300"
              onClick={() => navigate({ to: '/dashboard/publish' })}
            >
              {t('landing.publishSkill')}
            </Button>
          </div>

          <div className="grid grid-cols-3 gap-8 md:gap-16 pt-12 animate-fade-up delay-5">
            {Object.entries(stats).map(([key, value]) => (
              <div key={key} className="text-center group cursor-default">
                <div className="text-4xl md:text-5xl font-bold text-transparent bg-clip-text bg-gradient-to-br from-cyan-300 to-blue-400 group-hover:from-cyan-200 group-hover:to-blue-300 transition-all duration-300">
                  {value}
                </div>
                <div className="text-sm md:text-base text-slate-400 mt-2 capitalize">
                  {key === 'skills' && t('landing.statsSkills')}
                  {key === 'downloads' && t('landing.statsDownloads')}
                  {key === 'teams' && t('landing.statsTeams')}
                </div>
              </div>
            ))}
          </div>
        </div>

        <div className="py-20 space-y-16">
          <div className="text-center space-y-4 animate-fade-up">
            <h2 className="text-4xl md:text-5xl font-bold text-slate-100">
              {t('landing.whyTitle')} <span className="text-transparent bg-clip-text bg-gradient-to-r from-cyan-400 to-violet-400">SkillHub</span>
            </h2>
            <p className="text-lg text-slate-400 max-w-2xl mx-auto">
              {t('landing.whyDescription')}
            </p>
          </div>

          <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-6">
            {features.map((feature, idx) => (
              <div
                key={idx}
                className="group relative p-6 rounded-2xl bg-slate-800/40 backdrop-blur-sm border border-slate-700/50 hover:border-cyan-500/50 transition-all duration-300 hover:scale-105 animate-fade-up"
                style={{ animationDelay: (idx * 0.1) + 's' }}
              >
                <div className="absolute inset-0 bg-gradient-to-br from-cyan-500/5 to-violet-500/5 rounded-2xl opacity-0 group-hover:opacity-100 transition-opacity duration-300" />
                <div className="relative space-y-3">
                  <div className="text-4xl">{feature.icon}</div>
                  <h3 className="text-xl font-bold text-slate-100 group-hover:text-cyan-300 transition-colors">
                    {feature.title}
                  </h3>
                  <p className="text-slate-400 leading-relaxed">
                    {feature.description}
                  </p>
                </div>
              </div>
            ))}
          </div>
        </div>

      </div>

      <div className="relative z-10 border-t border-slate-800/50 backdrop-blur-sm">
        <div className="container mx-auto px-6 py-8">
          <div className="flex flex-col md:flex-row items-center justify-between gap-4 text-slate-400 text-sm">
            <div>{t('landing.footerLicense')}</div>
            <div className="flex items-center gap-6">
              <a
                href="https://github.com/iflytek/skillhub"
                target="_blank"
                rel="noopener noreferrer"
                className="hover:text-cyan-400 transition-colors"
              >
                {t('landing.footerGithub')}
              </a>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
