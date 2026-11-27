import { useAppearance, setAppearance } from './appearance.js';
import { Button } from './ui.js';

export function AppearanceToggle() {
  const dark = useAppearance() === 'dark';
  const label = dark ? '切换到浅色模式' : '切换到深色模式';
  return <Button size="icon" variant="ghost" aria-label={label} title={label} onClick={() => setAppearance(dark ? 'light' : 'dark')}>
    <svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7">
      {dark ? <><circle cx="12" cy="12" r="4" /><path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.5 1.5m11 11L19 19M5 19l1.5-1.5m11-11L19 5" /></> : <path d="M20.6 14.2A9 9 0 0 1 9.8 3.4 9 9 0 1 0 20.6 14.2Z" />}
    </svg>
  </Button>;
}
