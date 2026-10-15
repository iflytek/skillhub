import { NativeControlStyles } from './native-control-styles.js';

/** Scoped to the native installed view; colors follow the host's inherited foreground. */
export function InstalledStyles() {
  return <><NativeControlStyles /><style>{`
    .skillhub-installed { container-type: inline-size; line-height: 1.6; overflow-wrap: anywhere; }
    .skillhub-installed-toolbar { display: flex; align-items: end; gap: 12px; flex-wrap: wrap; }
    .skillhub-installed-toolbar label { display: grid; gap: 6px; font-size: 14px; }
    .skillhub-installed-search { flex: 1 1 240px; }
    .skillhub-installed-note { font-size: 13px; opacity: .75; margin-block: 16px; }
    .skillhub-installed-table { width: 100%; border-collapse: collapse; table-layout: fixed; font-size: 14px; }
    .skillhub-installed-table :is(th, td) { text-align: start; padding: 16px 12px; vertical-align: middle;
      border-bottom: 1px solid color-mix(in srgb, currentColor 16%, transparent); }
    .skillhub-installed-table th { font-weight: 500; background: color-mix(in srgb, currentColor 5%, transparent); }
    .skillhub-installed-table th:first-child { width: 25%; }
    .skillhub-installed-table th:last-child { width: 22%; }
    .skillhub-installed-actions { display: flex; flex-wrap: wrap; gap: 8px; }
    .skillhub-installed-actions button + button { margin-inline-start: 0; }
    .skillhub-installed-table td strong { display: block; font-size: 16px; }
    .skillhub-installed-coordinate { display: block; font-size: 12px; opacity: .7; margin-top: 4px; }
    .skillhub-installed-table td[data-label="本地状态"] { font-size: 13px; }
    .skillhub-installed-empty { padding: 32px 16px; text-align: center; border: 1px dashed color-mix(in srgb, currentColor 25%, transparent); border-radius: 12px; }
    @container (max-width: 680px) {
      .skillhub-installed-table, .skillhub-installed-table tbody { display: block; }
      .skillhub-installed-table thead { position: absolute; width: 1px; height: 1px; overflow: hidden; clip-path: inset(50%); }
      .skillhub-installed-table tr { display: block; border: 1px solid color-mix(in srgb, currentColor 20%, transparent); border-radius: 12px; padding: 12px; margin-block: 12px; }
      .skillhub-installed-table td { display: block; border: 0; padding: 6px 0; }
      .skillhub-installed-table td[data-label]::before { content: attr(data-label) "："; opacity: .7; margin-inline-end: 8px; }
      .skillhub-installed-toolbar label { width: 100%; }
      .skillhub-installed-toolbar button { flex: 1; }
    }
  `}</style></>;
}
