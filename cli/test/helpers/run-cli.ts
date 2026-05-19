import { existsSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

/**
 * Spawn the CLI with a clean environment so host-shell exports like
 * SKILLHUB_REGISTRY or SKILLHUB_TOKEN don't leak into the test process and
 * silently override stored credentials/config. Tests can still inject any
 * SKILLHUB_* variable explicitly via the `env` argument.
 */
function sanitizeProcessEnv(): Record<string, string> {
  const cleaned: Record<string, string> = {}
  for (const [key, value] of Object.entries(process.env)) {
    if (typeof value !== 'string') continue
    if (key.startsWith('SKILLHUB_')) continue
    cleaned[key] = value
  }
  return cleaned
}

export interface RunCliOptions {
  /**
   * Working directory for the child process. Defaults to the CLI package root
   * so `bun src/index.ts` resolves. Pass a temp dir when the command scans
   * cwd (e.g. `doctor`) so tests don't leak fixtures into the repo tree.
   */
  cwd?: string
}

export async function runCli(
  args: string[],
  env: Record<string, string> = {},
  options: RunCliOptions = {}
) {
  // Use Bun.which() to find bun in PATH, but verify it exists
  const whichBun = await Bun.which('bun')
  const bunPath = (whichBun && existsSync(whichBun)) ? whichBun : process.execPath

  const cliRoot = fileURLToPath(new URL('../../', import.meta.url))
  const entry = `${cliRoot}src/index.ts`

  const proc = Bun.spawn({
    cmd: [bunPath, entry, ...args],
    cwd: options.cwd ?? cliRoot,
    env: { ...sanitizeProcessEnv(), ...env },
    stdout: 'pipe',
    stderr: 'pipe'
  })
  const [stdout, stderr, exitCode] = await Promise.all([
    new Response(proc.stdout).text(),
    new Response(proc.stderr).text(),
    proc.exited
  ])
  return { stdout: stdout.trim(), stderr: stderr.trim(), exitCode }
}
