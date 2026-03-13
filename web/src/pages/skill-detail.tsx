import { useParams, useNavigate } from '@tanstack/react-router'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { MarkdownRenderer } from '@/features/skill/markdown-renderer'
import { FileTree } from '@/features/skill/file-tree'
import { InstallCommand } from '@/features/skill/install-command'
import { RatingInput } from '@/features/social/rating-input'
import { StarButton } from '@/features/social/star-button'
import { useAuth } from '@/features/auth/use-auth'
import { adminApi } from '@/api/client'
import { NamespaceBadge } from '@/shared/components/namespace-badge'
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/shared/ui/tabs'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import {
  useSkillDetail,
  useSkillVersions,
  useSkillFiles,
  useSkillReadme,
} from '@/shared/hooks/use-skill-queries'

export function SkillDetailPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { namespace, slug } = useParams({ from: '/@$namespace/$slug' })
  const { user, hasRole } = useAuth()

  const { data: skill, isLoading: isLoadingSkill } = useSkillDetail(namespace, slug)
  const { data: versions } = useSkillVersions(namespace, slug)
  const latestVersion = versions?.[0]
  const { data: files } = useSkillFiles(namespace, slug, latestVersion?.version)
  const { data: readme } = useSkillReadme(namespace, slug, latestVersion?.version)
  const governanceVisible = hasRole('SKILL_ADMIN') || hasRole('SUPER_ADMIN')

  const refreshSkill = () => {
    queryClient.invalidateQueries({ queryKey: ['skills', namespace, slug] })
    queryClient.invalidateQueries({ queryKey: ['skills', namespace, slug, 'versions'] })
    queryClient.invalidateQueries({ queryKey: ['skills'] })
  }

  const hideMutation = useMutation({
    mutationFn: () => adminApi.hideSkill(skill!.id),
    onSuccess: refreshSkill,
  })

  const unhideMutation = useMutation({
    mutationFn: () => adminApi.unhideSkill(skill!.id),
    onSuccess: refreshSkill,
  })

  const yankMutation = useMutation({
    mutationFn: () => adminApi.yankVersion(latestVersion!.id),
    onSuccess: refreshSkill,
  })

  const requireLogin = () => {
    navigate({ to: '/login' })
  }

  if (isLoadingSkill) {
    return (
      <div className="space-y-6 animate-fade-up">
        <div className="h-10 w-64 animate-shimmer rounded-lg" />
        <div className="h-5 w-96 animate-shimmer rounded-md" />
        <div className="h-64 animate-shimmer rounded-xl" />
      </div>
    )
  }

  if (!skill) {
    return (
      <div className="text-center py-20 animate-fade-up">
        <h2 className="text-2xl font-bold font-heading mb-2">技能不存在</h2>
        <p className="text-muted-foreground">该技能可能已被删除或从未存在</p>
      </div>
    )
  }

  return (
    <div className="grid grid-cols-1 lg:grid-cols-3 gap-8 animate-fade-up">
      {/* Main Content */}
      <div className="lg:col-span-2 space-y-8">
        <div className="space-y-3">
          <div className="flex items-center gap-3 mb-1">
            <NamespaceBadge type="GLOBAL" name={`@${namespace}`} />
          </div>
          <h1 className="text-4xl font-bold font-heading text-foreground">{skill.displayName}</h1>
          {skill.summary && (
            <p className="text-lg text-muted-foreground leading-relaxed">{skill.summary}</p>
          )}
        </div>

        <Tabs defaultValue="readme">
          <TabsList>
            <TabsTrigger value="readme">README</TabsTrigger>
            <TabsTrigger value="files">文件</TabsTrigger>
            <TabsTrigger value="versions">版本</TabsTrigger>
          </TabsList>

          <TabsContent value="readme" className="mt-6">
            {readme ? (
              <Card className="p-8">
                <MarkdownRenderer content={readme} />
              </Card>
            ) : (
              <Card className="p-8 text-muted-foreground text-center">
                暂无 README
              </Card>
            )}
          </TabsContent>

          <TabsContent value="files" className="mt-6">
            {files && files.length > 0 ? (
              <FileTree files={files} />
            ) : (
              <Card className="p-8 text-muted-foreground text-center">
                暂无文件
              </Card>
            )}
          </TabsContent>

          <TabsContent value="versions" className="mt-6">
            <Card className="p-6">
              {versions && versions.length > 0 ? (
                <div className="space-y-0 divide-y divide-border/40">
                  {versions.map((version) => (
                    <div key={version.id} className="py-5 first:pt-0 last:pb-0">
                      <div className="flex items-center justify-between mb-2">
                        <span className="font-semibold font-heading text-foreground flex items-center gap-2">
                          <span className="px-2.5 py-0.5 rounded-full bg-primary/10 text-primary text-sm font-mono">
                            v{version.version}
                          </span>
                        </span>
                        <span className="text-sm text-muted-foreground">
                          {new Date(version.publishedAt).toLocaleDateString('zh-CN')}
                        </span>
                      </div>
                      {version.changelog && (
                        <p className="text-sm text-muted-foreground leading-relaxed">{version.changelog}</p>
                      )}
                      <div className="text-xs text-muted-foreground mt-2 flex items-center gap-3">
                        <span>{version.fileCount} 个文件</span>
                        <span className="w-1 h-1 rounded-full bg-muted-foreground/40" />
                        <span>{(version.totalSize / 1024).toFixed(1)} KB</span>
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="text-muted-foreground text-center py-8">暂无版本</div>
              )}
            </Card>
          </TabsContent>
        </Tabs>
      </div>

      {/* Sidebar */}
      <div className="space-y-5">
        <Card className="p-5 space-y-5">
          <div className="flex items-center justify-between">
            <div className="text-sm text-muted-foreground">版本</div>
            <div className="font-semibold font-mono text-foreground">
              {skill.latestVersion ? `v${skill.latestVersion}` : '—'}
            </div>
          </div>

          <div className="h-px bg-border/40" />

          <div className="flex items-center justify-between">
            <div className="text-sm text-muted-foreground">下载量</div>
            <div className="font-semibold text-foreground">{skill.downloadCount.toLocaleString()}</div>
          </div>

          <div className="h-px bg-border/40" />

          <div className="flex items-center justify-between">
            <div className="text-sm text-muted-foreground">评分</div>
            <div className="font-semibold text-foreground">
              {skill.ratingCount > 0 && skill.ratingAvg !== undefined ? `${skill.ratingAvg.toFixed(1)} / 5` : '暂无'}
            </div>
          </div>

          <div className="h-px bg-border/40" />

          <div className="flex items-center justify-between">
            <div className="text-sm text-muted-foreground">命名空间</div>
            <NamespaceBadge type="GLOBAL" name={`@${namespace}`} />
          </div>

          <div className="h-px bg-border/40" />

          <div className="space-y-3">
            <StarButton skillId={skill.id} starCount={skill.starCount} onRequireLogin={requireLogin} />
            <RatingInput skillId={skill.id} onRequireLogin={requireLogin} />
            {!user && (
              <p className="text-xs text-muted-foreground">登录后可以收藏和评分</p>
            )}
          </div>
        </Card>

        {skill.latestVersion && (
          <Card className="p-5 space-y-4">
            <div className="text-sm font-semibold font-heading text-foreground">安装</div>
            <InstallCommand
              namespace={namespace}
              slug={slug}
              version={skill.latestVersion}
            />
          </Card>
        )}

        <Button className="w-full" variant="outline" size="lg">
          <svg className="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M9 19l3 3m0 0l3-3m-3 3V10" />
          </svg>
          下载
        </Button>

        {governanceVisible && (
          <Card className="p-5 space-y-3">
            <div className="text-sm font-semibold font-heading text-foreground">治理操作</div>
            <div className="flex flex-col gap-3">
              {!skill.hidden ? (
                <Button variant="outline" onClick={() => hideMutation.mutate()} disabled={hideMutation.isPending}>
                  {hideMutation.isPending ? '处理中...' : '隐藏技能'}
                </Button>
              ) : (
                <Button variant="outline" onClick={() => unhideMutation.mutate()} disabled={unhideMutation.isPending}>
                  {unhideMutation.isPending ? '处理中...' : '恢复技能'}
                </Button>
              )}
              {latestVersion && (
                <Button variant="destructive" onClick={() => yankMutation.mutate()} disabled={yankMutation.isPending}>
                  {yankMutation.isPending ? '处理中...' : '撤回当前版本'}
                </Button>
              )}
            </div>
          </Card>
        )}
      </div>
    </div>
  )
}
