#!/usr/bin/env node

import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import { execSync } from 'node:child_process'

export const HARD_CODED_COLOR_PATTERN =
  /#(?:[0-9a-fA-F]{3,8})\b|rgba?\([^)]*\)|hsla?\([^)]*\d[^)]*\)/g
export const FORBIDDEN_UTILITY_CLASS_PATTERN =
  /(?:^|[\s"'`])(?:text|bg|border|ring|stroke|fill)-(?:red|orange|amber|yellow|lime|green|emerald|teal|cyan|sky|blue|indigo|violet|purple|fuchsia|pink|rose|slate|gray|zinc|neutral|stone)-\d{2,3}(?:\/\d{1,3})?(?=$|[\s"'`])/g
export const INLINE_COLOR_STYLE_PATTERN =
  /\b(?:color|background(?:Color)?|border(?:Color|TopColor|RightColor|BottomColor|LeftColor)?|outlineColor|textDecorationColor|fill|stroke)\s*:\s*(['"`])([^'"`]+)\1/g

export const ALLOWLIST_PATHS = [
  'src/assets/',
]

const TOKEN_ALLOW_START = '/* COLOR_POLICY_ALLOW_START:token-definitions */'
const TOKEN_ALLOW_END = '/* COLOR_POLICY_ALLOW_END:token-definitions */'
const DEFAULT_ALLOWLIST_PATH = path.resolve(process.cwd(), 'scripts/check-no-hardcoded-colors.allowlist.json')
const DEFAULT_SCOPE = path.resolve(process.cwd(), 'src')

function parseArgs(argv) {
  const args = {
    mode: null,
    baseRef: null,
    baselinePath: null,
    scope: DEFAULT_SCOPE,
    writeBaselinePath: null,
  }

  for (let index = 0; index < argv.length; index += 1) {
    const token = argv[index]

    if (token === '--mode') {
      args.mode = argv[index + 1] ?? null
      index += 1
      continue
    }

    if (token === '--base-ref') {
      args.baseRef = argv[index + 1] ?? null
      index += 1
      continue
    }

    if (token === '--baseline') {
      args.baselinePath = argv[index + 1] ?? null
      index += 1
      continue
    }

    if (token === '--scope') {
      args.scope = argv[index + 1] ? path.resolve(process.cwd(), argv[index + 1]) : DEFAULT_SCOPE
      index += 1
      continue
    }

    if (token === '--write-baseline') {
      args.writeBaselinePath = argv[index + 1] ? path.resolve(process.cwd(), argv[index + 1]) : null
      index += 1
      continue
    }
  }

  if (!args.mode) {
    throw new Error("Missing required '--mode' option. Use changed, baseline-diff, or strict.")
  }

  if (!['changed', 'baseline-diff', 'strict'].includes(args.mode)) {
    throw new Error(`Unsupported mode '${args.mode}'. Use changed, baseline-diff, or strict.`)
  }

  if (args.mode === 'baseline-diff' && !args.baselinePath) {
    throw new Error("Missing required '--baseline <path>' option for baseline-diff mode.")
  }

  return args
}

function runGit(command) {
  try {
    return execSync(command, {
      cwd: process.cwd(),
      stdio: ['ignore', 'pipe', 'pipe'],
      encoding: 'utf8',
    }).trim()
  } catch {
    return null
  }
}

function isResolvableGitRef(reference) {
  if (!reference) {
    return false
  }

  return runGit(`git rev-parse --verify ${reference}`) !== null
}

function resolveBaseRef(explicitBaseRef) {
  const candidates = [
    explicitBaseRef ? { source: '--base-ref', value: explicitBaseRef } : null,
    process.env.CHECK_COLORS_BASE_REF ? { source: 'CHECK_COLORS_BASE_REF', value: process.env.CHECK_COLORS_BASE_REF } : null,
    { source: 'origin/main', value: 'origin/main' },
    { source: 'merge-base HEAD HEAD~1', value: runGit('git merge-base HEAD HEAD~1') },
  ].filter(Boolean)

  for (const candidate of candidates) {
    if (candidate.source === 'merge-base HEAD HEAD~1') {
      if (isResolvableGitRef(candidate.value)) {
        return { baseRef: candidate.value, source: candidate.source }
      }
      continue
    }

    if (isResolvableGitRef(candidate.value)) {
      return { baseRef: candidate.value, source: candidate.source }
    }
  }

  const ordered = [
    '--base-ref',
    'CHECK_COLORS_BASE_REF',
    'origin/main',
    'git merge-base HEAD HEAD~1',
  ]
  throw new Error(
    `Failed to resolve base ref for changed mode. Tried in order: ${ordered.join(' -> ')}. ` +
    "Provide '--base-ref <git-ref>' or set CHECK_COLORS_BASE_REF.",
  )
}

function collectAllFiles(directoryPath) {
  if (!fs.existsSync(directoryPath)) {
    return []
  }

  const stat = fs.statSync(directoryPath)
  if (stat.isFile()) {
    return [directoryPath]
  }

  const result = []
  const stack = [directoryPath]
  while (stack.length > 0) {
    const current = stack.pop()
    for (const entry of fs.readdirSync(current, { withFileTypes: true })) {
      const fullPath = path.join(current, entry.name)
      if (entry.isDirectory()) {
        stack.push(fullPath)
      } else {
        result.push(fullPath)
      }
    }
  }

  return result
}

function resolveScopeFiles(scopePath) {
  if (!fs.existsSync(scopePath)) {
    throw new Error(`Scope path does not exist: ${scopePath}`)
  }

  const isDefaultScope = path.resolve(scopePath) === DEFAULT_SCOPE
  return collectAllFiles(scopePath)
    .map((absolutePath) => ({
      absolutePath,
      relativePath: normalizePath(path.relative(process.cwd(), absolutePath)),
    }))
    .filter(({ relativePath }) => !isDefaultScope || relativePath.startsWith('src/'))
    .filter(({ relativePath }) => !isAllowedPath(relativePath))
}

function normalizePath(filePath) {
  return filePath.split(path.sep).join('/')
}

function isAllowedPath(relativePath) {
  const normalized = normalizePath(relativePath)
  return ALLOWLIST_PATHS.some((prefix) => normalized.startsWith(prefix))
}

function loadAllowlistManifest() {
  if (!fs.existsSync(DEFAULT_ALLOWLIST_PATH)) {
    throw new Error(`Allowlist manifest not found: ${DEFAULT_ALLOWLIST_PATH}`)
  }

  const parsed = JSON.parse(fs.readFileSync(DEFAULT_ALLOWLIST_PATH, 'utf8'))
  if (!Array.isArray(parsed)) {
    throw new Error('Allowlist manifest must be an array.')
  }

  const now = Date.now()
  for (const [index, entry] of parsed.entries()) {
    for (const fieldName of ['path', 'pattern', 'reason', 'owner', 'approved_by', 'expires_at']) {
      if (!entry || typeof entry[fieldName] !== 'string' || entry[fieldName].trim() === '') {
        throw new Error(`Allowlist entry #${index + 1} is missing required field '${fieldName}'.`)
      }
    }

    const expiresAt = Date.parse(entry.expires_at)
    if (!Number.isFinite(expiresAt)) {
      throw new Error(`Allowlist entry #${index + 1} has invalid expires_at '${entry.expires_at}'.`)
    }

    if (expiresAt < now) {
      throw new Error(`Allowlist entry #${index + 1} expired at '${entry.expires_at}'.`)
    }
  }

  return parsed
}

function parseChangedLineNumbers(diffText) {
  const changed = new Set()
  const lines = diffText.split('\n')
  let currentFile = null
  for (const line of lines) {
    if (line.startsWith('+++ b/')) {
      currentFile = line.replace('+++ b/', '').trim()
      continue
    }

    if (line.startsWith('@@')) {
      if (!currentFile) {
        continue
      }

      const match = line.match(/\+(\d+)(?:,(\d+))?/)
      if (!match) {
        continue
      }

      const startLine = Number(match[1])
      const count = Number(match[2] ?? '1')
      if (count === 0) {
        continue
      }

      for (let offset = 0; offset < count; offset += 1) {
        changed.add(`${currentFile}:${startLine + offset}`)
      }
    }
  }

  return changed
}

function getChangedScope(scopePath, baseRef) {
  const normalizedScope = normalizePath(path.relative(process.cwd(), scopePath))
  const changedLines = new Set()

  const diffCommands = [
    `git diff --unified=0 ${baseRef}...HEAD -- ${normalizedScope}`,
    `git diff --unified=0 --cached -- ${normalizedScope}`,
    `git diff --unified=0 -- ${normalizedScope}`,
  ]

  for (const command of diffCommands) {
    const output = runGit(command)
    if (!output) {
      continue
    }

    const parsed = parseChangedLineNumbers(output)
    for (const key of parsed) {
      changedLines.add(key)
    }
  }

  const untracked = runGit(`git ls-files --others --exclude-standard -- ${normalizedScope}`)
  if (untracked) {
    for (const filePath of untracked.split('\n').map((value) => value.trim()).filter(Boolean)) {
      const absolute = path.resolve(process.cwd(), filePath)
      if (!fs.existsSync(absolute) || !fs.statSync(absolute).isFile()) {
        continue
      }
      const lineCount = fs.readFileSync(absolute, 'utf8').split('\n').length
      for (let lineNumber = 1; lineNumber <= lineCount; lineNumber += 1) {
        changedLines.add(`${filePath}:${lineNumber}`)
      }
    }
  }

  return { changedLines }
}

function indexToLine(content, index) {
  let line = 1
  for (let cursor = 0; cursor < index && cursor < content.length; cursor += 1) {
    if (content[cursor] === '\n') {
      line += 1
    }
  }
  return line
}

function getTokenDefinitionRanges(content, relativePath) {
  if (!relativePath.endsWith('src/index.css')) {
    return []
  }

  const ranges = []
  let cursor = 0
  while (cursor < content.length) {
    const start = content.indexOf(TOKEN_ALLOW_START, cursor)
    if (start === -1) {
      break
    }
    const end = content.indexOf(TOKEN_ALLOW_END, start + TOKEN_ALLOW_START.length)
    if (end === -1) {
      throw new Error(`Missing token allow end marker in ${relativePath}`)
    }
    ranges.push([start, end + TOKEN_ALLOW_END.length])
    cursor = end + TOKEN_ALLOW_END.length
  }

  return ranges
}

function isWithinRanges(index, ranges) {
  return ranges.some(([start, end]) => index >= start && index < end)
}

function isAllowedInlineValue(value) {
  const normalized = value.trim()
  return (
    normalized.includes('var(--') ||
    normalized.includes('hsl(var(--') ||
    normalized === 'transparent' ||
    normalized === 'inherit' ||
    normalized === 'currentColor'
  )
}

function shouldSkipViolationByAllowlist(relativePath, lineText, allowlistEntries) {
  return allowlistEntries.some((entry) => {
    const pathRegex = new RegExp(entry.path)
    const patternRegex = new RegExp(entry.pattern)
    return pathRegex.test(relativePath) && patternRegex.test(lineText)
  })
}

function scanFileForViolations(absolutePath, relativePath, allowlistEntries) {
  const content = fs.readFileSync(absolutePath, 'utf8')
  const violations = []
  const tokenRanges = getTokenDefinitionRanges(content, relativePath)
  const lines = content.split('\n')

  HARD_CODED_COLOR_PATTERN.lastIndex = 0
  for (const match of content.matchAll(HARD_CODED_COLOR_PATTERN)) {
    const matchText = match[0]
    const matchIndex = match.index ?? 0

    if (matchText.includes('var(--') || matchText.includes('hsl(var(--')) {
      continue
    }

    if (relativePath.endsWith('src/index.css') && isWithinRanges(matchIndex, tokenRanges)) {
      continue
    }

    const lineNumber = indexToLine(content, matchIndex)
    const lineText = lines[lineNumber - 1] ?? ''
    if (shouldSkipViolationByAllowlist(relativePath, lineText, allowlistEntries)) {
      continue
    }

    violations.push({
      file: relativePath,
      line: lineNumber,
      type: 'literal-color',
      match: matchText,
      message: `Hardcoded color literal '${matchText}'`,
    })
  }

  FORBIDDEN_UTILITY_CLASS_PATTERN.lastIndex = 0
  for (const match of content.matchAll(FORBIDDEN_UTILITY_CLASS_PATTERN)) {
    const matchText = match[0].trim()
    const matchIndex = match.index ?? 0
    const lineNumber = indexToLine(content, matchIndex)
    const lineText = lines[lineNumber - 1] ?? ''
    if (shouldSkipViolationByAllowlist(relativePath, lineText, allowlistEntries)) {
      continue
    }
    violations.push({
      file: relativePath,
      line: lineNumber,
      type: 'forbidden-utility',
      match: matchText,
      message: `Forbidden utility class '${matchText}'`,
    })
  }

  INLINE_COLOR_STYLE_PATTERN.lastIndex = 0
  for (const match of content.matchAll(INLINE_COLOR_STYLE_PATTERN)) {
    const propertyMatch = match[0]
    const value = match[2] ?? ''
    const matchIndex = match.index ?? 0
    if (isAllowedInlineValue(value)) {
      continue
    }

    const lineNumber = indexToLine(content, matchIndex)
    const lineText = lines[lineNumber - 1] ?? ''
    if (shouldSkipViolationByAllowlist(relativePath, lineText, allowlistEntries)) {
      continue
    }

    violations.push({
      file: relativePath,
      line: lineNumber,
      type: 'inline-style-literal',
      match: propertyMatch,
      message: `Inline style uses literal color value '${value}'`,
    })
  }

  return violations
}

function scanScope(scopePath, allowlistEntries) {
  const files = resolveScopeFiles(scopePath)
  const violations = []
  for (const file of files) {
    const currentViolations = scanFileForViolations(file.absolutePath, file.relativePath, allowlistEntries)
    violations.push(...currentViolations)
  }

  return {
    files,
    violations,
  }
}

function serializeViolation(violation) {
  return `${violation.file}:${violation.line}:${violation.type}:${violation.match}`
}

function loadBaseline(baselinePath) {
  const absolutePath = path.resolve(process.cwd(), baselinePath)
  if (!fs.existsSync(absolutePath)) {
    throw new Error(`Baseline file does not exist: ${absolutePath}`)
  }

  const parsed = JSON.parse(fs.readFileSync(absolutePath, 'utf8'))
  const entries = Array.isArray(parsed.entries) ? parsed.entries : []
  return new Set(entries.map((entry) => entry.key))
}

function writeBaseline(absolutePath, mode, violations) {
  const directoryPath = path.dirname(absolutePath)
  fs.mkdirSync(directoryPath, { recursive: true })
  const payload = {
    mode,
    generated_at: new Date().toISOString(),
    entries: violations
      .slice()
      .sort((left, right) => serializeViolation(left).localeCompare(serializeViolation(right)))
      .map((violation) => ({
        key: serializeViolation(violation),
        file: violation.file,
        line: violation.line,
        type: violation.type,
        match: violation.match,
      })),
  }
  fs.writeFileSync(absolutePath, `${JSON.stringify(payload, null, 2)}\n`, 'utf8')
}

function printViolations(violations, prefix = '') {
  for (const violation of violations) {
    const marker = prefix ? `${prefix}:` : ''
    console.error(`${marker}${violation.file}:${violation.line} [${violation.type}] ${violation.message}`)
  }
}

function main() {
  const options = parseArgs(process.argv.slice(2))
  const allowlistEntries = loadAllowlistManifest()
  const { files: scannedFiles, violations: allViolations } = scanScope(options.scope, allowlistEntries)

  if (options.writeBaselinePath) {
    writeBaseline(options.writeBaselinePath, options.mode, allViolations)
    console.log(`Baseline written to ${options.writeBaselinePath} (${allViolations.length} entries).`)
    return
  }

  if (options.mode === 'strict') {
    if (scannedFiles.length === 0) {
      throw new Error(`Strict mode requires at least one scannable file in scope '${options.scope}'.`)
    }

    if (allViolations.length > 0) {
      printViolations(allViolations)
      process.exitCode = 1
      return
    }
    console.log('check:colors strict passed with 0 violations.')
    return
  }

  if (options.mode === 'baseline-diff') {
    const baselineSet = loadBaseline(options.baselinePath)
    const newViolations = allViolations.filter((violation) => !baselineSet.has(serializeViolation(violation)))
    if (newViolations.length > 0) {
      printViolations(newViolations, 'NEW')
      process.exitCode = 1
      return
    }
    console.log(`check:colors baseline-diff passed (${allViolations.length} current violations, 0 new).`)
    return
  }

  if (options.mode === 'changed') {
    const resolved = resolveBaseRef(options.baseRef)
    const { changedLines } = getChangedScope(options.scope, resolved.baseRef)
    const changedViolations = allViolations.filter((violation) =>
      changedLines.has(`${violation.file}:${violation.line}`),
    )

    if (changedViolations.length > 0) {
      printViolations(changedViolations, 'CHANGED')
      process.exitCode = 1
      return
    }

    console.log(`check:colors changed passed with 0 violations (base ref source: ${resolved.source}).`)
    return
  }
}

try {
  main()
} catch (error) {
  console.error(`[check:colors] ${error instanceof Error ? error.message : String(error)}`)
  process.exitCode = 1
}
