#!/usr/bin/env node

import { readdir, readFile } from 'node:fs/promises'
import path from 'node:path'
import process from 'node:process'

const E2E_ROOT = path.resolve(process.cwd(), 'e2e')
const SOURCE_EXTENSIONS = new Set(['.ts', '.tsx', '.js', '.jsx', '.mjs', '.cjs'])
const WAIT_FOR_TIMEOUT_PATTERN = /\bwaitForTimeout\s*\(/

async function collectSourceFiles(dir) {
  const entries = await readdir(dir, { withFileTypes: true })
  const files = []

  for (const entry of entries) {
    const absolutePath = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      files.push(...await collectSourceFiles(absolutePath))
      continue
    }

    if (SOURCE_EXTENSIONS.has(path.extname(entry.name))) {
      files.push(absolutePath)
    }
  }

  return files
}

function collectViolations(content, relativePath) {
  const violations = []
  const lines = content.split(/\r?\n/)

  for (let index = 0; index < lines.length; index += 1) {
    if (!WAIT_FOR_TIMEOUT_PATTERN.test(lines[index])) {
      continue
    }

    violations.push(`${relativePath}:${index + 1}: ${lines[index].trim()}`)
  }

  return violations
}

async function run() {
  const files = await collectSourceFiles(E2E_ROOT)
  const violations = []

  for (const absolutePath of files) {
    const fileContent = await readFile(absolutePath, 'utf8')
    const relativePath = path.relative(process.cwd(), absolutePath)
    violations.push(...collectViolations(fileContent, relativePath))
  }

  if (violations.length > 0) {
    console.error('Policy check failed: waitForTimeout is forbidden in web/e2e.')
    console.error('Replace hard waits with condition-based waits.')
    for (const violation of violations) {
      console.error(` - ${violation}`)
    }
    process.exitCode = 1
    return
  }

  console.log('Policy check passed: no waitForTimeout found in web/e2e.')
}

run().catch((error) => {
  console.error('Policy check failed to run:', error)
  process.exitCode = 1
})
