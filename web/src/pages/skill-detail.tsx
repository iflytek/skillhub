import { useParams } from '@tanstack/react-router'
import { MarkdownRenderer } from '@/features/skill/markdown-renderer'
import { FileTree } from '@/features/skill/file-tree'
import { InstallCommand } from '@/features/skill/install-command'
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
  const { namespace, slug } = useParams({ from: '/@$namespace/$slug' })

  const { data: skill, isLoading: isLoadingSkill } = useSkillDetail(namespace, slug)
  const { data: versions } = useSkillVersions(namespace, slug)
  const latestVersion = versions?.[0]
  const { data: files } = useSkillFiles(namespace, slug, latestVersion?.version)
  const { data: readme } = useSkillReadme(namespace, slug, latestVersion?.version)

  if (isLoadingSkill) {
    return <div className="animate-pulse">加载中...</div>
  }

  if (!skill) {
    return <div>技能不存在</div>
  }

  return (
    <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
      {/* Main Content */}
      <div className="lg:col-span-2 space-y-6">
        <div>
          <h1 className="text-3xl font-bold mb-2">{skill.displayName}</h1>
          {skill.summary && (
            <p className="text-muted-foreground">{skill.summary}</p>
          )}
        </div>

        <Tabs defaultValue="readme">
          <TabsList>
            <TabsTrigger value="readme">README</TabsTrigger>
            <TabsTrigger value="files">文件</TabsTrigger>
            <TabsTrigger value="versions">版本</TabsTrigger>
          </TabsList>

          <TabsContent value="readme" className="mt-4">
            {readme ? (
              <Card className="p-6">
                <MarkdownRenderer content={readme} />
              </Card>
            ) : (
              <Card className="p-6 text-muted-foreground">
                暂无 README
              </Card>
            )}
          </TabsContent>

          <TabsContent value="files" className="mt-4">
            {files && files.length > 0 ? (
              <FileTree files={files} />
            ) : (
              <Card className="p-6 text-muted-foreground">
                暂无文件
              </Card>
            )}
          </TabsContent>

          <TabsContent value="versions" className="mt-4">
            <Card className="p-6">
              {versions && versions.length > 0 ? (
                <div className="space-y-4">
                  {versions.map((version) => (
                    <div key={version.id} className="border-b pb-4 last:border-b-0">
                      <div className="flex items-center justify-between mb-2">
                        <span className="font-semibold">v{version.version}</span>
                        <span className="text-sm text-muted-foreground">
                          {new Date(version.publishedAt).toLocaleDateString('zh-CN')}
                        </span>
                      </div>
                      {version.changelog && (
                        <p className="text-sm text-muted-foreground">{version.changelog}</p>
                      )}
                      <div className="text-xs text-muted-foreground mt-2">
                        {version.fileCount} 个文件 · {(version.totalSize / 1024).toFixed(1)} KB
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="text-muted-foreground">暂无版本</div>
              )}
            </Card>
          </TabsContent>
        </Tabs>
      </div>

      {/* Sidebar */}
      <div className="space-y-4">
        <Card className="p-4 space-y-4">
          <div>
            <div className="text-sm text-muted-foreground mb-1">版本</div>
            <div className="font-semibold">
              {skill.latestVersion ? `v${skill.latestVersion}` : '暂无版本'}
            </div>
          </div>

          <div>
            <div className="text-sm text-muted-foreground mb-1">下载量</div>
            <div className="font-semibold">{skill.downloadCount}</div>
          </div>

          <div>
            <div className="text-sm text-muted-foreground mb-1">命名空间</div>
            <NamespaceBadge type="GLOBAL" name={namespace} />
          </div>
        </Card>

        {skill.latestVersion && (
          <Card className="p-4 space-y-4">
            <div className="text-sm font-medium">安装</div>
            <InstallCommand
              namespace={namespace}
              slug={slug}
              version={skill.latestVersion}
            />
          </Card>
        )}

        <Button className="w-full" variant="outline">
          下载
        </Button>
      </div>
    </div>
  )
}
