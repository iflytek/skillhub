import { useSearch } from '@tanstack/react-router'
import { useState } from 'react'
import { fetchJson } from '@/api/client'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'

interface MockUassLoginResponse {
  redirectUrl: string
}

/**
 * Development-only page that simulates a third-party UASS login screen.
 *
 * It lets a tester enter the upstream identity payload and then jumps back into
 * the normal SkillHub UASS callback flow.
 */
export function MockUassPage() {
  const search = useSearch({ from: '/mock-uass' })
  const [ussId, setUssId] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [mobile, setMobile] = useState('')
  const [email, setEmail] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit() {
    if (!search.state || !search.callbackUrl) {
      setError('缺少必要的 state 或 callbackUrl，无法继续模拟登录。')
      return
    }
    if (!ussId.trim() || !displayName.trim()) {
      setError('请至少填写 ussId 和用户名。')
      return
    }

    setSubmitting(true)
    setError(null)
    try {
      const result = await fetchJson<MockUassLoginResponse>('/api/v1/auth/uass/mock/login', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          state: search.state,
          callbackUrl: search.callbackUrl,
          ussId: ussId.trim(),
          displayName: displayName.trim(),
          mobile: mobile.trim() || undefined,
          email: email.trim() || undefined,
        }),
      })
      window.location.href = result.redirectUrl
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : '模拟登录失败')
      setSubmitting(false)
    }
  }

  return (
    <div className="flex min-h-[70vh] items-center justify-center px-4">
      <div className="w-full max-w-lg rounded-3xl border border-orange-300/40 bg-gradient-to-br from-orange-50 to-amber-100 p-8 shadow-xl">
        <div className="space-y-2">
          <p className="text-sm font-semibold uppercase tracking-[0.24em] text-orange-700">Mock UASS</p>
          <h1 className="text-3xl font-bold text-slate-900">模拟第三方登录页</h1>
          <p className="text-sm text-slate-600">
            填写第三方返回的用户资料，点击登录后将跳回 SkillHub 的 UASS callback，并完成本地会话建立。
          </p>
        </div>

        <div className="mt-8 space-y-4">
          <div className="space-y-2">
            <label className="text-sm font-medium text-slate-800" htmlFor="ussId">ussId</label>
            <Input id="ussId" value={ussId} onChange={(event) => setUssId(event.target.value)} placeholder="例如：uass-001" />
          </div>

          <div className="space-y-2">
            <label className="text-sm font-medium text-slate-800" htmlFor="displayName">用户名</label>
            <Input id="displayName" value={displayName} onChange={(event) => setDisplayName(event.target.value)} placeholder="例如：张三" />
          </div>

          <div className="space-y-2">
            <label className="text-sm font-medium text-slate-800" htmlFor="mobile">手机号码</label>
            <Input id="mobile" value={mobile} onChange={(event) => setMobile(event.target.value)} placeholder="例如：13800000000" />
          </div>

          <div className="space-y-2">
            <label className="text-sm font-medium text-slate-800" htmlFor="email">邮箱</label>
            <Input id="email" value={email} onChange={(event) => setEmail(event.target.value)} placeholder="例如：zhangsan@example.com" />
          </div>

          {error ? (
            <div className="rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
              {error}
            </div>
          ) : null}

          <div className="rounded-2xl border border-orange-200 bg-white/60 px-4 py-3 text-xs text-slate-500">
            <p>state: {search.state || '-'}</p>
            <p className="break-all">callbackUrl: {search.callbackUrl || '-'}</p>
          </div>

          <Button className="w-full" disabled={submitting} onClick={handleSubmit} type="button">
            {submitting ? '登录中...' : '登录并跳回 SkillHub'}
          </Button>
        </div>
      </div>
    </div>
  )
}
