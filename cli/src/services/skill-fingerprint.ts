import { createHash } from 'node:crypto'
import { readdir, readFile } from 'node:fs/promises'
import { join, relative } from 'node:path'

export interface SkillSnapshot {
  fingerprint: string
  files: Record<string, string>
}

export async function snapshotSkillDirectory(skillDir: string): Promise<SkillSnapshot> {
  const paths = await listSkillFiles(skillDir)
  const files: Record<string, string> = {}
  const aggregate = createHash('sha256')

  for (const path of paths) {
    const content = await readFile(join(skillDir, path))
    const fileHash = createHash('sha256').update(content).digest('hex')
    files[path] = fileHash
    aggregate.update(`${path}:${fileHash}\n`, 'utf8')
  }

  return { fingerprint: `sha256:${aggregate.digest('hex')}`, files }
}

export function diffSkillFiles(
  baseline: Record<string, string> | undefined,
  current: Record<string, string>
): string[] {
  if (!baseline) return []
  const paths = new Set([...Object.keys(baseline), ...Object.keys(current)])
  return [...paths]
    .filter(path => baseline[path] !== current[path])
    .sort((left, right) => left.localeCompare(right))
}

async function listSkillFiles(root: string): Promise<string[]> {
  const files: string[] = []

  async function walk(current: string): Promise<void> {
    const entries = await readdir(current, { withFileTypes: true })
    for (const entry of entries) {
      if (entry.name === '.skillhub') continue
      const absolute = join(current, entry.name)
      if (entry.isDirectory()) {
        await walk(absolute)
      } else if (entry.isFile()) {
        files.push(relative(root, absolute).split('\\').join('/'))
      }
    }
  }

  await walk(root)
  return files.sort()
}
