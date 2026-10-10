export function formatCompactCount(value: number): string {
  if (value >= 1_000_000) {
    const millions = value / 1_000_000
    return `${stripTrailingZero(millions)}M`
  }

  if (value >= 1_000) {
    const thousands = value / 1_000
    return `${stripTrailingZero(thousands)}K`
  }

  return String(value)
}

function stripTrailingZero(value: number): string {
  const formatted = value >= 10 ? value.toFixed(0) : value.toFixed(1)
  return formatted.endsWith('.0') ? formatted.slice(0, -2) : formatted
}
