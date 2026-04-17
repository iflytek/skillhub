import { test } from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { spawnSync } from 'node:child_process'

const webRoot = path.resolve(import.meta.dirname, '..')
const scriptPath = path.resolve(import.meta.dirname, 'check-no-hardcoded-colors.mjs')
const fixtureRoot = path.resolve(import.meta.dirname, '__fixtures__/check-colors')

function runCheck(args) {
  return spawnSync(process.execPath, [scriptPath, ...args], {
    cwd: webRoot,
    encoding: 'utf8',
  })
}

test('known-good fixture passes strict mode', () => {
  const goodScope = path.resolve(fixtureRoot, 'known-good.tsx')
  const result = runCheck(['--mode', 'strict', '--scope', goodScope])
  assert.equal(result.status, 0, result.stderr || result.stdout)
  assert.match(result.stdout, /strict passed with 0 violations/i)
})

test('known-bad fixture fails strict mode and reports file path + line number', () => {
  const badScope = path.resolve(fixtureRoot, 'known-bad.tsx')
  const result = runCheck(['--mode', 'strict', '--scope', badScope])

  assert.equal(result.status, 1, 'Expected strict mode to fail for known-bad fixture')
  assert.match(result.stderr, /known-bad\.tsx:\d+ \[forbidden-utility\]/)
  assert.match(result.stderr, /known-bad\.tsx:\d+ \[literal-color\]/)
  assert.match(result.stderr, /known-bad\.tsx:\d+ \[inline-style-literal\]/)
})

test('strict mode fails when scope path does not exist', () => {
  const missingScope = path.resolve(fixtureRoot, 'missing-scope.tsx')
  const result = runCheck(['--mode', 'strict', '--scope', missingScope])

  assert.equal(result.status, 1, 'Expected strict mode to fail for non-existent scope')
  assert.match(result.stderr, /scope path does not exist/i)
})

test('strict mode fails when scope resolves to zero scannable files', () => {
  const tempRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'check-colors-empty-'))
  try {
    const result = runCheck(['--mode', 'strict', '--scope', tempRoot])
    assert.equal(result.status, 1, 'Expected strict mode to fail for empty scope')
    assert.match(result.stderr, /strict mode requires at least one scannable file/i)
  } finally {
    fs.rmSync(tempRoot, { recursive: true, force: true })
  }
})
