import { describe, expect, mock, test } from 'bun:test'
import { canOpenBrowser, openExternalUrl } from '../../../src/platform/browser'

describe('openExternalUrl', () => {
  test.each([
    ['darwin', 'open', ['https://skillhub.example.com/device']],
    ['linux', 'xdg-open', ['https://skillhub.example.com/device']],
    ['win32', 'rundll32', ['url.dll,FileProtocolHandler', 'https://skillhub.example.com/device']]
  ] as const)('uses the platform launcher on %s', (platform, command, args) => {
    const launch = mock(() => true)

    const env = platform === 'linux' ? { DISPLAY: ':0' } : {}
    expect(openExternalUrl('https://skillhub.example.com/device', { platform, launch, env })).toBe(true)
    expect(launch).toHaveBeenCalledWith(command, [...args])
  })

  test('rejects non-http URLs without launching a process', () => {
    const launch = mock(() => true)

    expect(openExternalUrl('javascript:alert(1)', { platform: 'linux', launch, env: { DISPLAY: ':0' } })).toBe(false)
    expect(launch).not.toHaveBeenCalled()
  })

  test('falls back cleanly when the browser launcher is unavailable', () => {
    const launch = mock(() => false)

    expect(openExternalUrl('https://skillhub.example.com/device', { platform: 'linux', launch, env: { DISPLAY: ':0' } })).toBe(false)
  })

  test.each([
    ['CI', { CI: 'true', DISPLAY: ':0' }],
    ['SSH', { SSH_CONNECTION: 'client server', DISPLAY: ':0' }],
    ['SSH TTY', { SSH_TTY: '/dev/pts/0', DISPLAY: ':0' }],
    ['Linux without a display', {}]
  ])('does not launch in %s environments', (_name, env) => {
    const launch = mock(() => true)

    expect(openExternalUrl('https://skillhub.example.com/device', { platform: 'linux', launch, env })).toBe(false)
    expect(launch).not.toHaveBeenCalled()
  })

  test('allows Linux desktops with X11 or Wayland', () => {
    expect(canOpenBrowser('linux', { DISPLAY: ':0' })).toBe(true)
    expect(canOpenBrowser('linux', { WAYLAND_DISPLAY: 'wayland-0' })).toBe(true)
  })

  test('rejects unsupported platforms without launching a process', () => {
    const launch = mock(() => true)

    expect(openExternalUrl('https://skillhub.example.com/device', {
      platform: 'freebsd',
      launch,
      env: {}
    })).toBe(false)
    expect(launch).not.toHaveBeenCalled()
  })

  test('contains a synchronous launcher failure', () => {
    const launch = mock(() => { throw new Error('launcher unavailable') })

    expect(() => openExternalUrl('https://skillhub.example.com/device', {
      platform: 'darwin',
      launch,
      env: {}
    })).not.toThrow()
    expect(openExternalUrl('https://skillhub.example.com/device', {
      platform: 'darwin',
      launch,
      env: {}
    })).toBe(false)
  })
})
