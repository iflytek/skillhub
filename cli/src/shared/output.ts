import { CliError } from './errors'

export type JsonObject = Record<string, unknown>

export function printResult(result: string | JsonObject, json: boolean): string {
  if (json) {
    return JSON.stringify(typeof result === 'string' ? { ok: true, message: result } : result)
  }
  return typeof result === 'string' ? result : humanize(result)
}

export function renderError(error: unknown, json: boolean): string {
  const cliError = error instanceof CliError
    ? error
    : new CliError('unexpected failure', 1)

  if (json) {
    return JSON.stringify({
      ok: false,
      message: cliError.message,
      exitCode: cliError.exitCode,
      ...(Object.keys(cliError.details).length > 0 ? { details: cliError.details } : {})
    })
  }

  const lines = [`Error: ${cliError.message}`]
  if (typeof cliError.details.registry === 'string') {
    lines.push(`Context: registry ${cliError.details.registry}`)
  }
  if (typeof cliError.details.path === 'string') {
    lines.push(`Context: path ${cliError.details.path}`)
  }
  if (typeof cliError.details.next === 'string') {
    lines.push(`Next: ${cliError.details.next}`)
  }
  return lines.join('\n')
}

function humanize(value: JsonObject): string {
  return Object.entries(value)
    .filter(([key]) => key !== 'ok')
    .map(([key, item]) => `${key}: ${String(item)}`)
    .join('\n')
}
