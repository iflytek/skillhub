#!/usr/bin/env node

import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'

export const MAX_SHADOW_ALPHA = 0.24
export const MAX_GLOW_ALPHA = 0.18
export const MAX_BLUR_PX = 16
export const MIN_DURATION_MS = 100
export const MAX_DURATION_MS = 300

const FORBIDDEN_INFINITE_CLASSES = ['animate-float', 'animate-bounce', 'animate-ping']
const BLUR_SCALE_TO_PX = {
  none: 0,
  sm: 4,
  md: 12,
  lg: 16,
  xl: 24,
  '2xl': 40,
  '3xl': 64,
}

const DEFAULT_SCAN_PATHS = ['src']

function normalizePath(filePath) {
  return filePath.split(path.sep).join('/')
}

function parseArgs(argv) {
  const options = {
    scope: null,
  }

  for (let index = 0; index < argv.length; index += 1) {
    if (argv[index] === '--scope') {
      options.scope = argv[index + 1] ?? null
      index += 1
    }
  }

  return options
}

function collectFiles(startPath) {
  const resolvedPath = path.resolve(process.cwd(), startPath)
  if (!fs.existsSync(resolvedPath)) {
    return []
  }

  const stat = fs.statSync(resolvedPath)
  if (stat.isFile()) {
    return [resolvedPath]
  }

  const queue = [resolvedPath]
  const files = []
  while (queue.length > 0) {
    const current = queue.pop()
    for (const entry of fs.readdirSync(current, { withFileTypes: true })) {
      const fullPath = path.join(current, entry.name)
      if (entry.isDirectory()) {
        queue.push(fullPath)
      } else if (entry.isFile()) {
        files.push(fullPath)
      }
    }
  }

  return files
}

function isScannableFile(filePath) {
  const normalized = normalizePath(filePath)
  if (!/\.(ts|tsx|css)$/.test(normalized)) {
    return false
  }

  if (normalized.includes('.test.') || normalized.includes('.spec.')) {
    return false
  }

  if (normalized.includes('/__tests__/')) {
    return false
  }

  return true
}

function addViolation(violations, filePath, lineNumber, rule, message) {
  violations.push({
    file: normalizePath(path.relative(process.cwd(), filePath)),
    line: lineNumber,
    rule,
    message,
  })
}

function parseAlphaPercent(token) {
  const value = Number.parseInt(token, 10)
  if (!Number.isFinite(value)) {
    return null
  }

  return value / 100
}

function scanLineForViolations(violations, filePath, lineText, lineNumber) {
  for (const forbiddenClass of FORBIDDEN_INFINITE_CLASSES) {
    if (lineText.includes(forbiddenClass)) {
      addViolation(
        violations,
        filePath,
        lineNumber,
        'forbidden-infinite-motion',
        `Found forbidden class '${forbiddenClass}'.`,
      )
    }
  }

  for (const match of lineText.matchAll(/\bduration-(\d{2,4})\b/g)) {
    const duration = Number.parseInt(match[1], 10)
    if (duration < MIN_DURATION_MS || duration > MAX_DURATION_MS) {
      addViolation(
        violations,
        filePath,
        lineNumber,
        'transition-duration-range',
        `Duration ${duration}ms is outside ${MIN_DURATION_MS}-${MAX_DURATION_MS}ms.`,
      )
    }
  }

  for (const match of lineText.matchAll(/\bshadow-[^\s"'`]*\/(\d{1,3})\b/g)) {
    const alpha = parseAlphaPercent(match[1])
    if (alpha !== null && alpha > MAX_SHADOW_ALPHA) {
      addViolation(
        violations,
        filePath,
        lineNumber,
        'shadow-alpha-cap',
        `Shadow alpha ${alpha.toFixed(2)} exceeds ${MAX_SHADOW_ALPHA.toFixed(2)}.`,
      )
    }
  }

  for (const match of lineText.matchAll(/\bglow-[^\s"'`]*\/(\d{1,3})\b/g)) {
    const alpha = parseAlphaPercent(match[1])
    if (alpha !== null && alpha > MAX_GLOW_ALPHA) {
      addViolation(
        violations,
        filePath,
        lineNumber,
        'glow-alpha-cap',
        `Glow alpha ${alpha.toFixed(2)} exceeds ${MAX_GLOW_ALPHA.toFixed(2)}.`,
      )
    }
  }

  for (const match of lineText.matchAll(/\bblur-(none|sm|md|lg|xl|2xl|3xl)\b/g)) {
    const blurToken = match[1]
    const blurPx = BLUR_SCALE_TO_PX[blurToken] ?? 0
    if (blurPx > MAX_BLUR_PX) {
      addViolation(
        violations,
        filePath,
        lineNumber,
        'blur-cap',
        `Blur ${blurToken} (${blurPx}px) exceeds ${MAX_BLUR_PX}px.`,
      )
    }
  }

  for (const match of lineText.matchAll(/\bblur-\[(\d+(?:\.\d+)?)px\]/g)) {
    const blurPx = Number.parseFloat(match[1])
    if (Number.isFinite(blurPx) && blurPx > MAX_BLUR_PX) {
      addViolation(
        violations,
        filePath,
        lineNumber,
        'blur-cap',
        `Blur ${blurPx}px exceeds ${MAX_BLUR_PX}px.`,
      )
    }
  }
}

function scanFiles(filePaths) {
  const violations = []

  for (const filePath of filePaths) {
    const content = fs.readFileSync(filePath, 'utf8')
    const lines = content.split('\n')
    for (let index = 0; index < lines.length; index += 1) {
      scanLineForViolations(violations, filePath, lines[index], index + 1)
    }
  }

  return violations
}

function main() {
  const options = parseArgs(process.argv.slice(2))
  const scopes = options.scope ? [options.scope] : DEFAULT_SCAN_PATHS
  const uniqueFiles = [...new Set(scopes.flatMap((scope) => collectFiles(scope).filter(isScannableFile)))]
  const violations = scanFiles(uniqueFiles)

  if (violations.length > 0) {
    for (const violation of violations) {
      console.error(`${violation.file}:${violation.line} [${violation.rule}] ${violation.message}`)
    }
    process.exitCode = 1
    return
  }

  console.log(`check:motion passed (${uniqueFiles.length} files checked).`)
}

try {
  main()
} catch (error) {
  console.error(`[check:motion] ${error instanceof Error ? error.message : String(error)}`)
  process.exitCode = 1
}
