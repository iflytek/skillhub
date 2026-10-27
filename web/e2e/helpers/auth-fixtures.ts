import { randomBytes } from 'node:crypto'
import type { BrowserContext, Page } from '@playwright/test'

const clientIpByContext = new WeakMap<BrowserContext, string>()

/** Stable identity for fixture setup, without replacing the context's other headers. */
export function fixtureClientIp(page: Page): string {
  const context = page.context()
  let address = clientIpByContext.get(context)
  if (!address) {
    address = `10.${[...randomBytes(3)].join('.')}`
    clientIpByContext.set(context, address)
  }
  return address
}

export async function setEnglishLocale(page: Page) {
  await page.addInitScript(() => {
    window.localStorage.setItem('i18nextLng', 'en')
  })
}

export async function setUniqueClientIp(page: Page, seed: string) {
  const suffix = Date.now() + Math.floor(Math.random() * 1000)
  const thirdOctet = seed.split('').reduce((sum, char) => sum + char.charCodeAt(0), 0) % 250
  const fourthOctet = suffix % 250
  const address = `10.0.${thirdOctet}.${fourthOctet}`
  clientIpByContext.set(page.context(), address)
  await page.context().setExtraHTTPHeaders({
    'X-Forwarded-For': address,
  })
}
