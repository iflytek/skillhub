export function computeStrictIsTTY(env: {
  stdinIsTTY: boolean
  stdoutIsTTY: boolean
  json: boolean
}): boolean {
  return env.stdinIsTTY && env.stdoutIsTTY && !env.json
}
