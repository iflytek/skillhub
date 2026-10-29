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
    .skillhub-installed .astron-sh-card-grid { grid-template-columns:repeat(2,minmax(0,1fr));gap:12px; }
    .skillhub-installed .astron-sh-installed-card { display:grid;grid-template-columns:38px minmax(0,1fr) auto;align-items:start;gap:12px;padding:16px;position:relative;min-width:0; }
    .skillhub-installed .skillhub-installed-identity { padding:0;min-width:0; }
    .skillhub-installed .skillhub-installed-identity h3 { font-size:15px;line-height:22px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap; }
    .skillhub-installed .skillhub-installed-description { margin:5px 0 0;font-size:13px;line-height:20px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap; }
    .skillhub-installed .skillhub-installed-version { margin:5px 0 0;font-size:12px;line-height:18px;color:var(--astron-sh-muted);overflow:hidden;text-overflow:ellipsis;white-space:nowrap; }
    .skillhub-installed .skillhub-installed-side { display:flex;flex-direction:column;align-items:flex-end;justify-content:space-between;align-self:stretch;gap:10px;max-width:140px; }
    .skillhub-installed .skillhub-installed-badges { display:flex;flex-wrap:wrap;justify-content:flex-end;gap:4px;max-width:140px; }
    .skillhub-installed .skillhub-installed-badges .astron-sh-badge { text-align:right; }
    .skillhub-installed .skillhub-installed-actions { margin-top:auto;flex-wrap:nowrap;gap:4px; }
    .skillhub-installed .skillhub-installed-actions button { margin:0;font-size:12px;min-height:30px;height:auto;padding:4px 8px; }
    .skillhub-installed .skillhub-installed-actions .astron-sh-primary-actions { margin:0; }
    @container (max-width:800px) { .skillhub-installed .astron-sh-card-grid { grid-template-columns:1fr; } }
    @container (max-width:380px) {
      .skillhub-installed .astron-sh-installed-card { grid-template-columns:32px minmax(0,1fr);gap:10px;padding:12px; }
      .skillhub-installed .astron-sh-installed-card>.astron-sh-avatar { width:32px;height:32px; }
      .skillhub-installed .skillhub-installed-side { grid-column:2;flex-direction:row;align-items:center;max-width:none; }
      .skillhub-installed .skillhub-installed-actions { margin-top:0;margin-left:auto; }
    }
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
