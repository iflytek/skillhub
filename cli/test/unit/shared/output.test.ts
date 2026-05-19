import { describe, expect, test } from 'bun:test'
import { CliError } from '../../../src/shared/errors'
import { printResult, renderError } from '../../../src/shared/output'

describe('renderError', () => {
  test('renders stable json error to stderr payload', () => {
    const error = new CliError('authentication failed', 2, { registry: 'https://registry.example.com' })
    expect(renderError(error, true)).toBe(JSON.stringify({
      ok: false,
      message: 'authentication failed',
      exitCode: 2,
      details: { registry: 'https://registry.example.com' }
    }))
  })

  test('renders human error without stack trace', () => {
    const error = new CliError('registry unreachable', 3, {
      registry: 'https://registry.example.com',
      next: 'check network or pass --registry'
    })
    expect(renderError(error, false)).toBe([
      'Error: registry unreachable',
      'Context: registry https://registry.example.com',
      'Next: check network or pass --registry'
    ].join('\n'))
  })
})

describe('printResult', () => {
  test('returns string as-is in non-JSON mode', () => {
    expect(printResult('hello', false)).toBe('hello')
  })

  test('wraps string in JSON envelope', () => {
    expect(printResult('hello', true)).toBe(JSON.stringify({ ok: true, message: 'hello' }))
  })

  test('formats object in non-JSON mode', () => {
    const result = printResult({ ok: true, handle: 'me', email: 'a@b' }, false)
    expect(result).toBe('handle: me\nemail: a@b')
  })
})
