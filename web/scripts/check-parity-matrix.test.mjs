import { test } from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { spawnSync } from 'node:child_process'

const webRoot = path.resolve(import.meta.dirname, '..')
const scriptPath = path.resolve(import.meta.dirname, 'check-parity-matrix.mjs')

const REQUIRED_SCREENS = [
  'landing',
  'dashboard',
  'collection detail',
  'skill detail',
  'notifications',
  'login',
  'register',
  'reset password',
]

const REQUIRED_STATES = [
  'default',
  'hover',
  'focus',
  'disabled',
  'invalid',
  'loading',
  'empty',
  'dialog/dropdown',
]

function buildStateMatrixRows(overrides = new Map()) {
  return REQUIRED_SCREENS.map((screen) => {
    const states = REQUIRED_STATES.map((state) => overrides.get(`${screen}|${state}`) ?? 'FAIL')
    return `| ${screen} | ${states.join(' | ')} |`
  }).join('\n')
}

function createBaseEvidenceRows() {
  return REQUIRED_SCREENS.flatMap((screen, index) => {
    const lightState = REQUIRED_STATES[index]
    const darkState = REQUIRED_STATES[(index + 1) % REQUIRED_STATES.length]
    return [
      {
        screen,
        state: lightState,
        theme: 'light',
        steps: `Check ${screen} in light theme.`,
        expected: 'baseline expected',
        actual: 'baseline actual',
        artifact_path: 'artifacts/placeholder.txt',
        owner: 'test-owner',
        timestamp: `2026-04-17T10:${String(index).padStart(2, '0')}:00Z`,
        result: 'FAIL',
        na_reason: '',
      },
      {
        screen,
        state: darkState,
        theme: 'dark',
        steps: `Check ${screen} in dark theme.`,
        expected: 'baseline expected',
        actual: 'baseline actual',
        artifact_path: 'artifacts/placeholder.txt',
        owner: 'test-owner',
        timestamp: `2026-04-17T11:${String(index).padStart(2, '0')}:00Z`,
        result: 'FAIL',
        na_reason: '',
      },
    ]
  })
}

function renderEvidenceTable(rows) {
  const header = '| screen | state | theme | steps | expected | actual | artifact_path | owner | timestamp | result | na_reason |'
  const separator = '| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |'
  const body = rows
    .map((row) =>
      `| ${row.screen} | ${row.state} | ${row.theme} | ${row.steps} | ${row.expected} | ${row.actual} | ${row.artifact_path} | ${row.owner} | ${row.timestamp} | ${row.result} | ${row.na_reason} |`,
    )
    .join('\n')
  return `${header}\n${separator}\n${body}`
}

function writeMatrixFixture(tempRoot, matrixRows, evidenceRows) {
  const matrixPath = path.join(tempRoot, 'matrix.md')
  const matrixContent = `# Test Parity Matrix

## State Matrix

| screen | default | hover | focus | disabled | invalid | loading | empty | dialog/dropdown |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
${matrixRows}

## Evidence Log

${renderEvidenceTable(evidenceRows)}
`
  fs.writeFileSync(matrixPath, matrixContent, 'utf8')
  return matrixPath
}

function runCheck(matrixPath, artifactsRoot) {
  return spawnSync(process.execPath, [scriptPath, '--matrix', matrixPath, '--artifacts-root', artifactsRoot], {
    cwd: webRoot,
    encoding: 'utf8',
  })
}

test('fails when matrix PASS has no matching evidence combination for both themes', () => {
  const tempRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'parity-matrix-missing-'))
  try {
    const overrides = new Map([['landing|default', 'PASS']])
    const evidenceRows = createBaseEvidenceRows()

    evidenceRows[0] = {
      ...evidenceRows[0],
      state: 'default',
      result: 'PASS',
      expected: 'landing default light is correct',
      actual: 'landing default light is correct',
      artifact_path: 'artifacts/landing-default-light.txt',
    }

    fs.mkdirSync(path.join(tempRoot, 'artifacts'), { recursive: true })
    fs.writeFileSync(path.join(tempRoot, 'artifacts/landing-default-light.txt'), 'ok', 'utf8')

    const matrixPath = writeMatrixFixture(tempRoot, buildStateMatrixRows(overrides), evidenceRows)
    const result = runCheck(matrixPath, tempRoot)

    assert.equal(result.status, 1, 'Expected parity check to fail on missing matrix/evidence combination')
    assert.match(result.stderr, /missing evidence for screen 'landing', state 'default', theme 'dark'/i)
  } finally {
    fs.rmSync(tempRoot, { recursive: true, force: true })
  }
})

test('fails when matrix N/A combination does not contain N/A evidence result', () => {
  const tempRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'parity-matrix-result-'))
  try {
    const overrides = new Map([['landing|hover', 'N/A']])
    const evidenceRows = createBaseEvidenceRows()

    evidenceRows[0] = {
      ...evidenceRows[0],
      state: 'hover',
      result: 'FAIL',
    }
    evidenceRows[1] = {
      ...evidenceRows[1],
      state: 'hover',
      result: 'FAIL',
    }

    const matrixPath = writeMatrixFixture(tempRoot, buildStateMatrixRows(overrides), evidenceRows)
    const result = runCheck(matrixPath, tempRoot)

    assert.equal(result.status, 1, 'Expected parity check to fail on matrix/evidence result mismatch')
    assert.match(result.stderr, /must include result 'N\/A'/i)
  } finally {
    fs.rmSync(tempRoot, { recursive: true, force: true })
  }
})

test('passes when matrix contains only FAIL cells and evidence schema is valid', () => {
  const tempRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'parity-matrix-pass-'))
  try {
    const matrixRows = buildStateMatrixRows()
    const evidenceRows = createBaseEvidenceRows()
    const matrixPath = writeMatrixFixture(tempRoot, matrixRows, evidenceRows)
    const result = runCheck(matrixPath, tempRoot)

    assert.equal(result.status, 0, result.stderr || result.stdout)
    assert.match(result.stdout, /check:parity-matrix passed/i)
  } finally {
    fs.rmSync(tempRoot, { recursive: true, force: true })
  }
})
