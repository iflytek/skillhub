#!/usr/bin/env node

import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'

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

export const REQUIRED_FIELDS = [
  'screen',
  'state',
  'theme',
  'steps',
  'expected',
  'actual',
  'artifact_path',
  'owner',
  'timestamp',
  'result',
  'na_reason',
]

const ALLOWED_RESULTS = new Set(['PASS', 'FAIL', 'N/A'])
const REQUIRED_THEMES = ['light', 'dark']

function parseArgs(argv) {
  const args = {
    matrix: null,
    artifactsRoot: process.cwd(),
  }

  for (let index = 0; index < argv.length; index += 1) {
    const token = argv[index]
    if (token === '--matrix') {
      args.matrix = argv[index + 1] ?? null
      index += 1
      continue
    }
    if (token === '--artifacts-root') {
      args.artifactsRoot = argv[index + 1]
        ? path.resolve(process.cwd(), argv[index + 1])
        : process.cwd()
      index += 1
    }
  }

  if (!args.matrix) {
    throw new Error("Missing required argument '--matrix <path>'.")
  }

  return {
    matrix: path.resolve(process.cwd(), args.matrix),
    artifactsRoot: args.artifactsRoot,
  }
}

function normalizeKey(value) {
  return String(value ?? '').trim().toLowerCase()
}

function extractSection(text, heading) {
  const marker = `## ${heading}`
  const start = text.indexOf(marker)
  if (start === -1) {
    throw new Error(`Missing heading '${marker}'.`)
  }

  const nextHeadingIndex = text.indexOf('\n## ', start + marker.length)
  if (nextHeadingIndex === -1) {
    return text.slice(start + marker.length).trim()
  }
  return text.slice(start + marker.length, nextHeadingIndex).trim()
}

function parseMarkdownTable(sectionText, tableName) {
  const lines = sectionText
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)

  const tableLines = lines.filter((line) => line.startsWith('|') && line.endsWith('|'))
  if (tableLines.length < 3) {
    throw new Error(`${tableName}: markdown table is missing or incomplete.`)
  }

  const parseRow = (line) =>
    line
      .split('|')
      .slice(1, -1)
      .map((cell) => cell.trim())

  const header = parseRow(tableLines[0])
  const dataLines = tableLines.slice(2)
  const rows = dataLines.map((line) => parseRow(line))

  for (const row of rows) {
    if (row.length !== header.length) {
      throw new Error(`${tableName}: table row has ${row.length} columns, expected ${header.length}.`)
    }
  }

  const records = rows.map((row) => {
    const record = {}
    for (let index = 0; index < header.length; index += 1) {
      record[header[index]] = row[index]
    }
    return record
  })

  return { header, records }
}

function assertStateMatrix(stateMatrix) {
  const normalizedHeader = stateMatrix.header.map(normalizeKey)
  const requiredColumns = ['screen', ...REQUIRED_STATES]

  for (const column of requiredColumns) {
    if (!normalizedHeader.includes(column)) {
      throw new Error(`State Matrix: missing required column '${column}'.`)
    }
  }

  const normalizedScreens = new Set(
    stateMatrix.records.map((record) => normalizeKey(record[stateMatrix.header[normalizedHeader.indexOf('screen')]]))
  )

  for (const screen of REQUIRED_SCREENS) {
    if (!normalizedScreens.has(screen)) {
      throw new Error(`State Matrix: missing row for screen '${screen}'.`)
    }
  }
}

