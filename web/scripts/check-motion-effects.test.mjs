import { test } from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { spawnSync } from 'node:child_process'

const webRoot = path.resolve(import.meta.dirname, '..')
const scriptPath = path.resolve(import.meta.dirname, 'check-motion-effects.mjs')
const fixtureRoot = path.resolve(import.meta.dirname, '__fixtures__/check-motion')

function runCheck(scopePath) {
  return spawnSync(process.execPath, [scriptPath, '--scope', scopePath], {
    cwd: webRoot,
    encoding: 'utf8',
  })
}

function runDefaultCheck(cwd) {
  return spawnSync(process.execPath, [scriptPath], {
    cwd,
    encoding: 'utf8',
  })
}

test('known-good fixture passes motion checker', () => {
  const result = runCheck(path.resolve(fixtureRoot, 'known-good.tsx'))
  assert.equal(result.status, 0, result.stderr || result.stdout)
  assert.match(result.stdout, /check:motion passed/i)
})

test('known-bad fixture fails motion checker', () => {
  const result = runCheck(path.resolve(fixtureRoot, 'known-bad.tsx'))
  assert.equal(result.status, 1, 'Expected known-bad fixture to fail')
  assert.match(result.stderr, /forbidden-infinite-motion/)
  assert.match(result.stderr, /transition-duration-range/)
  assert.match(result.stderr, /shadow-alpha-cap/)
  assert.match(result.stderr, /glow-alpha-cap/)
  assert.match(result.stderr, /blur-cap/)
})

test('default scan checks repo-wide src and includes css files', () => {
  const tempRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'check-motion-default-'))
  const tsxViolationPath = path.join(tempRoot, 'src/features/widgets/banner.tsx')
  const cssViolationPath = path.join(tempRoot, 'src/styles/theme.css')

  try {
    fs.mkdirSync(path.dirname(tsxViolationPath), { recursive: true })
    fs.mkdirSync(path.dirname(cssViolationPath), { recursive: true })
    fs.writeFileSync(
      tsxViolationPath,
      "export const Banner = () => <div className=\"animate-bounce duration-200\">Banner</div>\n",
      'utf8',
    )
    fs.writeFileSync(
      cssViolationPath,
      '.hero-glow { @apply blur-2xl; }\n',
      'utf8',
    )

    const result = runDefaultCheck(tempRoot)
    assert.equal(result.status, 1, 'Expected default scan to detect violations under src/**')
    assert.match(result.stderr, /src\/features\/widgets\/banner\.tsx/)
    assert.match(result.stderr, /src\/styles\/theme\.css/)
  } finally {
    fs.rmSync(tempRoot, { recursive: true, force: true })
  }
})
