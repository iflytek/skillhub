import { describe, expect, mock, test } from 'bun:test'
import { openExternalUrl } from '../../../src/platform/browser'

describe('openExternalUrl', () => {
  test.each([
    ['darwin', 'open', ['https://skillhub.example.com/device']],
    ['linux', 'xdg-open', ['https://skillhub.example.com/device']],
    ['win32', 'rundll32', ['url.dll,FileProtocolHandler', 'https://skillhub.example.com/device']]
  ] as const)('uses the platform launcher on %s', (platform, command, args) => {
    const launch = mock(() => true)

    expect(openExternalUrl('https://skillhub.example.com/device', { platform, launch })).toBe(true)
    expect(launch).toHaveBeenCalledWith(command, [...args])
  })

  test('rejects non-http URLs without launching a process', () => {
    const launch = mock(() => true)

    expect(openExternalUrl('javascript:alert(1)', { platform: 'linux', launch })).toBe(false)
    expect(launch).not.toHaveBeenCalled()
  })

  test('falls back cleanly when the browser launcher is unavailable', () => {
    const launch = mock(() => false)

    expect(openExternalUrl('https://skillhub.example.com/device', { platform: 'linux', launch })).toBe(false)
  })
})
