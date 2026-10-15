import { expect, test } from 'bun:test'
import { mkdtemp, readdir, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

test('native integration entry bundles and imports under Node without starting the CLI', async () => {
  const root = await mkdtemp(join(tmpdir(), 'skillhub-integration-'))
  const loaded: string[] = []
  try {
    const built = await Bun.build({
      entrypoints: [fileURLToPath(new URL('../../../src/integrations/local-skills.ts', import.meta.url))],
      target: 'node', format: 'esm', outdir: root,
      plugins: [{ name: 'record-import-closure', setup(build) {
        build.onLoad({ filter: /\.[cm]?[jt]s$/ }, args => { loaded.push(args.path); return undefined })
      } }]
    })
    expect(built.success).toBe(true)
    expect(loaded.some(path => /\/commands\/|\/src\/index\.ts$|\/node_modules\/(prompts|cac)\//.test(path))).toBe(false)
    const entry = built.outputs[0]!.path
    const child = Bun.spawn(['node', '--input-type=module', '-e',
      `const core = await import(${JSON.stringify(pathToFileURL(entry).href)}); if (typeof core.installSkill !== 'function' || typeof core.orderSkillTargetLocks !== 'function') throw Error('missing core'); console.log('core imported');`
    ], { cwd: root, stdout: 'pipe', stderr: 'pipe' })
    expect(await child.exited).toBe(0)
    expect(await new Response(child.stdout).text()).toBe('core imported\n')
    expect(await new Response(child.stderr).text()).toBe('')
    expect((await readdir(root)).sort()).toEqual(['local-skills.js'])
  } finally {
    await rm(root, { recursive: true, force: true })
  }
})
