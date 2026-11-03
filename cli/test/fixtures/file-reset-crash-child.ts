import { readFile, unlink, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import type { NativeFileResetExecution } from '../../src/integrations/native-contract'

const [home, operationId, boundary, marker] = process.argv.slice(2)
if (!home || !operationId || !marker || !['prepared', 'old-moved', 'published', 'committed', 'cleanup'].includes(boundary!)) throw Error('Invalid crash fixture arguments')
const pause = async () => {
  await writeFile(marker, boundary!, { flag: 'wx', mode: 0o600 })
  await new Promise(() => { setInterval(() => {}, 1000) })
}
// Only this owned operation's cleanup is intercepted. Production has no crash flag or callback.
const backup = `${join(home, 'skills', 'writer')}.skillhub-backup-${operationId}`
const { InventoryStore } = await import('../../src/stores/inventory-store')
const { OperationStore } = await import('../../src/integrations/operation-store')
const { FileResetStore } = await import('../../src/integrations/native-file-reset')
const state = join(home, 'operations'), inventory = new InventoryStore(home), operations = await OperationStore.open(state)
class CrashStore extends FileResetStore {
  protected override async removeBackup(path: string): Promise<void> {
    if (boundary === 'cleanup' && path === backup) {
      await unlink(join(path, 'resource.txt'))
      await pause()
    }
    await super.removeBackup(path)
  }
}
const store = new CrashStore(state, operations.hostInstanceId, inventory)
const save = store.save.bind(store)
store.save = async record => {
  await save(record)
  if (boundary !== 'cleanup' && record.intent?.stage === boundary) await pause()
}
const input = JSON.parse(await readFile(join(home, 'reset-input.json'), 'utf8')) as Omit<NativeFileResetExecution, 'authorize'>
await store.execute({ ...input, authorize: async () => {} })
throw Error('Crash boundary was not reached')
