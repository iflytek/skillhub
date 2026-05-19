export interface CommandContext {
  cwd: string
  env: NodeJS.ProcessEnv
  stdout: { write(text: string): void }
  stderr: { write(text: string): void }
}

export interface CommandResult {
  exitCode: number
}
