import { runHealth, type HealthOptions } from '../services/health-service'

export interface HealthCommandOptions extends HealthOptions { json?: boolean }

export async function healthCommand(options: HealthCommandOptions): Promise<{ output: string; exitCode: number }> {
  const report = await runHealth(options)
  if (options.json) return { output: JSON.stringify(report), exitCode: report.exitCode }
  const labels = { pass: 'PASS', warn: 'WARN', fail: 'FAIL', skipped: 'SKIP' }
  const lines = [
    `Health: ${report.ok ? report.checks.some(check => check.status === 'warn') ? 'OK (with warnings)' : 'OK' : 'FAILED'}`,
    `Registry: ${report.registry ?? 'unresolved'} (${report.registrySource ?? 'unavailable'})`,
    `Credential source: ${report.credentialSource}`,
    `Config: ${report.paths.config ?? 'unavailable'}`,
    `Credentials: ${report.paths.credentials ?? 'unavailable'}`,
    `Inventory: ${report.paths.inventory ?? 'unavailable'}`,
    '',
    ...report.checks.flatMap(check => [
      `[${labels[check.status]}] ${check.id} (${check.code}): ${check.message}`,
      ...(check.next ? [`  Next: ${check.next}`] : [])
    ]),
    '',
    'Read-only diagnosis. No state was repaired, no login was started, and write permissions were not certified.'
  ]
  return { output: lines.join('\n'), exitCode: report.exitCode }
}
