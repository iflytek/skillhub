import { useMemo, useState } from 'react'
import {
  useApprovePromotionCampaign,
  usePromotionCampaigns,
  useRejectPromotionCampaign,
} from '@/features/promotion-campaign/hooks'
import type { CampaignStatus } from '@/features/promotion-campaign/api'

const STATUS_TABS: { value: CampaignStatus; label: string }[] = [
  { value: 'PENDING_REVIEW', label: '待审核' },
  { value: 'SCHEDULED', label: '已排期' },
  { value: 'ACTIVE', label: '生效中' },
  { value: 'ENDED', label: '已结束' },
  { value: 'REJECTED', label: '已拒绝' },
]

/**
 * 推广计划管理页面（运营推广位）。
 *
 * - 列表按状态分页展示推广计划
 * - 待审核状态下，管理员可填写审核意见后通过或拒绝
 * - 与“全局提升审核”页面（{@code dashboard/promotions}）独立，避免概念混淆
 */
export function PromotionCampaignsPage() {
  const [activeStatus, setActiveStatus] = useState<CampaignStatus>('PENDING_REVIEW')
  const { data, isLoading, error } = usePromotionCampaigns(activeStatus)
  const approveMutation = useApprovePromotionCampaign()
  const rejectMutation = useRejectPromotionCampaign()
  const [comments, setComments] = useState<Record<number, string>>({})

  const items = useMemo(() => data?.items ?? [], [data])

  return (
    <div className="space-y-6 p-6" data-testid="promotion-campaigns-page">
      <header>
        <h1 className="text-2xl font-semibold">推广计划管理</h1>
        <p className="text-sm text-muted-foreground">维护首页、搜索页等运营推广位的投放计划。</p>
      </header>

      <nav className="flex gap-2" role="tablist" aria-label="推广计划状态">
        {STATUS_TABS.map((tab) => (
          <button
            key={tab.value}
            role="tab"
            aria-selected={activeStatus === tab.value}
            onClick={() => setActiveStatus(tab.value)}
            className={`rounded-md border px-3 py-1 text-sm ${
              activeStatus === tab.value
                ? 'border-primary bg-primary/10 text-primary'
                : 'border-muted bg-background'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </nav>

      {isLoading ? <div>加载中...</div> : null}
      {error ? <div className="text-destructive">{(error as Error).message}</div> : null}

      <ul className="space-y-3">
        {items.map((campaign) => (
          <li key={campaign.id} className="rounded-lg border p-4" data-testid={`campaign-row-${campaign.id}`}>
            <div className="flex items-start justify-between gap-4">
              <div>
                <div className="font-medium">{campaign.title}</div>
                <div className="text-xs text-muted-foreground">
                  {campaign.slotCode} · {campaign.targetType} #{campaign.targetId} · 优先级 {campaign.priority}
                </div>
                <div className="text-xs text-muted-foreground">
                  {campaign.startsAt} → {campaign.endsAt}
                </div>
              </div>
              <span className="rounded bg-muted px-2 py-1 text-xs">{campaign.status}</span>
            </div>
            {campaign.subtitle ? <p className="mt-2 text-sm">{campaign.subtitle}</p> : null}

            {activeStatus === 'PENDING_REVIEW' ? (
              <div className="mt-3 space-y-2">
                <label className="text-xs text-muted-foreground" htmlFor={`comment-${campaign.id}`}>
                  审核意见
                </label>
                <textarea
                  id={`comment-${campaign.id}`}
                  className="w-full rounded border px-2 py-1 text-sm"
                  rows={2}
                  value={comments[campaign.id] ?? ''}
                  onChange={(event) =>
                    setComments((prev) => ({ ...prev, [campaign.id]: event.target.value }))
                  }
                />
                <div className="flex gap-2">
                  <button
                    type="button"
                    className="rounded bg-primary px-3 py-1 text-sm text-primary-foreground"
                    disabled={approveMutation.isPending}
                    onClick={() =>
                      approveMutation.mutate({ id: campaign.id, comment: comments[campaign.id] })
                    }
                  >
                    通过
                  </button>
                  <button
                    type="button"
                    className="rounded border border-destructive px-3 py-1 text-sm text-destructive"
                    disabled={rejectMutation.isPending}
                    onClick={() =>
                      rejectMutation.mutate({ id: campaign.id, comment: comments[campaign.id] })
                    }
                  >
                    拒绝
                  </button>
                </div>
              </div>
            ) : campaign.reviewComment ? (
              <p className="mt-2 text-xs text-muted-foreground">审核意见：{campaign.reviewComment}</p>
            ) : null}
          </li>
        ))}
        {!isLoading && items.length === 0 ? (
          <li className="rounded-lg border border-dashed p-8 text-center text-sm text-muted-foreground">
            暂无{STATUS_TABS.find((t) => t.value === activeStatus)?.label}的推广计划
          </li>
        ) : null}
      </ul>
    </div>
  )
}

export default PromotionCampaignsPage
