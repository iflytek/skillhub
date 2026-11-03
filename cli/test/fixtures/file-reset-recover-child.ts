import { join } from 'node:path'
import { openNativeRuntime } from '../../src/integrations/native-runtime'
const [home, operationId] = process.argv.slice(2)
if (!home || !operationId) throw Error('Invalid reset recovery fixture arguments')
const childRuntime = { executable: process.execPath, version: process.version, platform: process.platform }
const runtime = await openNativeRuntime({ stateDirectory: join(home, 'operations'), inventoryHome: home })
try {
  const result = await runtime.recover(operationId, 'owner', [join(home, 'skills', 'writer')], async () => {})
  process.stdout.write(JSON.stringify({ result, childRuntime, pending: await runtime.listPending() }))
} catch (error) {
  process.stdout.write(JSON.stringify({ error: error instanceof Error ? error.message : 'unknown', childRuntime, pending: await runtime.listPending() }))
}
