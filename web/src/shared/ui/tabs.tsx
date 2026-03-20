import * as React from 'react'
import { cn } from '@/shared/lib/utils'

interface TabsProps {
  defaultValue?: string
  value?: string
  children: React.ReactNode
  className?: string
  onValueChange?: (value: string) => void
}

interface TabsContextValue {
  value: string
  setValue: (value: string) => void
}

const TabsContext = React.createContext<TabsContextValue | undefined>(undefined)

export function Tabs({ defaultValue, value: controlledValue, children, className, onValueChange }: TabsProps) {
  const [internalValue, setInternalValue] = React.useState(defaultValue ?? '')
  const isControlled = controlledValue !== undefined
  const value = isControlled ? controlledValue : internalValue

  const handleValueChange = React.useCallback((nextValue: string) => {
    if (!isControlled) {
      setInternalValue(nextValue)
    }
    onValueChange?.(nextValue)
  }, [isControlled, onValueChange])

  return (
    <TabsContext.Provider value={{ value, setValue: handleValueChange }}>
      <div className={className}>{children}</div>
    </TabsContext.Provider>
  )
}

interface TabsListProps {
  children: React.ReactNode
  className?: string
}

export function TabsList({ children, className }: TabsListProps) {
  return (
    <div
      className={cn(
        'inline-flex items-center gap-6 border-b text-sm',
        className
      )}
      style={{ borderColor: 'hsl(var(--border))' }}
    >
      {children}
    </div>
  )
}

interface TabsTriggerProps {
  value: string
  children: React.ReactNode
  className?: string
}

export function TabsTrigger({ value, children, className }: TabsTriggerProps) {
  const context = React.useContext(TabsContext)
  if (!context) throw new Error('TabsTrigger must be used within Tabs')

  const isActive = context.value === value

  return (
    <button
      type="button"
      onClick={() => context.setValue(value)}
      data-state={isActive ? 'active' : 'inactive'}
      className={cn(
        'inline-flex -mb-px items-center justify-center whitespace-nowrap py-3 text-sm font-medium transition-all duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:pointer-events-none disabled:opacity-50',
        isActive
          ? 'border-b-2 border-primary text-primary'
          : 'text-muted-foreground hover:text-foreground/80',
        className
      )}
    >
      {children}
    </button>
  )
}

interface TabsContentProps {
  value: string
  children: React.ReactNode
  className?: string
}

export function TabsContent({ value, children, className }: TabsContentProps) {
  const context = React.useContext(TabsContext)
  if (!context) throw new Error('TabsContent must be used within Tabs')

  if (context.value !== value) return null

  return <div className={cn('animate-fade-in', className)}>{children}</div>
}
