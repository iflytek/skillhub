import { runDoctor } from '../services/doctor-service'

export interface DoctorCommandOptions {
  json?: boolean
}

export async function doctorCommand(options: DoctorCommandOptions): Promise<string> {
  const result = await runDoctor(process.cwd())
  if (options.json) {
    return JSON.stringify({
      ok: true,
      inventoryPath: result.inventoryPath,
      backupPath: result.backupPath,
      itemsScanned: result.itemsScanned,
      targetsScanned: result.targetsScanned,
      itemsPreserved: result.itemsPreserved,
      targetsPreserved: result.targetsPreserved,
      skipped: result.skipped,
      conflicts: result.conflicts
    })
  }
  const lines = [
    `Inventory: ${result.inventoryPath}`,
    result.backupPath ? `Backup: ${result.backupPath}` : null,
    `Scanned: ${result.itemsScanned} items, ${result.targetsScanned} targets`,
    result.itemsPreserved > 0
      ? `Preserved (outside scan): ${result.itemsPreserved} items, ${result.targetsPreserved} targets`
      : null,
    result.skipped.length > 0 ? `Skipped: ${result.skipped.length} directories` : null,
    result.conflicts.length > 0 ? `Conflicts: ${result.conflicts.length} groups` : null
  ].filter(Boolean)
  return lines.join('\n')
}
