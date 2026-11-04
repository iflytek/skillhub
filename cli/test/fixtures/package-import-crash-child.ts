import { randomUUID, createHash } from 'node:crypto'
import { writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { zipSync } from 'fflate'
import { openNativeRuntime } from '../../src/integrations/native-runtime'
import { OperationStore } from '../../src/integrations/operation-store'
import { createInstallTransactionObserver } from '../../src/integrations/transaction-journal'
import { installSkill } from '../../src/services/install-service'
import { InventoryStore } from '../../src/stores/inventory-store'

const [root, boundary] = process.argv.slice(2)
if (!root || !boundary) throw new Error('Missing fixture arguments')
const stateDirectory = join(root,'operations')
const runtime = await openNativeRuntime({stateDirectory, inventoryHome:join(root,'shared')})
const content = '---\nname: demo\ndescription: Local crash fixture\n---\nhello'
const archive = zipSync({'SKILL.md':new TextEncoder().encode(content),'asset.bin':new Uint8Array([0,255])})
const fingerprint = 'sha256:' + createHash('sha256').update(['SKILL.md','asset.bin'].map(path => `${path}:${createHash('sha256').update(path === 'SKILL.md' ? new TextEncoder().encode(content) : new Uint8Array([0,255])).digest('hex')}\n`).join('')).digest('hex')
const target = join(root,'skills/demo')
const operation = (await runtime.acceptInstall({hostInstanceId:runtime.hostInstanceId,ownerId:'session',requestKey:randomUUID(),mode:'new',parameters:{fixture:boundary}}, async()=>({namespace:'local',slug:'demo',version:'local',targetLabel:'Project',targetPaths:[target],context:{sourceIdentity:'local:import',sourceLabel:'Local',sourceRevision:'r',hostLabel:'Host',initiatedFrom:'plaza'}})))!
const operations = await OperationStore.open(stateDirectory)
const accepted = (await operations.get(operation.operationId,'session'))!
await operations.update(accepted.operationId,accepted.ownerId,accepted.revision,{...accepted.operation.status,phase:'staging'})
await writeFile(join(root,'fixture.json'),JSON.stringify({operationId:operation.operationId,target}))
const observer = await createInstallTransactionObserver(operations,operation.operationId,'session')
const stop = async (point: string) => { if (point === boundary) {process.kill(process.pid,'SIGKILL'); await new Promise(()=>{})} }
await installSkill({registry:'local:import',namespace:'local',slug:'demo',version:'local',force:false,packageBytes:archive,metadataSource:'local-import',inventoryStore:new InventoryStore(join(stateDirectory,'local-imports')),targets:[{agent:'dsh',rootDir:join(root,'skills'),scope:'project',source:'explicit'}],resolved:{namespace:'local',slug:'demo',version:'local',versionId:1,fingerprint,downloadUrl:''},transactionObserver:{...observer,beforePreparation:async value=>{await observer.beforePreparation!(value);await stop('prepared')},afterMove:async value=>{await observer.afterMove!(value);if(value.phase==='install')await stop('after-install')}}})
throw new Error('Expected kill boundary')
