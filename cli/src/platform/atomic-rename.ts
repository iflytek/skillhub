import { rename } from 'node:fs/promises'

/** Retry only Windows sharing failures; never remove the destination or replay a transaction. */
export async function renameAtomicFile(from: string, to: string,
  renameFile: typeof rename = rename, platform: NodeJS.Platform = process.platform): Promise<void> {
  const delays = [20, 40, 80, 160, 320]
  for (let attempt = 0; ; attempt++) {
    try { await renameFile(from, to); return }
    catch (error) {
      const code = (error as NodeJS.ErrnoException).code
      const delay = delays[attempt]
      if (platform !== 'win32' || !['EPERM', 'EBUSY'].includes(code ?? '') || delay === undefined) throw error
      await new Promise(resolve => setTimeout(resolve, delay))
    }
  }
}
