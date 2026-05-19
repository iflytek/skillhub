import { CLI_VERSION } from '../shared/constants'
import { printResult } from '../shared/output'

export async function versionCommand(args: string[]): Promise<string> {
  const json = args.includes('--json')
  return printResult(json ? { ok: true, version: CLI_VERSION } : `SkillHub CLI ${CLI_VERSION}`, json)
}
