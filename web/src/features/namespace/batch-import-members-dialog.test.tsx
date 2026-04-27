import { describe, expect, it } from 'vitest'
import { parseCsv, validateRows } from './batch-import-members-dialog'

describe('parseCsv', () => {
  it('parses basic CSV with header', () => {
    const result = parseCsv('userId,role\nuser-1,MEMBER\nuser-2,ADMIN')
    expect(result).toEqual([
      { userId: 'user-1', role: 'MEMBER' },
      { userId: 'user-2', role: 'ADMIN' },
    ])
  })

  it('parses CSV without header', () => {
    const result = parseCsv('user-1,MEMBER\nuser-2,ADMIN')
    expect(result).toEqual([
      { userId: 'user-1', role: 'MEMBER' },
      { userId: 'user-2', role: 'ADMIN' },
    ])
  })

  it('handles Windows line endings', () => {
    const result = parseCsv('userId,role\r\nuser-1,MEMBER\r\nuser-2,ADMIN\r\n')
    expect(result).toEqual([
      { userId: 'user-1', role: 'MEMBER' },
      { userId: 'user-2', role: 'ADMIN' },
    ])
  })

  it('normalizes role to uppercase', () => {
    const result = parseCsv('user-1,member\nuser-2,admin')
    expect(result).toEqual([
      { userId: 'user-1', role: 'MEMBER' },
      { userId: 'user-2', role: 'ADMIN' },
    ])
  })

  it('skips empty lines', () => {
    const result = parseCsv('userId,role\nuser-1,MEMBER\n\n\nuser-2,ADMIN\n')
    expect(result).toEqual([
      { userId: 'user-1', role: 'MEMBER' },
      { userId: 'user-2', role: 'ADMIN' },
    ])
  })

  it('returns empty array for empty input', () => {
    expect(parseCsv('')).toEqual([])
    expect(parseCsv('\n\n')).toEqual([])
  })

  it('handles missing role column', () => {
    const result = parseCsv('user-1')
    expect(result).toEqual([{ userId: 'user-1', role: '' }])
  })

  it('trims whitespace from values', () => {
    const result = parseCsv('  user-1  ,  MEMBER  ')
    expect(result).toEqual([{ userId: 'user-1', role: 'MEMBER' }])
  })
})

describe('validateRows', () => {
  it('marks valid rows', () => {
    const result = validateRows([
      { userId: 'user-1', role: 'MEMBER' },
      { userId: 'user-2', role: 'ADMIN' },
    ])
    expect(result).toEqual([
      { userId: 'user-1', role: 'MEMBER', validation: 'valid' },
      { userId: 'user-2', role: 'ADMIN', validation: 'valid' },
    ])
  })

  it('flags missing userId', () => {
    const result = validateRows([{ userId: '', role: 'MEMBER' }])
    expect(result[0].validation).toBe('missing_user_id')
  })

  it('flags invalid role', () => {
    const result = validateRows([{ userId: 'user-1', role: 'OWNER' }])
    expect(result[0].validation).toBe('invalid_role')
  })

  it('flags empty role as invalid', () => {
    const result = validateRows([{ userId: 'user-1', role: '' }])
    expect(result[0].validation).toBe('invalid_role')
  })

  it('flags duplicate userIds', () => {
    const result = validateRows([
      { userId: 'user-1', role: 'MEMBER' },
      { userId: 'user-1', role: 'ADMIN' },
    ])
    expect(result[0].validation).toBe('valid')
    expect(result[1].validation).toBe('duplicate')
  })

  it('checks missing userId before duplicate', () => {
    const result = validateRows([{ userId: '', role: 'MEMBER' }])
    expect(result[0].validation).toBe('missing_user_id')
  })
})
