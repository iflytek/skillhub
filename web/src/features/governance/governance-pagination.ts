export function getGovernanceTotalPages(total: number, size: number): number {
  if (size <= 0) {
    return 1
  }
  return Math.max(1, Math.ceil(total / size))
}

