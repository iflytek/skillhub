import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const stylesheet = new URL('./public/fonts/fonts.css', import.meta.url)
const css = readFileSync(stylesheet, 'utf8')
const references = [...css.matchAll(/url\(['"]?([^'"\)]+)['"]?\)/g)].map(match => match[1])

describe('self-hosted font assets', () => {
  it('keeps every declared font backed by a real WOFF2 file', () => {
    expect(references).toHaveLength(12)
    for (const reference of references) {
      const contents = readFileSync(new URL(reference, stylesheet))
      expect(contents.length).toBeGreaterThan(4)
      expect(contents.subarray(0, 4).toString('ascii')).toBe('wOF2')
    }
  })

  it.each(['/', '/skillhub/', '/release-v024/', '/nested/skillhub/'])(
    'resolves every font relative to its stylesheet under %s',
    (basePath) => {
      const stylesheetUrl = new URL(`${basePath}fonts/fonts.css`, 'https://example.test')
      for (const reference of references) {
        const filename = reference.split('/').at(-1)
        expect(new URL(reference, stylesheetUrl).href).toBe(`https://example.test${basePath}fonts/${filename}`)
      }
    },
  )
})
