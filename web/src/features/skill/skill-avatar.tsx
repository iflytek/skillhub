import { useState } from 'react'
import { buildApiUrl } from '@/api/client'

const fallbackPalettes = [
  'bg-[#edf3ff] text-[#4d658d] dark:bg-[#202d45] dark:text-[#c0d0ed]',
  'bg-[#edf5ef] text-[#4c735b] dark:bg-[#22372c] dark:text-[#bad9c5]',
  'bg-[#f7f1e7] text-[#80643a] dark:bg-[#3b3020] dark:text-[#e5cfaa]',
  'bg-[#f2eef9] text-[#70578d] dark:bg-[#322840] dark:text-[#d3c1eb]',
  'bg-[#f9eeee] text-[#8b555b] dark:bg-[#40282d] dark:text-[#edc0c6]',
  'bg-[#eaf4f5] text-[#426e75] dark:bg-[#20363b] dark:text-[#b4d8de]',
  'bg-[#fff1e8] text-[#8a6040] dark:bg-[#3b2c22] dark:text-[#e7c4a9]',
  'bg-[#eeefff] text-[#5c6091] dark:bg-[#2a2c46] dark:text-[#d0d3f4]',
] as const

function getFallbackPalette(seed: string) {
  let hash = 0
  for (let index = 0; index < seed.length; index += 1) {
    hash = (Math.imul(hash, 31) + seed.charCodeAt(index)) | 0
  }
  return fallbackPalettes[(hash >>> 0) % fallbackPalettes.length]
}

export function SkillAvatar({ name, url, seed, size = 'h-10 w-10' }: { name: string; url?: string | null; seed?: string; size?: string }) {
  const [failedUrl, setFailedUrl] = useState<string | null>(null)
  const imageUrl = url && failedUrl !== url ? buildApiUrl(url) : null
  return (
    <span className={`inline-flex shrink-0 items-center justify-center overflow-hidden rounded-lg font-semibold ${imageUrl ? 'bg-primary/10 text-primary' : getFallbackPalette(seed?.trim() || name.trim() || '?')} ${size}`} aria-hidden="true">
      {imageUrl ? <img src={imageUrl} alt="" className="h-full w-full object-cover" onError={() => setFailedUrl(url ?? null)} /> : (Array.from(name.trim())[0] ?? '?').toLocaleUpperCase()}
    </span>
  )
}
