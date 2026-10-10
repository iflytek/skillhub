const PORTAL_ROOT_ID = 'skillhub-portals'

/**
 * Shared host for Dialog/Sonner portals so they do not fight React's `#root`
 * reconciliation on `document.body` (insertBefore / removeChild races).
 * Select/DropdownMenu render in-tree (no Portal) by design.
 */
export function getPortalContainer(): HTMLElement | undefined {
  if (typeof document === 'undefined') {
    return undefined
  }

  return document.getElementById(PORTAL_ROOT_ID) ?? undefined
}

export { PORTAL_ROOT_ID }
