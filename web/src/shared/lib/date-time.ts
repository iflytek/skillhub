function parseServerDateTime(value: string): Date {
  if (/[zZ]$|[+-]\d{2}:\d{2}$/.test(value)) {
    return new Date(value)
  }

  const [datePart, timePart = '00:00:00'] = value.split('T')
  const [year, month, day] = datePart.split('-').map(Number)
  const [rawTime, fractional = ''] = timePart.split('.')
  const [hours = 0, minutes = 0, seconds = 0] = rawTime.split(':').map(Number)
  const milliseconds = Number((fractional + '000').slice(0, 3))

  return new Date(year, (month || 1) - 1, day || 1, hours, minutes, seconds, milliseconds)
}

export function formatLocalDateTime(
  value: string,
  locale: string,
  options: Intl.DateTimeFormatOptions = { dateStyle: 'medium', timeStyle: 'short' },
) {
  return new Intl.DateTimeFormat(locale, options).format(parseServerDateTime(value))
}
