export class CliError extends Error {
  constructor(
    message: string,
    readonly exitCode: number,
    readonly details: Record<string, unknown> = {}
  ) {
    super(message)
    this.name = 'CliError'
  }
}
