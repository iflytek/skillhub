import { useCallback, useRef, useState } from 'react'

/**
 * Copy text to clipboard with fallback for insecure contexts (HTTP, iframes).
 */
export async function copyToClipboard(text: string): Promise<void> {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(text)
    return
  }
  // Fallback for insecure contexts (HTTP, iframes)
  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.style.position = 'fixed'
  textarea.style.opacity = '0'
  document.body.appendChild(textarea)
  textarea.select()
  const success = document.execCommand('copy')
  document.body.removeChild(textarea)

  if (!success) {
    throw new Error('Failed to copy text to clipboard')
  }
}

/**
 * React hook for clipboard copy with auto-reset "copied" state.
 *
 * @param timeout - ms before `copied` resets to false (default 2000)
 * @returns `[copied, copy]` — boolean status and an async copy function
 *
 * @example
 * const [copied, copy] = useCopyToClipboard()
 * <button onClick={() => copy('hello')}>
 *   {copied ? 'Copied!' : 'Copy'}
 * </button>
 */
export function useCopyToClipboard(timeout = 2000) {
  const [copied, setCopied] = useState(false)
  const timerRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)

  const copy = useCallback(async (text: string) => {
    await copyToClipboard(text)
    setCopied(true)
    if (timerRef.current) {
      clearTimeout(timerRef.current)
    }
    timerRef.current = setTimeout(() => setCopied(false), timeout)
  }, [timeout])

  return [copied, copy] as const
}
