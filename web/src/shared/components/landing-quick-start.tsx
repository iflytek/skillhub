import { useMemo, useState } from 'react'
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
  title: string
  description: string
}

const ACCESS_MODES: AccessModeOption[] = [
  { id: 'agent', number: '01', title: 'Agent 自动接入', description: '配置一次 Registry，按需发现技能' },
  { id: 'cli', number: '02', title: 'CLI 命令行', description: '搜索、获取和发布技能' },
  { id: 'web', number: '03', title: 'Web 界面', description: '可视化浏览、阅读并下载技能' },
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
  const [activeView, setActiveView] = useState<AgentView>('registry')
  const [copied, copy] = useCopyToClipboard()
  const registryUrl = useMemo(getRegistryUrl, [])
  const instruction = `阅读 ${registryUrl}/registry/skill.md，并按照说明完成 SkillHub Skills Registry 的配置`

  return (
    <div className="min-h-[340px]">
      <div className="flex flex-col gap-4 border-b border-border/70 pb-5 sm:flex-row sm:items-center sm:justify-between">
        <div className="inline-flex w-fit items-center gap-1 border-b border-border/70" role="tablist" aria-label="Agent 接入演示">
          {([
            ['registry', 'Registry 配置'],
            ['discovery', '隐式发现'],
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
          Agent 实时
        </span>
      </div>

      {activeView === 'registry' ? (
        <div className="animate-fade-up py-9">
          <p className="mb-5 text-xs text-muted-foreground">复制以下指令，发送给 Agent 即可完成配置</p>
          <div className="flex flex-col gap-5 border-y border-border/70 py-5 sm:flex-row sm:items-start">
            <span className="mt-1 hidden h-12 w-1 flex-shrink-0 bg-foreground sm:block" aria-hidden />
            <p className="min-w-0 flex-1 text-sm leading-7 text-foreground">
              阅读 <span className="break-all text-blue-600 underline decoration-blue-300 underline-offset-4">{registryUrl}/registry/skill.md</span>，并按照说明完成 SkillHub Skills Registry 的配置
            </p>
            <button
              type="button"
              onClick={() => {
                void copy(instruction).catch(() => {
                  toast.error('复制失败', '请手动选择并复制这条 Registry 配置指令。')
                })
              }}
              className="inline-flex w-fit flex-shrink-0 items-center gap-2 rounded-lg bg-foreground px-4 py-2.5 text-xs font-semibold text-background shadow-sm transition-[transform,opacity,box-shadow] duration-150 hover:-translate-y-px hover:opacity-90 hover:shadow-md active:translate-y-0 active:scale-[0.98] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 motion-reduce:transform-none"
            >
              {copied ? <Check className="h-3.5 w-3.5" /> : <Copy className="h-3.5 w-3.5" />}
              {copied ? '已复制' : '复制指令'}
            </button>
          </div>
          <div className="mt-6 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
            {['读取文档', '配置 Registry', '按需发现技能'].map((step, index) => (
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
            <span className="border-b border-foreground pb-2 text-sm text-foreground">帮我查下合肥今天的天气</span>
          </div>
          <div className="relative space-y-0 pl-7 before:absolute before:bottom-3 before:left-[5px] before:top-3 before:w-px before:bg-border">
            {[
              ['识别任务意图', '需要实时天气与出行建议'],
              ['检索 SkillHub Registry', '匹配 @global/weather · v1.3.0'],
              ['读取技能说明', '确认输入格式和调用方式'],
            ].map(([title, description], index) => (
              <div key={title} className={`relative grid grid-cols-1 gap-1 border-b border-border/60 py-3.5 sm:grid-cols-[9rem_1fr] animate-fade-up delay-${index + 1}`}>
                <span className="absolute -left-[26px] top-[20px] h-2.5 w-2.5 rounded-full border-2 border-background bg-muted-foreground ring-1 ring-border" />
                <strong className="text-xs font-semibold text-foreground">{title}</strong>
                <span className="text-xs text-muted-foreground">{description}</span>
              </div>
            ))}
          </div>
          <p className="mt-6 border-t-2 border-foreground pt-5 text-sm leading-7 text-foreground">
            <strong className="text-emerald-700 dark:text-emerald-400">合肥今天多云，26°C</strong>，下午可能有短时阵雨，外出建议携带雨具。
          </p>
        </div>
      )}
    </div>
  )
}

function CliAccessPanel() {
  const [copiedIdx, setCopiedIdx] = useState(-1)

  const handleCopy = (text: string, idx: number) => {
    void copyToClipboard(text)
      .then(() => {
        setCopiedIdx(idx)
        window.setTimeout(() => setCopiedIdx((prev) => (prev === idx ? -1 : prev)), 2000)
      })
      .catch(() => {
        toast.error('复制失败', '请手动选择并复制这条 CLI 命令。')
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
              <span className="text-neutral-800 dark:text-neutral-200">npx -y @astron-team/skillhub@0.1.11 --version</span>
              <button
                type="button"
                onClick={() => handleCopy('npx -y @astron-team/skillhub@0.1.11 --version', 0)}
                className="ml-2 inline-flex items-center gap-0.5 rounded px-1.5 py-0.5 text-[10px] text-muted-foreground align-middle transition-[color,background-color,transform] hover:bg-neutral-100 hover:text-foreground active:scale-95 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring dark:hover:bg-neutral-800"
              >
                {copiedIdx === 0 ? <Check className="h-3 w-3" /> : <Copy className="h-3 w-3" />}
                {copiedIdx === 0 ? '已复制' : '复制'}
              </button>
            </div>
          </div>
          <div className="pl-5 text-muted-foreground">SkillHub CLI 0.1.11</div>
        </div>

        {/* Search */}
        <div className="mb-3">
          <div className="flex items-start gap-2">
            <span className="select-none font-bold text-emerald-600 dark:text-emerald-400">$</span>
            <div className="flex-1">
              <span className="text-neutral-800 dark:text-neutral-200">npx -y @astron-team/skillhub@0.1.11 search weather \</span>
              <button
                type="button"
                onClick={() => handleCopy('npx -y @astron-team/skillhub@0.1.11 search weather --registry https://skill.xfyun.cn --limit 5', 1)}
                className="ml-2 inline-flex items-center gap-0.5 rounded px-1.5 py-0.5 text-[10px] text-muted-foreground align-middle transition-[color,background-color,transform] hover:bg-neutral-100 hover:text-foreground active:scale-95 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring dark:hover:bg-neutral-800"
              >
                {copiedIdx === 1 ? <Check className="h-3 w-3" /> : <Copy className="h-3 w-3" />}
                {copiedIdx === 1 ? '已复制' : '复制'}
              </button>
            </div>
          </div>
          <div className="pl-5 text-neutral-600 dark:text-neutral-400">
            <span className="text-muted-foreground">{'  --registry'} </span>
            <span className="text-blue-600 dark:text-blue-400">https://skill.xfyun.cn</span> \
          </div>
          <div className="pl-5 text-neutral-600 dark:text-neutral-400">
            <span className="text-muted-foreground">{'  --limit'} </span>
            <span className="text-neutral-800 dark:text-neutral-200">5</span>
          </div>
          <div className="mt-1 space-y-0.5 pl-5">
            <div className="text-[11px] text-muted-foreground">Skills found:</div>
            <div className="flex gap-4">
              <span className="text-blue-600 dark:text-blue-400">@global/weather</span>
              <span className="text-muted-foreground">v1.3.0</span>
              <span className="text-neutral-600 dark:text-neutral-400">查询全球城市天气</span>
            </div>
            <div className="flex gap-4">
              <span className="text-blue-600 dark:text-blue-400">@global/forecast</span>
              <span className="text-muted-foreground">v2.1.0</span>
              <span className="text-neutral-600 dark:text-neutral-400">7 天天气预报</span>
            </div>
          </div>
        </div>

        {/* Install */}
        <div className="mb-3">
          <div className="flex items-start gap-2">
            <span className="select-none font-bold text-emerald-600 dark:text-emerald-400">$</span>
            <div className="flex-1">
              <span className="text-neutral-800 dark:text-neutral-200">npx -y @astron-team/skillhub@0.1.11 install @global/weather \</span>
              <button
                type="button"
                onClick={() => handleCopy('npx -y @astron-team/skillhub@0.1.11 install @global/weather --dir ./skills --registry https://skill.xfyun.cn', 2)}
                className="ml-2 inline-flex items-center gap-0.5 rounded px-1.5 py-0.5 text-[10px] text-muted-foreground align-middle transition-[color,background-color,transform] hover:bg-neutral-100 hover:text-foreground active:scale-95 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring dark:hover:bg-neutral-800"
              >
                {copiedIdx === 2 ? <Check className="h-3 w-3" /> : <Copy className="h-3 w-3" />}
                {copiedIdx === 2 ? '已复制' : '复制'}
              </button>
            </div>
          </div>
          <div className="pl-5 text-neutral-600 dark:text-neutral-400">
            <span className="text-muted-foreground">{'  --dir'} </span>
            <span className="text-neutral-800 dark:text-neutral-200">./skills</span> \
          </div>
          <div className="pl-5 text-neutral-600 dark:text-neutral-400">
            <span className="text-muted-foreground">{'  --registry'} </span>
            <span className="text-blue-600 dark:text-blue-400">https://skill.xfyun.cn</span>
          </div>
          <div className="mt-1 flex items-center gap-1.5 pl-5 text-[11px] text-emerald-600 dark:text-emerald-400">
            <Check className="h-3 w-3" strokeWidth={2.5} />
            <span>@global/weather installed to ./skills/global/weather</span>
          </div>
        </div>

        {/* Cursor */}
        <div className="flex items-center gap-2">
          <span className="select-none font-bold text-emerald-600 dark:text-emerald-400">$</span>
          <span className="inline-block h-3.5 w-2 animate-pulse bg-neutral-700 dark:bg-neutral-400" />
        </div>
      </div>

      {/* Bottom status bar */}
      <div className="flex items-center justify-between border-t border-border/60 bg-white px-4 py-2.5 text-[11px] dark:bg-neutral-900">
        <div className="flex items-center gap-3 text-muted-foreground">
          <span className="flex items-center gap-1">
            <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
            connected
          </span>
          <span className="text-border">·</span>
          <span className="font-mono">registry: skill.xfyun.cn</span>
        </div>
        <a href="https://github.com/iflytek/skillhub/tree/main/cli" target="_blank" rel="noreferrer" className="group flex items-center gap-1 font-medium text-blue-600 transition-colors hover:text-blue-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring dark:text-blue-400 dark:hover:text-blue-300">
          CLI 文档
          <ChevronRight className="h-3 w-3 transition-transform group-hover:translate-x-0.5 motion-reduce:transform-none" />
        </a>
      </div>
    </div>
  )
}

function WebAccessPanel() {
  return (
    <div className="min-h-[340px]">
      <div className="grid gap-6 md:grid-cols-[11rem_minmax(0,1fr)]">
        <div className="border-b border-border/70 pb-5 md:border-b-0 md:border-r md:pb-0 md:pr-5">
          <div className="mb-4 flex items-center justify-between">
            <strong className="text-xs font-semibold text-foreground">技能市场</strong>
            <span className="text-[10px] text-muted-foreground">公开技能</span>
          </div>
          <div className="mb-3 flex items-center gap-2 border-b border-border/70 pb-2.5 text-[11px] text-muted-foreground">
            <Search className="h-3.5 w-3.5" />
            搜索技能
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
                <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />公开 · 正常
              </span>
              <h3 className="mt-2 text-xl font-semibold tracking-tight text-foreground">weather <span className="font-mono text-xs font-normal text-muted-foreground">@global</span></h3>
              <p className="mt-1 text-xs text-muted-foreground">查询实时天气、未来预报和出行信息</p>
            </div>
            <a href={buildApiUrl(`${WEB_API_PREFIX}/skills/global/weather/download`)} className="inline-flex w-fit items-center gap-2 rounded-lg bg-foreground px-4 py-2.5 text-xs font-semibold text-background shadow-sm transition-[transform,opacity,box-shadow] duration-150 hover:-translate-y-px hover:opacity-90 hover:shadow-md active:translate-y-0 active:scale-[0.98] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 motion-reduce:transform-none">
              <Download className="h-3.5 w-3.5" />下载 ZIP
            </a>
          </div>

          <div className="mt-6 grid gap-5 border-t border-border/70 pt-5 sm:grid-cols-[minmax(0,1fr)_8rem]">
            <div>
              <div className="mb-3 flex items-center gap-2 text-xs font-semibold text-foreground">
                <FileText className="h-3.5 w-3.5" />
                先阅读，再使用
              </div>
              <p className="text-[11px] leading-6 text-muted-foreground">
                阅读 SKILL.md 中的用途、输入要求和执行方式，确认适合当前任务后再获取完整技能包。
              </p>
            </div>
            <dl className="space-y-3 text-[10px]">
              <div className="flex justify-between border-b border-border/60 pb-2"><dt className="text-muted-foreground">版本</dt><dd className="font-mono text-foreground">v1.3.0</dd></div>
              <div className="flex justify-between border-b border-border/60 pb-2"><dt className="text-muted-foreground">文件</dt><dd className="text-foreground">3</dd></div>
              <div className="flex justify-between"><dt className="text-muted-foreground">格式</dt><dd className="text-foreground">ZIP</dd></div>
            </dl>
          </div>

          <div className="mt-6 flex flex-wrap gap-x-5 gap-y-2 text-[11px] text-muted-foreground">
            <span>浏览与搜索</span><span>内容预览</span><span>文件与版本</span><span>收藏和订阅</span>
          </div>
        </div>
      </div>
    </div>
  )
}

export function LandingQuickStartSection() {
  const [activeMode, setActiveMode] = useState<AccessMode>('agent')

  return (
    <section id="quickstart" className="relative z-10 w-full overflow-hidden bg-secondary/70 px-6 py-16 md:py-20">
      <div className="absolute inset-0 bg-dots opacity-40" aria-hidden />
      <div className="relative mx-auto max-w-6xl">
        <div className="mb-12 max-w-3xl">
          <p className="mb-3 text-[11px] font-semibold uppercase tracking-[0.16em] text-muted-foreground">Quick Start</p>
          <h2 className="mb-4 text-3xl font-medium tracking-tight text-foreground md:text-4xl">选择你的接入方式</h2>
          <p className="max-w-2xl text-lg leading-relaxed text-muted-foreground">三种路径，获得同一套技能能力。选择最适合你工作流的入口。</p>
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
                    <strong className={`block text-sm font-semibold transition-colors ${active ? 'text-foreground' : 'text-muted-foreground group-hover:text-foreground'}`}>{mode.title}</strong>
                    <span className="mt-1 block text-[11px] text-muted-foreground">{mode.description}</span>
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
