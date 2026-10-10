import type { Page } from '@playwright/test'

export async function setEnglishLocale(page: Page) {
  await page.addInitScript(() => {
    window.localStorage.setItem('i18nextLng', 'en')
  })
}

export async function setUniqueClientIp(page: Page, seed: string) {
  const suffix = Date.now() + Math.floor(Math.random() * 1000)
  const thirdOctet = seed.split('').reduce((sum, char) => sum + char.charCodeAt(0), 0) % 250
  const fourthOctet = suffix % 250
  await page.context().setExtraHTTPHeaders({
    'X-Forwarded-For': `10.0.${thirdOctet}.${fourthOctet}`,
  })
}
