export function skillInitial(name?: string | null, slug?: string | null): string {
  return Array.from(name?.trim() || slug?.trim() || '?')[0]!.toUpperCase();
}
export function skillTone(identity: string): number {
  let hash = 0;
  for (const char of identity) hash = (Math.imul(hash, 31) + (char.codePointAt(0) ?? 0)) >>> 0;
  return hash % 6;
}
export function formatCount(value: number): string {
  return value >= 10000 ? `${(value / 10000).toFixed(1)}万` : value.toLocaleString('zh-CN');
}
export function validCount(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value >= 0;
}
export function validRating(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value) && value > 0 && value <= 5;
}

export function readableDate(value?: string | null, includeTime = false): string {
  if (!value) return '日期未知';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '日期未知' : new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: 'long', day: 'numeric',
    ...(includeTime ? { hour: '2-digit', minute: '2-digit' } as const : {}),
  }).format(date);
}
