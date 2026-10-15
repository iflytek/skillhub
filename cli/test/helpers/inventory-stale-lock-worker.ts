import { createRequire } from 'node:module'
import type { Stats } from 'node:fs'
import { access, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { InventoryStore } from '../../src/stores/inventory-store'

const [home, id] = process.argv.slice(2)
if (!home || !id) throw new Error('Expected isolated home and contender id')
const require = createRequire(import.meta.url)
// Delay only the stale stat callback; every filesystem operation remains real.
const fs = createRequire(require.resolve('proper-lockfile'))('graceful-fs') as {
  stat(path: string, callback: (error: NodeJS.ErrnoException | null, stat: Stats) => void): void
}
const stat = fs.stat.bind(fs)
const store = new InventoryStore(home)
async function waitFor(name: string): Promise<void> {
  const deadline = Date.now() + 10000
  for (;;) {
    try { await access(join(home!, name)); return }
    catch (error) { if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error }
    if (Date.now() >= deadline) throw new Error(`Timed out waiting for ${name}`)
    await Bun.sleep(5)
  }
}
let paused = false
fs.stat = (path, callback) => stat(path, (error, info) => {
  if (path !== `${store.path}.lock` || error) { callback(error, info); return }
  if (!paused && info.mtimeMs < Date.now() - 30000) {
    paused = true
    void writeFile(join(home, `${id}-stale`), '').then(() => waitFor(`${id}-resume`)).then(
      () => callback(null, info), failure => callback(failure, info))
  } else if (!paused) {
    void writeFile(join(home, `${id}-fresh`), '').then(() => callback(null, info), failure => callback(failure, info))
  } else callback(null, info)
})
await store.mutateAtomic(inventory => {
  inventory.items.push({ registry: 'https://registry.example', namespace: 'global', slug: id, version: '1.0.0', targets: [] })
}, async () => {
  await writeFile(join(home, `${id}-entered`), '')
  await waitFor(`${id}-commit`)
})
