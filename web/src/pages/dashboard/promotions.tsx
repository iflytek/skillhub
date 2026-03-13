import { useState } from 'react'
import { useApprovePromotion, usePromotionList, useRejectPromotion } from '@/features/promotion/use-promotion-list'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/shared/ui/tabs'

function PromotionSection({ status }: { status: 'PENDING' | 'APPROVED' | 'REJECTED' }) {
  const { data: items, isLoading } = usePromotionList(status)
  const approveMutation = useApprovePromotion()
  const rejectMutation = useRejectPromotion()
  const [commentById, setCommentById] = useState<Record<number, string>>({})

  if (isLoading) {
    return <div className="h-32 animate-shimmer rounded-xl" />
  }

  if (!items || items.length === 0) {
    return <Card className="p-10 text-center text-muted-foreground">暂无提升申请</Card>
  }

  return (
    <div className="space-y-4">
      {items.map((item) => (
        <Card key={item.id} className="p-5 space-y-4">
          <div className="flex items-start justify-between gap-4">
            <div>
              <div className="font-semibold font-heading">{item.sourceNamespace}/{item.sourceSkillSlug}</div>
              <div className="text-sm text-muted-foreground">
                {item.sourceVersion} {'->'} @{item.targetNamespace}
              </div>
            </div>
            <div className="text-sm text-muted-foreground">{new Date(item.submittedAt).toLocaleString('zh-CN')}</div>
          </div>
          {status === 'PENDING' ? (
            <>
              <Input
                placeholder="审核意见（可选）"
                value={commentById[item.id] ?? ''}
                onChange={(event) => setCommentById((prev) => ({ ...prev, [item.id]: event.target.value }))}
              />
              <div className="flex gap-3">
                <Button
                  onClick={() => approveMutation.mutate({ id: item.id, comment: commentById[item.id] })}
                  disabled={approveMutation.isPending || rejectMutation.isPending}
                >
                  通过
                </Button>
                <Button
                  variant="destructive"
                  onClick={() => rejectMutation.mutate({ id: item.id, comment: commentById[item.id] })}
                  disabled={approveMutation.isPending || rejectMutation.isPending}
                >
                  拒绝
                </Button>
              </div>
            </>
          ) : item.reviewComment ? (
            <p className="text-sm text-muted-foreground">{item.reviewComment}</p>
          ) : null}
        </Card>
      ))}
    </div>
  )
}

export function PromotionsPage() {
  return (
    <div className="space-y-8 animate-fade-up">
      <div>
        <h1 className="text-4xl font-bold font-heading mb-2">提升审核</h1>
        <p className="text-muted-foreground text-lg">审核团队技能提升到全局空间的申请</p>
      </div>
      <Tabs defaultValue="PENDING">
        <TabsList>
          <TabsTrigger value="PENDING">待审核</TabsTrigger>
          <TabsTrigger value="APPROVED">已通过</TabsTrigger>
          <TabsTrigger value="REJECTED">已拒绝</TabsTrigger>
        </TabsList>
        <TabsContent value="PENDING" className="mt-6"><PromotionSection status="PENDING" /></TabsContent>
        <TabsContent value="APPROVED" className="mt-6"><PromotionSection status="APPROVED" /></TabsContent>
        <TabsContent value="REJECTED" className="mt-6"><PromotionSection status="REJECTED" /></TabsContent>
      </Tabs>
    </div>
  )
}
