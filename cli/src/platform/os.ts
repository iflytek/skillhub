/**
 * Platform detection utilities.
 */

export type Platform = 'darwin' | 'linux' | 'win32' | 'unknown'

export function currentPlatform(): Platform {
  const p = process.platform
  if (p === 'darwin' || p === 'linux' || p === 'win32') return p
  return 'unknown'
}

export function isWindows(): boolean {
  return process.platform === 'win32'
}

export function isTTY(): boolean {
  return process.stdout.isTTY === true
}
