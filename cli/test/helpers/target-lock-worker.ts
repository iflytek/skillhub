import { acquireSkillTargetLock } from '../../src/services/skill-target-lock'
import { CliError } from '../../src/shared/errors'

const [rootDir, slug, holdMilliseconds] = process.argv.slice(2)
if (!rootDir || !slug || !holdMilliseconds) process.exit(5)

try {
  const release = await acquireSkillTargetLock(rootDir, slug)
  process.stdout.write('acquired\n')
  await new Promise(resolve => setTimeout(resolve, Number(holdMilliseconds)))
  await release()
  process.exit(0)
} catch (error) {
  if (error instanceof CliError) {
    process.stderr.write(`${error.message}\n`)
    process.exit(error.exitCode)
  }
  throw error
}
