import { useState, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { Check, Copy } from 'lucide-react'
import { Button } from '@/shared/ui/button'

interface InstallCommandProps {
  namespace: string
  slug: string
  version?: string
}

function getAppBaseUrl(): string {
  if (typeof window === 'undefined') {
    return ''
  }
  const runtimeConfig = window.__SKILLHUB_RUNTIME_CONFIG__
  if (runtimeConfig?.appBaseUrl) {
    return runtimeConfig.appBaseUrl
  }
  return `${window.location.protocol}//${window.location.host}`
}

export function InstallCommand({ slug }: InstallCommandProps) {
  const { t } = useTranslation()
  const [copied, setCopied] = useState(false)

  const baseUrl = useMemo(() => getAppBaseUrl(), [])

  const command = useMemo(() => {
    const installCmd = `clawhub install ${slug} `
    // 如果是默认的 clawhub.ai 不需要环境变量，否则显示完整配置
    if (baseUrl && !baseUrl.includes('clawhub.ai') && !baseUrl.includes('localhost') && !baseUrl.includes('127.0.0.1')) {
      return `CLAWHUB_SITE=${baseUrl} CLAWHUB_REGISTRY=${baseUrl} ${installCmd}`
    }
    return installCmd
  }, [baseUrl, slug])

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(command)
      setCopied(true)
      window.setTimeout(() => setCopied(false), 2000)
    } catch (err) {
      console.error('Failed to copy:', err)
    }
  }

  return (
    <div className="relative overflow-hidden rounded-xl border border-border/60 bg-muted/50">
      <Button
        type="button"
        variant="ghost"
        size="icon"
        onClick={handleCopy}
        title={copied ? t('copyButton.copied') : t('copyButton.copy')}
        aria-label={copied ? t('copyButton.copied') : t('copyButton.copy')}
        className="absolute right-2 top-2 z-10 h-8 w-8 rounded-md bg-background/80 backdrop-blur hover:bg-background"
      >
        {copied ? <Check className="h-4 w-4" /> : <Copy className="h-4 w-4" />}
      </Button>
      <pre className="p-4 pr-14 whitespace-pre-wrap break-words">
        <code className="font-mono text-sm leading-6 text-foreground whitespace-pre-wrap break-words">
          {command}
        </code>
      </pre>
    </div>
  )
}