function assertEvidenceLog(evidenceLog, artifactsRoot) {
  const normalizedHeader = evidenceLog.header.map(normalizeKey)

  for (const field of REQUIRED_FIELDS) {
    if (!normalizedHeader.includes(field)) {
      throw new Error(`Evidence Log: missing required column '${field}'.`)
    }
  }

  const getField = (record, field) => {
    const index = normalizedHeader.indexOf(field)
    return String(record[evidenceLog.header[index]] ?? '').trim()
  }

  if (evidenceLog.records.length === 0) {
    throw new Error('Evidence Log: at least one evidence row is required.')
  }

  const normalizedRows = []
  const seenStates = new Set()
  const screenThemeCoverage = new Map()

  evidenceLog.records.forEach((record, rowIndex) => {
    const rowNumber = rowIndex + 1
    const screen = getField(record, 'screen')
    const state = getField(record, 'state')
    const theme = getField(record, 'theme')
    const steps = getField(record, 'steps')
    const expected = getField(record, 'expected')
    const actual = getField(record, 'actual')
    const artifactPath = getField(record, 'artifact_path')
    const owner = getField(record, 'owner')
    const timestamp = getField(record, 'timestamp')
    const result = getField(record, 'result').toUpperCase()
    const naReason = getField(record, 'na_reason')

    const mandatory = {
      screen,
      state,
      theme,
      steps,
      expected,
      actual,
      artifact_path: artifactPath,
      owner,
      timestamp,
      result,
    }

    for (const [fieldName, value] of Object.entries(mandatory)) {
      if (!value) {
        throw new Error(`Evidence Log row ${rowNumber}: field '${fieldName}' is required.`)
      }
    }

    if (!ALLOWED_RESULTS.has(result)) {
      throw new Error(`Evidence Log row ${rowNumber}: unsupported result '${result}'.`)
    }

    if (!Number.isFinite(Date.parse(timestamp))) {
      throw new Error(`Evidence Log row ${rowNumber}: timestamp '${timestamp}' is malformed.`)
    }

    const normalizedTheme = normalizeKey(theme)
    if (normalizedTheme !== 'light' && normalizedTheme !== 'dark') {
      throw new Error(`Evidence Log row ${rowNumber}: theme must be 'light' or 'dark'.`)
    }

    if (result === 'PASS') {
      if (expected !== actual) {
        throw new Error(`Evidence Log row ${rowNumber}: PASS requires actual to equal expected.`)
      }
      if (naReason) {
        throw new Error(`Evidence Log row ${rowNumber}: PASS requires empty na_reason.`)
      }
      const resolvedArtifactPath = path.resolve(artifactsRoot, artifactPath)
      if (!fs.existsSync(resolvedArtifactPath)) {
        throw new Error(`Evidence Log row ${rowNumber}: artifact '${artifactPath}' does not exist.`)
      }
    }

    if (result === 'N/A' && !naReason) {
      throw new Error(`Evidence Log row ${rowNumber}: N/A requires non-empty na_reason.`)
    }

    normalizedRows.push({
      screen: normalizeKey(screen),
      state: normalizeKey(state),
      theme: normalizedTheme,
      result,
    })

    seenStates.add(normalizeKey(state))
    const coverageKey = normalizeKey(screen)
    if (!screenThemeCoverage.has(coverageKey)) {
      screenThemeCoverage.set(coverageKey, new Set())
    }
    screenThemeCoverage.get(coverageKey).add(normalizedTheme)
  })

  for (const state of REQUIRED_STATES) {
    if (!seenStates.has(state)) {
      throw new Error(`Evidence Log: missing evidence for state '${state}'.`)
    }
  }

  for (const screen of REQUIRED_SCREENS) {
    const themes = screenThemeCoverage.get(screen)
    if (!themes || !themes.has('light') || !themes.has('dark')) {
      throw new Error(`Evidence Log: screen '${screen}' must include both light and dark evidence rows.`)
    }
  }

  return normalizedRows
}

function assertStateMatrixEvidenceParity(stateMatrix, evidenceRows) {
  const normalizedHeader = stateMatrix.header.map(normalizeKey)
  const screenColumnIndex = normalizedHeader.indexOf('screen')
  const stateColumnIndices = new Map(
    REQUIRED_STATES.map((state) => [state, normalizedHeader.indexOf(state)]),
  )

  const evidenceIndex = new Map()
  for (const row of evidenceRows) {
    const key = `${row.screen}|${row.state}|${row.theme}`
    if (!evidenceIndex.has(key)) {
      evidenceIndex.set(key, new Set())
    }
    evidenceIndex.get(key).add(row.result)
  }

  for (const record of stateMatrix.records) {
    const screen = normalizeKey(record[stateMatrix.header[screenColumnIndex]])
    for (const state of REQUIRED_STATES) {
      const stateColumnIndex = stateColumnIndices.get(state)
      const rawMatrixValue = String(record[stateMatrix.header[stateColumnIndex]] ?? '').trim().toUpperCase()
      if (!ALLOWED_RESULTS.has(rawMatrixValue)) {
        throw new Error(
          `State Matrix: unsupported value '${rawMatrixValue || '(empty)'}' for screen '${screen}', state '${state}'.`,
        )
      }

      if (rawMatrixValue !== 'PASS' && rawMatrixValue !== 'N/A') {
        continue
      }

      for (const theme of REQUIRED_THEMES) {
        const key = `${screen}|${state}|${theme}`
        const resultsForCombination = evidenceIndex.get(key)
        if (!resultsForCombination) {
          throw new Error(
            `State Matrix: missing evidence for screen '${screen}', state '${state}', theme '${theme}' (expected result '${rawMatrixValue}').`,
          )
        }

        if (!resultsForCombination.has(rawMatrixValue)) {
          throw new Error(
            `State Matrix: evidence for screen '${screen}', state '${state}', theme '${theme}' must include result '${rawMatrixValue}'.`,
          )
        }
      }
    }
  }
}

function main() {
  const args = parseArgs(process.argv.slice(2))

  if (!fs.existsSync(args.matrix)) {
    throw new Error(`Matrix file not found: ${args.matrix}`)
  }

  const matrixText = fs.readFileSync(args.matrix, 'utf8')
  const stateMatrixSection = extractSection(matrixText, 'State Matrix')
  const evidenceLogSection = extractSection(matrixText, 'Evidence Log')

  const stateMatrix = parseMarkdownTable(stateMatrixSection, 'State Matrix')
  const evidenceLog = parseMarkdownTable(evidenceLogSection, 'Evidence Log')

  assertStateMatrix(stateMatrix)
  const evidenceRows = assertEvidenceLog(evidenceLog, args.artifactsRoot)
  assertStateMatrixEvidenceParity(stateMatrix, evidenceRows)

  console.log(`check:parity-matrix passed (${evidenceLog.records.length} evidence rows validated).`)
}

try {
  main()
} catch (error) {
  console.error(`[check:parity-matrix] ${error instanceof Error ? error.message : String(error)}`)
  process.exitCode = 1
}
