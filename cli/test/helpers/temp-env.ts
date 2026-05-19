import { mkdtemp } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

export async function createTempHome() {
  const home = await mkdtemp(join(tmpdir(), 'skillhub-test-home-'))
  const cwd = await mkdtemp(join(tmpdir(), 'skillhub-test-cwd-'))
  return { home, cwd }
}
