import { writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { InventoryStore } from '../../src/stores/inventory-store'
import { OperationStore } from '../../src/integrations/operation-store'
import { inspectToggle, executeNativeToggle } from '../../src/integrations/native-toggle'

const [home, operationId, boundary, readyFile] = process.argv.slice(2)
if (!home || !operationId || !readyFile || !['intent', 'linked', 'moved', 'restore-linked', 'committed'].includes(boundary!)) throw new Error('Invalid fixture arguments')
const inventory = new InventoryStore(home)
const operations = await OperationStore.open(join(home, 'operations'))
const baseline = await inspectToggle(inventory, { registry: 'https://registry.example', namespace: 'demo', slug: 'writer', version: '1.0.0' },
  { root: join(home, 'skills'), scope: 'user' }, 'disabled')
if (baseline.status !== 'ready') throw new Error('Fixture baseline unavailable')
const append = operations.recordToggle.bind(operations)
operations.recordToggle = async (...args) => {
  if (boundary === 'restore-linked' && args[3].stage === 'committed') throw new Error('Injected commit rejection')
  const saved = await append(...args)
  if (args[3].stage === boundary) {
    await writeFile(readyFile!, boundary, { flag: 'wx' })
    // The parent force-terminates this owned process using the native platform API.
    await new Promise(() => { setInterval(() => {}, 1000) })
  }
  return saved
}
await executeNativeToggle(operations, inventory, { operationId, ownerId: 'owner', baseline, authorize: async () => {} })
throw new Error('Crash boundary was not reached')
