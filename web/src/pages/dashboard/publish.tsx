import { useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { UploadZone } from '@/features/publish/upload-zone'
import { Button } from '@/shared/ui/button'
import { Select } from '@/shared/ui/select'
import { Label } from '@/shared/ui/label'
import { Card } from '@/shared/ui/card'
import { useMyNamespaces, usePublishSkill } from '@/shared/hooks/use-skill-queries'
import { toast } from '@/shared/lib/toast'

export function PublishPage() {
  const navigate = useNavigate()
  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [namespaceSlug, setNamespaceSlug] = useState<string>('')
  const [visibility, setVisibility] = useState<string>('PUBLIC')

  const { data: namespaces, isLoading: isLoadingNamespaces } = useMyNamespaces()
  const publishMutation = usePublishSkill()

  const handlePublish = async () => {
    if (!selectedFile || !namespaceSlug) {
      toast.error('请选择命名空间和文件')
      return
    }

    try {
      const result = await publishMutation.mutateAsync({
        namespace: namespaceSlug,
        file: selectedFile,
        visibility,
      })
      toast.success('发布成功', `${result.namespace}/${result.slug}@${result.version} 已提交审核，等待管理员批准后即可使用`)
      navigate({ to: '/dashboard/skills' })
    } catch (error) {
      toast.error('发布失败', error instanceof Error ? error.message : '未知错误')
    }
  }

  return (
    <div className="max-w-2xl mx-auto space-y-8 animate-fade-up">
      <div>
        <h1 className="text-4xl font-bold font-heading mb-2">发布技能</h1>
        <p className="text-muted-foreground text-lg">上传技能包到 SkillHub</p>
      </div>

      {/* 审核提示 */}
      <Card className="p-4 bg-blue-500/5 border-blue-500/20">
        <div className="flex items-start gap-3">
          <svg className="w-5 h-5 text-blue-500 mt-0.5 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          <div className="flex-1">
            <h3 className="text-sm font-semibold text-foreground mb-1">发布审核说明</h3>
            <p className="text-sm text-muted-foreground">
              技能包提交后需要经过管理员审核才能正式发布。审核通过后，您的技能将对所有用户可见。
            </p>
          </div>
        </div>
      </Card>

      <Card className="p-8 space-y-8">
        {/* Namespace Selector */}
        <div className="space-y-3">
          <Label htmlFor="namespace" className="text-sm font-semibold font-heading">命名空间</Label>
          {isLoadingNamespaces ? (
            <div className="h-11 animate-shimmer rounded-lg" />
          ) : (
            <Select
              id="namespace"
              value={namespaceSlug}
              onChange={(e) => setNamespaceSlug(e.target.value)}
            >
              <option value="">选择命名空间</option>
              {namespaces?.map((ns) => (
                <option key={ns.id} value={ns.slug}>
                  {ns.displayName} (@{ns.slug})
                </option>
              ))}
            </Select>
          )}
        </div>

        {/* Visibility Selector */}
        <div className="space-y-3">
          <Label htmlFor="visibility" className="text-sm font-semibold font-heading">可见性</Label>
          <Select
            id="visibility"
            value={visibility}
            onChange={(e) => setVisibility(e.target.value)}
          >
            <option value="PUBLIC">公开</option>
            <option value="NAMESPACE_ONLY">仅命名空间</option>
            <option value="PRIVATE">私有</option>
          </Select>
        </div>

        {/* Upload Zone */}
        <div className="space-y-3">
          <Label className="text-sm font-semibold font-heading">技能包文件</Label>
          <UploadZone
            onFileSelect={setSelectedFile}
            disabled={publishMutation.isPending}
          />
          {selectedFile && (
            <div className="text-sm text-muted-foreground flex items-center gap-2">
              <svg className="w-4 h-4 text-emerald-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
              </svg>
              {selectedFile.name} ({(selectedFile.size / 1024).toFixed(1)} KB)
            </div>
          )}
        </div>

        {/* Publish Button */}
        <Button
          className="w-full"
          size="lg"
          onClick={handlePublish}
          disabled={!selectedFile || !namespaceSlug || publishMutation.isPending}
        >
          {publishMutation.isPending ? '发布中...' : '确认发布'}     </Button>
      </Card>
    </div>
  )
}
