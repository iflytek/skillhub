import type { FavoriteSkill } from '../host/types.js';
import { Icon } from './skill-card.js';
import { Button } from './ui.js';

export function FavoriteButton({ item, pressed, disabled, busy, onToggle }: {
  item: FavoriteSkill; pressed: boolean; disabled: boolean; busy: boolean; onToggle(): void;
}) {
  const label = `${pressed ? '取消收藏' : '收藏'} ${item.displayName || item.slug} @${item.namespace}/${item.slug}`;
  return <Button variant="ghost" size="icon" className="astron-sh-icon-button astron-sh-favorite-button"
    aria-label={label} aria-pressed={pressed} aria-busy={busy || undefined} aria-disabled={busy || undefined} title={pressed ? '取消本机收藏' : '收藏到本机'} disabled={disabled}
    onClick={event => { event.stopPropagation(); if (!disabled && !busy) onToggle(); }}><Icon name="bookmark" /></Button>;
}
