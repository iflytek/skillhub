import { useState, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { Check, Copy } from 'lucide-react'
import { Button } from '@/shared/ui/button'

interface InstallCommandProps {
  namespace: string
  slug: string
  version?: string
}

export function buildInstallTarget(namespace: string, slug: string): string {
  return namespace === 'global' ? slug : `${namespace}--${slug}`
}

export function getRegistryUrl(): string {
  if (typeof window === 'undefined') {
    return ''
  }
  const runtimeConfig = window.__SKILLHUB_RUNTIME_CONFIG__
  if (runtimeConfig?.registryUrl) {
    return runtimeConfig.registryUrl
  }
  if (runtimeConfig?.appBaseUrl) {
    return runtimeConfig.appBaseUrl
  }
  return `${window.location.protocol}//${window.location.host}`
}

export function buildInstallCommand(namespace: string, slug: string, registryUrl: string): string {
  const installTarget = buildInstallTarget(namespace, slug)
  return `npx clawhub install ${installTarget} --registry ${registryUrl}`
}

export function InstallCommand({ namespace, slug }: InstallCommandProps) {
  const { t } = useTranslation()
  const [copied, setCopied] = useState(false)

  const registryUrl = useMemo(() => getRegistryUrl(), [])

  const command = useMemo(() => buildInstallCommand(namespace, slug, registryUrl), [namespace, registryUrl, slug])

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
