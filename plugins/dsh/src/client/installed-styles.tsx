import { NativeControlStyles } from './native-control-styles.js';

export function InstalledStyles() {
  return <><NativeControlStyles /><style>{`
    .skillhub-installed { container-type: inline-size; line-height: 1.6; overflow-wrap: anywhere; }
    .skillhub-installed .skillhub-installed-toolbar { display:flex;align-items:center;gap:12px;flex-wrap:wrap; }
    .skillhub-installed .skillhub-installed-toolbar label { display:grid;gap:6px;font-size:14px; }
    .skillhub-installed .skillhub-installed-search { flex:1 1 240px; }
    .skillhub-installed .skillhub-installed-toolbar { margin-bottom:12px; }
    .skillhub-installed .skillhub-installed-toolbar button { margin:0;white-space:nowrap; }
    .skillhub-installed .skillhub-installed-summary { display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap;margin-bottom:20px; }
    .skillhub-installed .skillhub-installed-summary p { margin:0;color:var(--astron-sh-muted);font-size:13px; }
    .skillhub-installed .skillhub-installed-filters { display:flex;gap:4px; }
    .skillhub-installed .skillhub-installed-filters button { margin:0; }
    .skillhub-installed .skillhub-installed-note { font-size:13px;color:var(--astron-sh-muted);margin-block:16px; }
    .skillhub-installed .astron-sh-installed-card { display:flex;flex-direction:column;min-width:0; }
    .skillhub-installed .astron-sh-installed-card .astron-sh-card-content { padding:0 18px 18px;font-size:13px; }
    .skillhub-installed .astron-sh-installed-card .astron-sh-card-content p { margin:4px 0; }
    .skillhub-installed .skillhub-installed-actions { display:flex;flex-wrap:wrap;gap:8px;padding:14px 18px;border-top:1px solid var(--astron-sh-border);margin-top:auto; }
    .skillhub-installed .skillhub-installed-actions button { font-size:12px;padding:6px 8px; }
    .skillhub-installed .skillhub-installed-actions button + button { margin-inline-start:0; }
    .skillhub-installed .skillhub-installed-coordinate { display:block;font-size:12px;line-height:18px;color:var(--astron-sh-muted);margin-top:4px;overflow-wrap:anywhere; }
    .skillhub-installed .skillhub-installed-empty { padding:32px 16px;text-align:center;border:1px dashed var(--astron-sh-border);border-radius:12px; }
    .skillhub-installed .astron-sh-version-update { font-size:13px;line-height:20px; }
    .skillhub-installed .astron-sh-version-update .astron-sh-card-header { padding:14px 16px 8px; }
    .skillhub-installed .astron-sh-version-update h3 { margin:0;font-size:13px;line-height:20px;font-weight:600; }
    .skillhub-installed .astron-sh-version-update .astron-sh-card-content { padding:0 16px 14px; }
    .skillhub-installed .astron-sh-version-update p { margin:10px 0 0; }
    .skillhub-installed .astron-sh-update-actions { display:flex;flex-wrap:wrap;gap:8px; }
    .skillhub-installed .astron-sh-update-actions button { margin:0;font-size:12px;min-height:32px;padding:5px 9px; }
    .skillhub-installed .astron-sh-version-update .astron-sh-update-time { font-size:12px;line-height:18px;color:var(--astron-sh-muted); }
    @container (max-width:680px) { .skillhub-installed .skillhub-installed-toolbar label { width:100%; } }
  `}</style></>;
}
