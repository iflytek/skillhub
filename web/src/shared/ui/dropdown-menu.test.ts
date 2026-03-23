import { describe, expect, it } from 'vitest'
import {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
} from './dropdown-menu'

describe('DropdownMenu components', () => {
  it('exports all dropdown menu sub-components', () => {
    expect(DropdownMenu).toBeDefined()
    expect(DropdownMenuTrigger).toBeDefined()
    expect(DropdownMenuContent).toBeDefined()
    expect(DropdownMenuItem).toBeDefined()
    expect(DropdownMenuSeparator).toBeDefined()
  })

  it('sets displayName on styled wrapper components', () => {
    expect(DropdownMenuContent.displayName).toBeDefined()
    expect(DropdownMenuItem.displayName).toBeDefined()
    expect(DropdownMenuSeparator.displayName).toBeDefined()
  })
})
