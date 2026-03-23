import { describe, expect, it } from 'vitest'
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from './table'

describe('Table components', () => {
  it('exports all table sub-components', () => {
    expect(Table).toBeDefined()
    expect(TableHeader).toBeDefined()
    expect(TableBody).toBeDefined()
    expect(TableRow).toBeDefined()
    expect(TableHead).toBeDefined()
    expect(TableCell).toBeDefined()
  })

  it('sets displayName on all table sub-components', () => {
    expect(Table.displayName).toBe('Table')
    expect(TableHeader.displayName).toBe('TableHeader')
    expect(TableBody.displayName).toBe('TableBody')
    expect(TableRow.displayName).toBe('TableRow')
    expect(TableHead.displayName).toBe('TableHead')
    expect(TableCell.displayName).toBe('TableCell')
  })
})
