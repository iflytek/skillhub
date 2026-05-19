import { describe, expect, test } from 'bun:test'
import { UpdateService } from '../../../src/services/update-service'

describe('UpdateService', () => {
  test('npx install mode only suggests next command', async () => {
    const service = new UpdateService({
      currentVersion: '0.1.0',
      latestVersion: async () => '0.2.0',
      detectInstallMode: () => 'npx',
      run: async () => { throw new Error('should not run') }
    })

    const result = await service.update({ checkOnly: false })

    expect(result.updated).toBe(false)
    expect(result.next).toContain('npx @astron-team/skillhub@latest')
  })

  test('npm-global install mode runs npm update', async () => {
    let executed: readonly string[] = []
    const service = new UpdateService({
      currentVersion: '0.1.0',
      latestVersion: async () => '0.2.0',
      detectInstallMode: () => 'npm-global',
      run: async (cmd) => { executed = cmd; return { success: true, output: '' } }
    })

    const result = await service.update({ checkOnly: false })

    expect(result.updated).toBe(true)
    expect(executed).toEqual(['npm', 'install', '-g', '@astron-team/skillhub@latest'])
  })

  test('bun-global install mode runs bun update', async () => {
    let executed: readonly string[] = []
    const service = new UpdateService({
      currentVersion: '0.1.0',
      latestVersion: async () => '0.2.0',
      detectInstallMode: () => 'bun-global',
      run: async (cmd) => { executed = cmd; return { success: true, output: '' } }
    })

    const result = await service.update({ checkOnly: false })

    expect(result.updated).toBe(true)
    expect(executed).toEqual(['bun', 'add', '-g', '@astron-team/skillhub@latest'])
  })

  test('unknown install mode only suggests next command', async () => {
    const service = new UpdateService({
      currentVersion: '0.1.0',
      latestVersion: async () => '0.2.0',
      detectInstallMode: () => 'unknown',
      run: async () => { throw new Error('should not run') }
    })

    const result = await service.update({ checkOnly: false })

    expect(result.updated).toBe(false)
    expect(result.next).toBeTruthy()
  })

  test('checkOnly mode does not execute update', async () => {
    const service = new UpdateService({
      currentVersion: '0.1.0',
      latestVersion: async () => '0.2.0',
      detectInstallMode: () => 'npm-global',
      run: async () => { throw new Error('should not run') }
    })

    const result = await service.update({ checkOnly: true })

    expect(result.updated).toBe(false)
    expect(result.available).toBe(true)
  })

  test('no update available', async () => {
    const service = new UpdateService({
      currentVersion: '0.2.0',
      latestVersion: async () => '0.2.0',
      detectInstallMode: () => 'npm-global',
      run: async () => { throw new Error('should not run') }
    })

    const result = await service.update({ checkOnly: false })

    expect(result.updated).toBe(false)
    expect(result.available).toBe(false)
  })
})
