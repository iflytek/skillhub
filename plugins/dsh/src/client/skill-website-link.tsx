import { Button } from './ui.js';
import { Icon } from './skill-card.js';

/** Use only the API's complete public page URL; never append local configuration or credentials. */
export function safeSkillWebUrl(value: string | null | undefined): string | undefined {
  if (!value) return undefined;
  const text = value.trim();
  // eslint-disable-next-line no-control-regex -- Reject characters URL parsing would silently normalize.
  if (!/^https?:\/\//iu.test(text) || /[\\\u0000-\u001f\u007f]/u.test(value)) return undefined;
  try {
    const url = new URL(text);
    if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password) return undefined;
    return url.href;
  } catch { return undefined; }
}

export function SkillWebsiteLink({ webUrl, enabled }: { webUrl?: string | null; enabled: boolean }) {
  const href = enabled ? safeSkillWebUrl(webUrl) : undefined;
  return href ? <Button asChild size="sm" variant="outline" className="astron-sh-website-link"><a href={href} target="_blank" rel="noopener noreferrer">在官网查看<Icon name="arrow" /></a></Button> : null;
}
