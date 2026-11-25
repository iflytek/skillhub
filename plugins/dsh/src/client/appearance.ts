import { useSyncExternalStore } from 'react';

type Appearance = 'light' | 'dark';
const key = 'skillhub-appearance';
const changed = 'skillhub-appearance-changed';
let temporary: Appearance | undefined;
export function getAppearance(): Appearance {
  if (temporary) return temporary;
  try {
    const saved = localStorage.getItem(key);
    if (saved === 'light' || saved === 'dark') return saved;
  } catch { /* Use the Host theme when storage is unavailable. */ }
  if (typeof document === 'undefined') return 'light';
  const host = getComputedStyle(document.documentElement).colorScheme;
  if (host === 'light' || host === 'dark') return host;
  return matchMedia('(prefers-color-scheme:dark)').matches ? 'dark' : 'light';
}
export function setAppearance(next: Appearance) {
  temporary = next;
  try { localStorage.setItem(key, next); temporary = undefined; } catch { /* Keep a session-local preference. */ }
  window.dispatchEvent(new Event(changed));
}
function subscribe(notify: () => void) {
  const storage = (event: StorageEvent) => { if (event.key === key || event.key === null) { temporary = undefined; notify(); } };
  const media = matchMedia('(prefers-color-scheme:dark)');
  const observer = new MutationObserver(notify);
  observer.observe(document.documentElement, { attributes: true, attributeFilter: ['style', 'class'] });
  window.addEventListener(changed, notify);
  window.addEventListener('storage', storage);
  media.addEventListener('change', notify);
  return () => { observer.disconnect(); window.removeEventListener(changed, notify); window.removeEventListener('storage', storage); media.removeEventListener('change', notify); };
}
export const useAppearance = () => useSyncExternalStore(subscribe, getAppearance, () => 'light' as const);
