export function userStateDir(home = process.env.HOME || process.env.USERPROFILE || ''): string {
  if (!home) {
    throw new Error('Cannot resolve user home directory')
  }
  return `${home.replace(/\\/g, '/')}/.skillhub`
}

export function joinPath(...parts: string[]): string {
  return parts.join('/').replace(/\/+/g, '/')
}

export async function ensureDir(dir: string): Promise<void> {
  const { mkdir } = await import('node:fs/promises')
  await mkdir(dir, { recursive: true })
}

export async function pathExists(path: string): Promise<boolean> {
  const { access } = await import('node:fs/promises')
  try {
    await access(path)
    return true
  } catch {
    return false
  }
}

export async function applyCredentialPermissions(path: string): Promise<void> {
  if (process.platform === 'win32') return
  const { chmod } = await import('node:fs/promises')
  await chmod(path, 0o600)
}
