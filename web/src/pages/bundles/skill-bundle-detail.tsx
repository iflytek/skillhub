import { useState } from 'react'
import { useSkillBundleDetail } from '@/features/skill-bundle/hooks'

/**
 * Skill bundle detail page. Shows the bundle metadata, the lock-pinned skill list with
 * each item's role description, and an install command snippet. The page is intentionally
 * lightweight — richer interactions (star, rate, comment) live on follow-up branches.
 */
export function SkillBundleDetailPage({
  namespaceSlug,
  bundleSlug,
}: {
  namespaceSlug: string
  bundleSlug: string
}) {
  const { data, isLoading, error } = useSkillBundleDetail(namespaceSlug, bundleSlug)
  const [copied, setCopied] = useState(false)

  if (isLoading) return <div className="p-6">加载中...</div>
  if (error) return <div className="p-6 text-destructive">{(error as Error).message}</div>
  if (!data) return null

  const installCommand = `skillhub bundle install @${namespaceSlug}/${bundleSlug}`
  const onCopy = async () => {
    await navigator.clipboard?.writeText(installCommand)
    setCopied(true)
    window.setTimeout(() => setCopied(false), 1500)
  }

  return (
    <article className="space-y-6 p-6" data-testid="skill-bundle-detail">
      <header>
        <h1 className="text-2xl font-semibold">{data.displayName}</h1>
        <p className="text-sm text-muted-foreground">{data.summary}</p>
        <div className="mt-2 flex gap-3 text-xs text-muted-foreground">
          <span>类型 {data.type}</span>
          <span>下载 {data.downloadCount}</span>
          <span>收藏 {data.starCount}</span>
          {data.ratingAvg ? <span>评分 {data.ratingAvg.toFixed(1)} ({data.ratingCount})</span> : null}
        </div>
      </header>

      {data.version ? (
        <section>
          <div className="text-sm text-muted-foreground">
            当前版本 <strong>{data.version.version}</strong> · 状态 {data.version.status}
          </div>
        </section>
      ) : (
        <section className="rounded border border-amber-300 bg-amber-50 p-3 text-sm text-amber-800">
          尚未发布版本
        </section>
      )}

      <section>
        <div className="mb-2 flex items-center justify-between">
          <h2 className="text-lg font-medium">安装命令</h2>
          <button
            type="button"
            onClick={onCopy}
            className="rounded border px-2 py-1 text-xs"
            data-testid="copy-install-command"
          >
            {copied ? '已复制' : '复制'}
          </button>
        </div>
        <pre className="rounded bg-muted px-3 py-2 text-sm">{installCommand}</pre>
      </section>

      <section>
        <h2 className="text-lg font-medium">包含的技能</h2>
        <ul className="mt-2 space-y-2">
          {data.items.map((item) => (
            <li key={`${item.namespaceSlug}/${item.skillSlug}`} className="rounded border p-3">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <a className="font-medium hover:underline" href={item.detailUrl}>
                    {item.displayName}
                  </a>
                  <div className="text-xs text-muted-foreground">
                    @{item.namespaceSlug}/{item.skillSlug}@{item.version}
                  </div>
                </div>
                <div className="flex gap-2 text-xs">
                  {item.required ? (
                    <span className="rounded bg-primary/10 px-2 py-0.5 text-primary">必装</span>
                  ) : (
                    <span className="rounded bg-muted px-2 py-0.5 text-muted-foreground">可选</span>
                  )}
                  <span className="rounded bg-muted px-2 py-0.5 text-muted-foreground">
                    顺序 {item.installOrder}
                  </span>
                </div>
              </div>
              <p className="mt-2 text-sm">{item.roleDescription}</p>
            </li>
          ))}
        </ul>
      </section>
    </article>
  )
}

export default SkillBundleDetailPage
