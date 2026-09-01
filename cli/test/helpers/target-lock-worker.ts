import { access, writeFile } from 'node:fs/promises'
import { acquireSkillTargetLock } from '../../src/services/skill-target-lock'
import { CliError } from '../../src/shared/errors'

const [rootDir, slug, readyPath, startPath, acquiredPath, releasePath] = process.argv.slice(2)
if (!rootDir || !slug || !readyPath || !startPath || !acquiredPath || !releasePath) process.exit(5)

try {
  await writeFile(readyPath, 'ready')
  let startRequested = false
  while (!startRequested) {
    try {
      await access(startPath)
      startRequested = true
    } catch {
      await new Promise(resolve => setTimeout(resolve, 10))
    }
  }
  const release = await acquireSkillTargetLock(rootDir, slug)
  await writeFile(acquiredPath, 'acquired')
  process.stdout.write('acquired\n')
  let releaseRequested = false
  while (!releaseRequested) {
    try {
      await access(releasePath)
      releaseRequested = true
    } catch {
      await new Promise(resolve => setTimeout(resolve, 10))
    }
  }
  await release()
  process.exit(0)
} catch (error) {
  if (error instanceof CliError) {
    process.stderr.write(`${error.message}\n`)
    process.exit(error.exitCode)
  }
  throw error
}
