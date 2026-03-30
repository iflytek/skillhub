import { useTranslation } from 'react-i18next'
import { Button } from '@/shared/ui/button'
import { useCopyToClipboard } from '@/shared/lib/clipboard'

interface CopyButtonProps {
  text: string
  className?: string
}

export function CopyButton({ text, className }: CopyButtonProps) {
  const { t } = useTranslation()
  const [copied, copy] = useCopyToClipboard()

  const handleCopy = async () => {
    try {
      await copy(text)
    } catch (err) {
      console.error('Failed to copy:', err)
    }
  }

  return (
    <Button
      variant="outline"
      size="sm"
      onClick={handleCopy}
      className={className}
    >
      {copied ? t('copyButton.copied') : t('copyButton.copy')}
    </Button>
  )
}
