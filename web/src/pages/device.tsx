import { useState, useRef, useEffect } from 'react'
import { Card } from '@/shared/ui/card'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { fetchJson, getCsrfHeaders } from '@/api/client'

async function authorizeDevice(userCode: string): Promise<void> {
  await fetchJson<void>('/api/v1/device/authorize', {
    method: 'POST',
    headers: getCsrfHeaders({
      'Content-Type': 'application/json',
    }),
    body: JSON.stringify({ userCode }),
  })
}

export function DeviceAuthPage() {
  const [part1, setPart1] = useState('')
  const [part2, setPart2] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null)

  const input1Ref = useRef<HTMLInputElement>(null)
  const input2Ref = useRef<HTMLInputElement>(null)

  useEffect(() => {
    input1Ref.current?.focus()
  }, [])

  const handlePart1Change = (value: string) => {
    const cleaned = value.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 4)
    setPart1(cleaned)
    if (cleaned.length === 4) {
      input2Ref.current?.focus()
    }
  }

  const handlePart2Change = (value: string) => {
    const cleaned = value.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 4)
    setPart2(cleaned)
  }

  const handlePaste = (e: React.ClipboardEvent) => {
    e.preventDefault()
    const pasted = e.clipboardData.getData('text').toUpperCase().replace(/[^A-Z0-9]/g, '')

    if (pasted.length >= 8) {
      setPart1(pasted.slice(0, 4))
      setPart2(pasted.slice(4, 8))
      input2Ref.current?.focus()
    } else if (pasted.length > 0) {
      setPart1(pasted.slice(0, 4))
      if (pasted.length > 4) {
        setPart2(pasted.slice(4))
      }
    }
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()

    if (part1.length !== 4 || part2.length !== 4) {
      setMessage({ type: 'error', text: '请输入完整的 8 位用户码' })
      return
    }

    const userCode = `${part1}-${part2}`
    setIsSubmitting(true)
    setMessage(null)

    try {
      await authorizeDevice(userCode)
      setMessage({ type: 'success', text: '设备授权成功！' })
      setPart1('')
      setPart2('')
      input1Ref.current?.focus()
    } catch (error) {
      setMessage({
        type: 'error',
        text: error instanceof Error ? error.message : '授权失败，请检查用户码是否正确'
      })
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center p-4">
      <Card className="w-full max-w-md p-8 space-y-6">
        <div className="text-center space-y-2">
          <h1 className="text-3xl font-bold">设备授权</h1>
          <p className="text-muted-foreground">
            请输入设备上显示的 8 位用户码
          </p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-6">
          <div className="space-y-2">
            <Label>用户码</Label>
            <div className="flex items-center gap-3">
              <Input
                ref={input1Ref}
                type="text"
                value={part1}
                onChange={(e) => handlePart1Change(e.target.value)}
                onPaste={handlePaste}
                placeholder="XXXX"
                className="text-center text-2xl font-mono tracking-wider"
                maxLength={4}
              />
              <span className="text-2xl font-bold text-muted-foreground">-</span>
              <Input
                ref={input2Ref}
                type="text"
                value={part2}
                onChange={(e) => handlePart2Change(e.target.value)}
                onPaste={handlePaste}
                placeholder="XXXX"
                className="text-center text-2xl font-mono tracking-wider"
                maxLength={4}
              />
            </div>
            <p className="text-sm text-muted-foreground">
              格式: XXXX-XXXX (支持粘贴)
            </p>
          </div>

          {message && (
            <div
              className={`p-4 rounded-md ${
                message.type === 'success'
                  ? 'bg-green-50 text-green-800 border border-green-200'
                  : 'bg-red-50 text-red-800 border border-red-200'
              }`}
            >
              {message.text}
            </div>
          )}

          <Button
            type="submit"
            className="w-full"
            disabled={isSubmitting || part1.length !== 4 || part2.length !== 4}
          >
            {isSubmitting ? '授权中...' : '授权设备'}
          </Button>
        </form>

        <div className="text-center text-sm text-muted-foreground">
          <p>授权后，设备将可以访问你的账户</p>
        </div>
      </Card>
    </div>
  )
}
