export class CliError extends Error {
  constructor(
    message: string,
    readonly exitCode: number,
    readonly details: Record<string, unknown> = {},
    // Transport metadata is separate from details to preserve public error JSON.
    readonly httpStatus?: number
  ) {
    super(message)
    this.name = 'CliError'
  }
}
