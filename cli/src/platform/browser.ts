import { spawn } from 'node:child_process'

type SupportedPlatform = 'darwin' | 'linux' | 'win32'
type Launch = (command: string, args: string[]) => boolean

interface OpenExternalUrlOptions {
  platform?: NodeJS.Platform
  launch?: Launch
}

function launchDetached(command: string, args: string[]): boolean {
  try {
    const child = spawn(command, args, { detached: true, stdio: 'ignore' })
    child.on('error', () => {})
    child.unref()
    return true
  } catch {
    return false
  }
}

function launcherFor(platform: SupportedPlatform, url: string): [string, string[]] {
  if (platform === 'darwin') return ['open', [url]]
  if (platform === 'win32') return ['rundll32', ['url.dll,FileProtocolHandler', url]]
  return ['xdg-open', [url]]
}

export function openExternalUrl(url: string, options: OpenExternalUrlOptions = {}): boolean {
  let parsed: URL
  try {
    parsed = new URL(url)
  } catch {
    return false
  }
  if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') return false

  const platform = options.platform ?? process.platform
  if (platform !== 'darwin' && platform !== 'linux' && platform !== 'win32') return false
  const [command, args] = launcherFor(platform, parsed.toString())
  return (options.launch ?? launchDetached)(command, args)
}
