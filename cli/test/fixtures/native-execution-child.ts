import { join } from 'node:path'
import { openNativeRuntime } from '../../src/integrations/native-runtime'
import type { NativeInstallExecution } from '../../src/integrations/native-contract'

const [root, input] = process.argv.slice(2)
if (!root || !input) throw new Error('Missing native execution fixture arguments')
const runtime = await openNativeRuntime({ stateDirectory: join(root, 'operations'), inventoryHome: join(root, 'home') })
const request = JSON.parse(input) as Omit<NativeInstallExecution, 'authorize' | 'validateStaged'>
await runtime.executeInstall({ ...request, validateStaged: async () => {}, authorize: async () => {
  process.stdout.write('ready')
  await new Promise(() => { setInterval(() => {}, 1000) })
} })
