import { useNavigate } from '@tanstack/react-router'
import { SearchBar } from '@/features/search/search-bar'
import { Button } from '@/shared/ui/button'
import { useEffect, useRef, useState } from 'react'

export function LandingPage() {
  const navigate = useNavigate()
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
    navigate({ to: '/search', search: { q: query, sort: 'relevance', page: 1 } })
  }

  const features = [
    {
      icon: '🔒',
      title: '私有部署',
      description: '完全自托管，数据主权在您手中。一键部署到您的基础设施，防火墙后安全运行。',
    },
    {
      icon: '📦',
      title: '版本管理',
      description: '语义化版本控制，自定义标签（beta、stable），自动追踪 latest 版本。',
    },
    {
      icon: '🔍',
      title: '智能搜索',
      description: '全文搜索，支持命名空间、下载量、评分、时间等多维度筛选。',
    },
    {
      icon: '👥',
      title: '团队协作',
      description: '命名空间管理，角色权限控制（Owner/Admin/Member），发布策略配置。',
    },
    {
      icon: '✅',
      title: '审核治理',
      description: '团队内审核，平台级审批，全流程审计日志，满足合规要求。',
    },
    {
      icon: '⚡',
      title: 'CLI 优先',
      description: '原生 REST API，兼容现有 ClawHub CLI 工具，无需客户端改动。',
    },
  ]

  return (
    <div className="relative min-h-screen overflow-hidden bg-gradient-to-br from-slate-950 via-indigo-950 to-slate-900">
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
            <span className="text-sm text-cyan-300 font-medium">企业级技能注册中心</span>
          </div>

          <div className="space-y-6 max-w-5xl">
            <h1 className="text-7xl md:text-8xl lg:text-9xl font-black tracking-tight animate-fade-up">
              <span className="block text-transparent bg-clip-text bg-gradient-to-r from-cyan-300 via-blue-400 to-violet-400">
                SkillHub
              </span>
            </h1>
            <p className="text-2xl md:text-3xl text-slate-300 font-light leading-relaxed animate-fade-up delay-1">
              发布、发现、管理
              <span className="text-cyan-400 font-medium"> Agent 技能包</span>
            </p>
            <p className="text-lg text-slate-400 max-w-2xl mx-auto animate-fade-up delay-2">
              自托管的私有技能注册平台，为团队提供安全、高效的技能共享与协作空间
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
              onClick={() => navigate({ to: '/search', search: { q: '', sort: 'relevance', page: 1 } })}
            >
              探索技能
            </Button>
            <Button
              size="lg"
              variant="outline"
              className="border-2 border-slate-600 hover:border-cyan-500 text-slate-200 hover:text-cyan-300 font-semibold px-8 py-6 text-lg rounded-xl backdrop-blur-sm bg-slate-800/30 hover:bg-slate-800/50 transition-all duration-300"
              onClick={() => navigate({ to: '/dashboard/publish' })}
            >
              发布技能
            </Button>
          </div>

          <div className="grid grid-cols-3 gap-8 md:gap-16 pt-12 animate-fade-up delay-5">
            {Object.entries(stats).map(([key, value]) => (
              <div key={key} className="text-center group cursor-default">
                <div className="text-4xl md:text-5xl font-bold text-transparent bg-clip-text bg-gradient-to-br from-cyan-300 to-blue-400 group-hover:from-cyan-200 group-hover:to-blue-300 transition-all duration-300">
                  {value}
                </div>
                <div className="text-sm md:text-base text-slate-400 mt-2 capitalize">
                  {key === 'skills' && '技能包'}
                  {key === 'downloads' && '下载量'}
                  {key === 'teams' && '团队'}
                </div>
              </div>
            ))}
          </div>
        </div>

        <div className="py-20 space-y-16">
          <div className="text-center space-y-4 animate-fade-up">
            <h2 className="text-4xl md:text-5xl font-bold text-slate-100">
              为什么选择 <span className="text-transparent bg-clip-text bg-gradient-to-r from-cyan-400 to-violet-400">SkillHub</span>
            </h2>
            <p className="text-lg text-slate-400 max-w-2xl mx-auto">
              专为企业打造的技能管理平台，提供完整的发布、发现、治理能力
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

        <div className="py-20">
          <div className="relative max-w-4xl mx-auto p-12 rounded-3xl bg-gradient-to-br from-cyan-500/10 to-violet-500/10 backdrop-blur-sm border border-cyan-500/30 text-center space-y-8 animate-fade-up">
            <div className="absolute inset-0 bg-gradient-to-br from-cyan-500/5 to-violet-500/5 rounded-3xl blur-xl" />
            <div className="relative space-y-6">
              <h2 className="text-4xl md:text-5xl font-bold text-slate-100">
                准备好开始了吗？
              </h2>
              <p className="text-lg text-slate-300 max-w-2xl mx-auto">
                一条命令即可启动本地开发环境，体验完整的技能管理流程
              </p>
              <div className="inline-block p-4 rounded-xl bg-slate-900/80 backdrop-blur-sm border border-slate-700/50">
                <code className="text-cyan-400 text-lg font-mono">make dev-all</code>
              </div>
              <div className="flex flex-wrap items-center justify-center gap-4 pt-4">
                <Button
                  size="lg"
                  className="bg-gradient-to-r from-cyan-500 to-blue-500 hover:from-cyan-400 hover:to-blue-400 text-white font-semibold px-8 py-6 text-lg rounded-xl shadow-lg shadow-cyan-500/25 hover:shadow-cyan-500/40 transition-all duration-300 hover:scale-105"
                  onClick={() => navigate({ to: '/search', search: { q: '', sort: 'relevance', page: 1 } })}
                >
                  立即体验
                </Button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div className="relative z-10 border-t border-slate-800/50 backdrop-blur-sm">
        <div className="container mx-auto px-6 py-8">
          <div className="flex flex-col md:flex-row items-center justify-between gap-4 text-slate-400 text-sm">
            <div>© 2026 SkillHub. MIT License.</div>
            <div className="flex items-center gap-6">
              <a href="#" className="hover:text-cyan-400 transition-colors">文档</a>
              <a href="#" className="hover:text-cyan-400 transition-colors">GitHub</a>
              <a href="#" className="hover:text-cyan-400 transition-colors">社区</a>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
