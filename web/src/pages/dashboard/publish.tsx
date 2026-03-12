import { useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { UploadZone } from '@/features/publish/upload-zone'
import { Button } from '@/shared/ui/button'
import { Select } from '@/shared/ui/select'
import { Label } from '@/shared/ui/label'
import { Card } from '@/shared/ui/card'
import { useMyNamespaces, usePublishSkill } from '@/shared/hooks/use-skill-queries'

export function PublishPage() {
  const navigate = useNavigate()
  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [namespaceSlug, setNamespaceSlug] = useState<string>('')
  const [visibility, setVisibility] = useState<string>('PUBLIC')

  const { data: namespaces, isLoading: isLoadingNamespaces } = useMyNamespaces()
  const publishMutation = usePublishSkill()

  const handlePublish = async () => {
    if (!selectedFile || !namespaceSlug) {
      alert('请选择命名空间和文件')
      return
    }

    try {
      const result = await publishMutation.mutateAsync({
        namespace: namespaceSlug,
        file: selectedFile,
        visibility,
      })
      alert(`发布成功: ${result.namespace}/${result.slug}@${result.version}`)
      navigate({ to: '/dashboard/skills' })
    } catch (error) {
      alert('发布失败: ' + (error instanceof Error ? error.message : '未知错误'))
    }
  }

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      <div>
        <h1 className="text-3xl font-bold mb-2">发布技能</h1>
        <p className="text-muted-foreground">上传技能包到 SkillHub</p>
      </div>

      <Card className="p-6 space-y-6">
        {/* Namespace Selector */}
        <div className="space-y-2">
          <Label htmlFor="namespace">命名空间</Label>
          {isLoadingNamespaces ? (
            <div className="text-sm text-muted-foreground">加载中...</div>
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
        <div className="space-y-2">
          <Label htmlFor="visibility">可见性</Label>
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
        <div className="space-y-2">
          <Label>技能包文件</Label>
          <UploadZone
            onFileSelect={setSelectedFile}
            disabled={publishMutation.isPending}
          />
          {selectedFile && (
            <div className="text-sm text-muted-foreground">
              已选择: {selectedFile.name} ({(selectedFile.size / 1024).toFixed(1)} KB)
            </div>
          )}
        </div>

        {/* Publish Button */}
        <Button
          className="w-full"
          onClick={handlePublish}
          disabled={!selectedFile || !namespaceSlug || publishMutation.isPending}
        >
          {publishMutation.isPending ? '发布中...' : '确认发布'}
        </Button>
      </Card>
    </div>
  )
}
