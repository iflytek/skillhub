import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { tmpdir } from 'node:os'
import { dirname, join } from 'node:path'
import { pathToFileURL } from 'node:url'
import { expect, it, vi } from 'vitest'
import { ESLint } from 'eslint'

const require = createRequire(import.meta.url)
const eslintRequire = createRequire(require.resolve('eslint'))

it('preserves ESLint ESM override inclusion and exclusion with minimatch 11', async () => {
  const entry = eslintRequire.resolve('@eslint/eslintrc')
  const { OverrideTester } = await import(pathToFileURL(join(dirname(entry), '../lib/config-array/override-tester.js')).href)
  const base = tmpdir()
  const matcher = OverrideTester.create(['*.ts'], ['**/*.test.ts'], base)
  expect(matcher.test(join(base, 'src/index.ts'))).toBe(true)
  expect(matcher.test(join(base, 'src/index.test.ts'))).toBe(false)
  expect(matcher.test(join(base, 'src/index.js'))).toBe(false)
})

it('only sends configured Redocly headers to matching URLs', async () => {
  const openapiRequire = createRequire(require.resolve('openapi-typescript'))
  const { readFileFromUrl } = openapiRequire('@redocly/openapi-core/lib/utils.js')
  const customFetch = vi.fn(async () => new Response('{}', { headers: { 'content-type': 'application/json' } }))
  const config = { headers: [{ matches: 'example.test/specs/**', name: 'X-Spec', value: 'fixture' }], customFetch }
  await readFileFromUrl('https://example.test/specs/api.json', config)
  await readFileFromUrl('https://other.test/specs/api.json', config)
  expect(customFetch.mock.calls[0][1].headers).toEqual({ 'X-Spec': 'fixture' })
  expect(customFetch.mock.calls[1][1].headers).toEqual({})
})

it('persists and invalidates ESLint file caches after replacing flat-cache', async () => {
  const cwd = mkdtempSync(join(tmpdir(), 'skillhub-eslint-'))
  try {
    const file = join(cwd, 'example.js')
    const options = { cwd, useEslintrc: false, cache: true, cacheStrategy: 'content', cacheLocation: join(cwd, '.eslintcache'), overrideConfig: { rules: { 'no-undef': 'error' } } }
    writeFileSync(file, 'var value = 1\n')
    expect((await new ESLint(options).lintFiles(['*.js']))[0].errorCount).toBe(0)
    expect((await new ESLint(options).lintFiles(['*.js']))[0].errorCount).toBe(0)
    writeFileSync(file, 'missingName()\n')
    expect((await new ESLint(options).lintFiles(['*.js']))[0].messages[0].ruleId).toBe('no-undef')
  } finally {
    rmSync(cwd, { recursive: true, force: true })
  }
})
