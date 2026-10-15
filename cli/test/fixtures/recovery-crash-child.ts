import { readFile } from 'node:fs/promises'
import { join } from 'node:path'
import { recoverInstallation } from '../../src/integrations/install-recovery'
import { OperationStore } from '../../src/integrations/operation-store'
import { InventoryStore } from '../../src/stores/inventory-store'

const [root, boundary] = process.argv.slice(2)
if (!root || !boundary) throw new Error('Missing recovery crash arguments')
const fixture = JSON.parse(await readFile(join(root, 'fixture.json'), 'utf8')) as { operationId: string; ownerId: string; trustedTargetPaths: string[] }
const operations = await OperationStore.open(join(root, 'operations'))
const record = operations.recordRecoveryAction.bind(operations)
operations.recordRecoveryAction = async (...args) => {
  const [, , , action, completed] = args
  if (completed && action.kind === boundary) {
    process.kill(process.pid, 'SIGKILL')
    await new Promise(() => {})
  }
  return record(...args)
}
await recoverInstallation({ ...fixture, operations, inventory: new InventoryStore(join(root, 'home')) })
throw new Error('Recovery crash boundary was not reached')
