/** RFC 9110 section 10.2.3: delay-seconds or HTTP-date. */
export function retryAfterDeadline(value: string | null, now: number): number | undefined {
  if (!value) return undefined;
  const input = value.trim();
  if (/^\d+$/.test(input)) {
    const deadline = now + Number(input) * 1000;
    return Number.isSafeInteger(deadline) ? deadline : undefined;
  }
  // Do not let Date.parse interpret malformed delay values (e.g. "1.5") as dates.
  if (!/^[A-Za-z]{3}(?:, |[a-z]+, | )[\w ,:-]+$/.test(input)) return undefined;
  const deadline = Date.parse(input);
  return Number.isFinite(deadline) ? Math.max(now, deadline) : undefined;
}
