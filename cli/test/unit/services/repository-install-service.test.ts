import { expect, test } from 'bun:test'
import { installRepositoryPlan } from '../../../src/services/repository-install-service'
import { CliError } from '../../../src/shared/errors'
import type { RepositoryInstallPlan } from '../../../src/clients/skillhub-client'

const plan: RepositoryInstallPlan = { repositoryId: '42', owner: 'fixture', name: 'repo', members: ['alpha', 'beta'].map((slug, i) => ({
  skillId: i + 1, namespace: 'github', slug, displayName: slug, version: '1.0.0', versionId: i + 11,
  fingerprint: 'sha256:' + 'a'.repeat(64), downloadUrl: `/api/cli/v1/skills/github/${slug}/versions/1.0.0/download`
})) }

test('known commit-unknown recovery preserves safe paths and stops later writes without leaking arbitrary details', async () => {
  let attempts = 0
  const result = await installRepositoryPlan(plan, { registry: 'https://registry.invalid', targets: [] }, {
    installSkill: async () => {
      attempts++
      throw new CliError('installation commit outcome is unknown; files retained for recovery', 4, {
        reason: 'INSTALL_COMMIT_UNKNOWN', recovery: 'unknown', targets: ['/safe/alpha'],
        stagingPaths: ['/safe/.alpha-stage'], retainedBackups: ['/safe/.alpha-backup'],
        Authorization: 'fake-secret-marker', cause: 'fake-secret-marker', next: 'fake-secret-marker'
      })
    }
  })
  expect(attempts).toBe(1)
  expect(result.summary).toEqual({ installed: 0, skipped: 1, failed: 1 })
  expect(result.items[0]?.recovery).toMatchObject({ status: 'unknown', targets: ['/safe/alpha'], retainedBackups: ['/safe/.alpha-backup'] })
  expect(JSON.stringify(result)).not.toContain('fake-secret-marker')
  expect(result.items[1]?.reason).toContain('no further writes')
})

test('rollback recovery retains backup locations but excludes raw rollback errors', async () => {
  const result = await installRepositoryPlan(plan, { registry: 'https://registry.invalid', targets: [] }, {
    installSkill: async () => { throw new CliError('installation failed and rollback was incomplete', 4, {
      retainedBackups: ['/safe/backup'], originalError: 'fake-secret-marker', rollbackFailures: [{ path: '/safe/alpha', error: 'fake-secret-marker' }]
    }) }
  })
  expect(result.items[0]?.recovery).toMatchObject({ status: 'rollback-incomplete', retainedBackups: ['/safe/backup'] })
  expect(JSON.stringify(result)).not.toContain('fake-secret-marker')
})
