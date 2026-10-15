import { join } from 'node:path'
import { openNativeRuntime } from '../../src/integrations/native-runtime'
import { CliError } from '../../src/shared/errors'

const [root, operationId, ownerId] = process.argv.slice(2)
if (!root || !operationId || !ownerId) throw new Error('Missing native recovery fixture arguments')
const runtime = await openNativeRuntime({ stateDirectory: join(root, 'operations'), inventoryHome: join(root, 'home') })
try {
  const result = await runtime.recover(operationId, ownerId, [join(root, 'skills/writer')], async () => {})
  process.stdout.write(JSON.stringify({ outcome: result.outcome }))
} catch (error) {
  if (!(error instanceof CliError) || error.details.reason !== 'TARGET_BUSY') throw error
  process.stdout.write(JSON.stringify({ outcome: 'busy' }))
}
