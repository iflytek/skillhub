import { createHash } from 'node:crypto'
import { mkdir, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { describe, expect, test } from 'bun:test'
import { createTempHome } from '../../helpers/temp-env'
import { diffSkillFiles, snapshotSkillDirectory } from '../../../src/services/skill-fingerprint'

describe('skill fingerprint', () => {
  test('matches the server fingerprint for mixed-case package paths', async () => {
    const env = await createTempHome()
    const skillDir = join(env.cwd, 'mixed-case-paths')
    const skillContent = '# Mixed-case paths\n'
    const referenceContent = 'Reference\n'
    await mkdir(join(skillDir, 'references'), { recursive: true })
    await writeFile(join(skillDir, 'SKILL.md'), skillContent)
    await writeFile(join(skillDir, 'references', 'guide.md'), referenceContent)

    const skillHash = createHash('sha256').update(skillContent).digest('hex')
    const referenceHash = createHash('sha256').update(referenceContent).digest('hex')
    const serverFingerprint = createHash('sha256')
      .update(`SKILL.md:${skillHash}\n`)
      .update(`references/guide.md:${referenceHash}\n`)
      .digest('hex')

    expect((await snapshotSkillDirectory(skillDir)).fingerprint).toBe(`sha256:${serverFingerprint}`)
  })

  test('ignores SkillHub metadata and reports changed files', async () => {
    const env = await createTempHome()
    const skillDir = join(env.cwd, 'demo')
    await mkdir(join(skillDir, '.skillhub'), { recursive: true })
    await writeFile(join(skillDir, 'SKILL.md'), '# one\n')
    await writeFile(join(skillDir, '.skillhub', 'metadata.json'), '{"ignored":true}')

    const baseline = await snapshotSkillDirectory(skillDir)
    await writeFile(join(skillDir, '.skillhub', 'metadata.json'), '{"ignored":false}')
    expect((await snapshotSkillDirectory(skillDir)).fingerprint).toBe(baseline.fingerprint)

    await writeFile(join(skillDir, 'SKILL.md'), '# two\n')
    const current = await snapshotSkillDirectory(skillDir)
    expect(diffSkillFiles(baseline.files, current.files)).toEqual(['SKILL.md'])
  })
})
