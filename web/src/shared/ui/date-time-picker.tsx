import { useId, useState } from 'react'
import * as Popover from '@radix-ui/react-popover'
import { CalendarDays, ChevronLeft, ChevronRight } from 'lucide-react'
import { DayPicker } from 'react-day-picker'
import { enUS } from 'react-day-picker/locale/en-US'
import { zhCN } from 'react-day-picker/locale/zh-CN'
import { useTranslation } from 'react-i18next'
import { formatLocalDateTime, toLocalDateTimeInputValue } from '@/shared/lib/date-time'
import { getPortalContainer } from '@/shared/lib/portal-container'
import { cn } from '@/shared/lib/utils'
import { Button, buttonVariants } from './button'
import { Input } from './input'
import { Label } from './label'

interface DateTimePickerProps {
  id?: string
  value: string
  onChange: (value: string) => void
  min?: Date
  disabled?: boolean
  'aria-label'?: string
  'aria-invalid'?: boolean
  'aria-describedby'?: string
}

/** Local-time form value; conversion to UTC belongs to the submitting form. */
export function DateTimePicker({ value, onChange, min, disabled, id, ...aria }: DateTimePickerProps) {
  const { t, i18n } = useTranslation()
  const generatedId = useId()
  const fieldId = id ?? generatedId
  const [open, setOpen] = useState(false)
  const [day, setDay] = useState<Date>()
  const [hour, setHour] = useState('00')
  const [minute, setMinute] = useState('00')
  const [expired, setExpired] = useState(false)
  const selected = value ? new Date(value) : undefined
  const validSelected = selected && Number.isFinite(selected.getTime()) ? selected : undefined
  const lowerBound = min ? new Date(min.getFullYear(), min.getMonth(), min.getDate()) : undefined
  const draft = day && /^\d{1,2}$/.test(hour) && /^\d{1,2}$/.test(minute)
    && +hour < 24 && +minute < 60
    ? new Date(day.getFullYear(), day.getMonth(), day.getDate(), +hour, +minute)
    : undefined
  const tooEarly = expired || (!!draft && !!min && draft.getTime() <= min.getTime())

  const apply = () => {
    if (!draft) return
    if (min && draft.getTime() <= Math.max(min.getTime(), Date.now())) {
      setExpired(true)
      return
    }
    onChange(toLocalDateTimeInputValue(draft))
    setOpen(false)
  }

  const changeOpen = (next: boolean) => {
    if (next) {
      setExpired(false)
      const initial = validSelected ?? new Date(Math.max(Date.now(), min?.getTime() ?? 0) + 60 * 60 * 1000)
      setDay(initial)
      setHour(String(initial.getHours()).padStart(2, '0'))
      setMinute(String(initial.getMinutes()).padStart(2, '0'))
    }
    setOpen(next)
  }

  return (
    <Popover.Root open={open} onOpenChange={changeOpen} modal>
      <Popover.Trigger asChild>
        <Button
          type="button" id={fieldId} variant="outline" disabled={disabled}
          className={cn('h-9 w-full min-w-0 justify-between text-xs font-normal sm:h-7 aria-[invalid=true]:border-destructive', !validSelected && 'text-muted-foreground')}
          {...aria}
        >
          <span className="truncate">{validSelected
            ? formatLocalDateTime(value, i18n.language)
            : t('dateTimePicker.placeholder')}</span>
          <CalendarDays className="h-3.5 w-3.5 shrink-0 text-muted-foreground" aria-hidden="true" />
        </Button>
      </Popover.Trigger>
      <Popover.Portal container={getPortalContainer()}>
        <Popover.Content
          align="end" sideOffset={8} collisionPadding={16}
          aria-label={t('dateTimePicker.title')}
          onKeyDown={(event) => {
            if (event.key === 'Enter' && event.target instanceof HTMLInputElement) {
              event.preventDefault()
              apply()
            }
          }}
          className="z-50 w-[17.5rem] max-w-[calc(100vw-2rem)] max-h-[var(--radix-popover-content-available-height)] overflow-y-auto rounded-xl border border-border bg-popover p-3 text-popover-foreground shadow-lg outline-none"
        >
          <DayPicker
            mode="single" required autoFocus selected={day} onSelect={(next) => { setDay(next); setExpired(false) }}
            defaultMonth={day} startMonth={lowerBound} disabled={lowerBound ? { before: lowerBound } : undefined}
            locale={i18n.language.startsWith('zh') ? zhCN : enUS}
            weekStartsOn={1} showOutsideDays fixedWeeks navLayout="around"
            classNames={{
              root: 'relative',
              months: 'relative', month: 'relative',
              month_caption: 'flex h-7 items-center justify-center', caption_label: 'text-sm font-semibold',
              button_previous: cn(buttonVariants({ variant: 'ghost', size: 'icon' }), 'absolute left-0 top-0'),
              button_next: cn(buttonVariants({ variant: 'ghost', size: 'icon' }), 'absolute right-0 top-0'),
              month_grid: 'mt-2 w-full border-collapse', weekdays: 'flex',
              weekday: 'flex-1 py-0.5 text-center text-xs leading-4 font-normal text-muted-foreground',
              week: 'mt-0.5 flex', day: 'group/day relative flex flex-1 justify-center p-0 text-center',
              day_button: 'mx-auto flex h-9 w-9 items-center justify-center rounded-md text-xs leading-4 tabular-nums sm:h-7 sm:w-7 hover:bg-accent focus-visible:outline focus-visible:outline-2 focus-visible:outline-ring focus-visible:outline-offset-1 disabled:pointer-events-none',
              selected: '[&>button]:bg-primary [&>button]:text-primary-foreground [&>button]:hover:bg-primary/90',
              today: '[&>button]:font-semibold [&:not([data-selected])>button]:ring-1 [&:not([data-selected])>button]:ring-inset [&:not([data-selected])>button]:ring-border',
              outside: 'text-muted-foreground/60', disabled: 'text-muted-foreground/35',
              hidden: 'invisible',
            }}
            components={{ Chevron: ({ orientation }) => orientation === 'left'
              ? <ChevronLeft className="h-4 w-4" aria-hidden="true" />
              : <ChevronRight className="h-4 w-4" aria-hidden="true" /> }}
          />
          <div className="mt-2 flex items-center justify-between gap-2 border-t border-border pt-2">
            <div className="text-xs leading-4 font-medium">{t('dateTimePicker.time')}<p className="mt-0.5 text-xs leading-4 font-normal text-muted-foreground">{t('dateTimePicker.localTime')}</p></div>
            <div className="flex items-center gap-1.5">
              <Label htmlFor={`${fieldId}-hour`} className="sr-only">{t('dateTimePicker.hour')}</Label>
              <Input id={`${fieldId}-hour`} type="number" inputMode="numeric" min={0} max={23} value={hour}
                aria-invalid={tooEarly} aria-describedby={tooEarly ? `${fieldId}-error` : undefined}
                onChange={(event) => { setHour(event.target.value); setExpired(false) }} className="h-9 w-14 px-2 text-center text-xs tabular-nums sm:h-7" />
              <span aria-hidden="true" className="text-muted-foreground">:</span>
              <Label htmlFor={`${fieldId}-minute`} className="sr-only">{t('dateTimePicker.minute')}</Label>
              <Input id={`${fieldId}-minute`} type="number" inputMode="numeric" min={0} max={59} value={minute}
                aria-invalid={tooEarly} aria-describedby={tooEarly ? `${fieldId}-error` : undefined}
                onChange={(event) => { setMinute(event.target.value); setExpired(false) }} className="h-9 w-14 px-2 text-center text-xs tabular-nums sm:h-7" />
            </div>
          </div>
          {tooEarly && <p id={`${fieldId}-error`} className="mt-2 text-xs text-destructive" role="alert">{t('dateTimePicker.futureRequired')}</p>}
          <div className="mt-2 flex justify-end gap-2">
            <Button type="button" variant="ghost" size="sm" className="h-9 text-xs sm:h-7" onClick={() => setOpen(false)}>{t('dialog.cancel')}</Button>
            <Button type="button" size="sm" className="h-9 text-xs sm:h-7" disabled={!draft || tooEarly} onClick={apply}>{t('dateTimePicker.apply')}</Button>
          </div>
        </Popover.Content>
      </Popover.Portal>
    </Popover.Root>
  )
}
