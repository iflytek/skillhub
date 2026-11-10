import { NativeControlStyles } from './native-control-styles.js';

export function InstalledStyles() {
  return <><NativeControlStyles /><style>{`
    .skillhub-installed { container-type: inline-size; line-height: 1.6; overflow-wrap: anywhere; }
    .skillhub-installed .skillhub-installed-toolbar { display:flex;align-items:center;gap:8px;flex-wrap:wrap; }
    .skillhub-installed .skillhub-installed-toolbar label { display:grid;gap:6px;font-size:13px; }
    .skillhub-installed .skillhub-installed-search input { height:32px;min-height:32px;font-size:13px; }
    .skillhub-installed .skillhub-installed-search { flex:1 1 240px; }
    .skillhub-installed .skillhub-installed-toolbar { margin-bottom:12px; }
    .skillhub-installed .skillhub-installed-toolbar button { margin:0;white-space:nowrap; }
    .skillhub-installed .skillhub-installed-summary { display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap;margin-bottom:20px; }
    .skillhub-installed .skillhub-installed-summary p { margin:0;color:var(--astron-sh-muted);font-size:13px; }
    .skillhub-installed .skillhub-installed-filters { display:flex;gap:4px; }
    .skillhub-installed .skillhub-installed-filters button { margin:0; }
    .skillhub-installed .skillhub-installed-note { font-size:13px;color:var(--astron-sh-muted);margin-block:16px; }
    .skillhub-installed .astron-sh-card-grid { grid-template-columns:repeat(4,minmax(0,1fr));gap:16px; }
    .skillhub-installed .astron-sh-installed-card { display:flex;flex-direction:column;gap:0;padding:16px;position:relative;min-width:0;min-height:192px; }
    .skillhub-installed .skillhub-installed-card-heading { display:flex;align-items:center;gap:12px;min-width:0; }
    .skillhub-installed .skillhub-installed-card-heading>.astron-sh-avatar { width:32px;height:32px;flex-shrink:0; }
    .skillhub-installed .skillhub-installed-identity { padding:0;min-width:0; }
    .skillhub-installed .skillhub-installed-identity h3 { font-size:14px;line-height:20px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap; }
    .skillhub-installed .skillhub-installed-namespace { margin:2px 0 0;font-size:12px;line-height:16px;color:var(--astron-sh-muted);overflow:hidden;text-overflow:ellipsis;white-space:nowrap; }
    .skillhub-installed .skillhub-installed-description { margin:10px 0 0;font-size:13px;line-height:20px;min-height:40px;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden; }
    .skillhub-installed .skillhub-installed-version { margin:0;font-size:12px;line-height:18px;color:var(--astron-sh-muted);overflow:hidden;text-overflow:ellipsis;white-space:nowrap;min-width:0; }
    .skillhub-installed .skillhub-installed-badges { display:flex;flex-wrap:wrap;gap:4px;margin:6px 0;min-height:20px; }
    .skillhub-installed .skillhub-installed-footer { display:flex;align-items:center;justify-content:space-between;gap:8px;border-top:1px solid var(--astron-sh-border);padding-top:8px;margin-top:auto; }
    .skillhub-installed .skillhub-installed-actions { flex-wrap:nowrap;gap:4px;flex-shrink:0; }
    .skillhub-installed .skillhub-installed-actions button { margin:0;white-space:nowrap; }
    .skillhub-installed .skillhub-installed-actions .astron-sh-primary-actions { margin:0; }
    .skillhub-installed .skillhub-installed-filters { flex-wrap:wrap; }
    @container (max-width:1119px) { .skillhub-installed .astron-sh-card-grid { grid-template-columns:repeat(3,minmax(0,1fr)); } }
    @container (max-width:839px) { .skillhub-installed .astron-sh-card-grid { grid-template-columns:repeat(2,minmax(0,1fr)); } }
    @container (max-width:579px) { .skillhub-installed .astron-sh-card-grid { grid-template-columns:1fr; } }
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

export function WorkbenchStyles() {
  return <style>{`
    .skillhub-controls.astron-sh-catalog .astron-sh-workbench-frame{max-width:1152px;margin:0 auto;padding:24px 24px 32px;box-sizing:content-box}
    .skillhub-controls.astron-sh-catalog .astron-sh-catalog-header{height:64px;min-height:64px;max-height:none;box-sizing:border-box;margin:0;padding:0 48px;border-bottom:1px solid var(--astron-sh-border-subtle);display:grid;grid-template-columns:minmax(0,1fr) auto minmax(0,1fr);align-items:center;gap:16px}
    .skillhub-controls.astron-sh-catalog .astron-sh-location{font-size:16px;gap:12px;white-space:nowrap;min-width:0}
    .skillhub-controls.astron-sh-catalog .astron-sh-location>svg{width:20px;height:20px}
    .skillhub-controls.astron-sh-catalog .astron-sh-location>strong{font-size:16px;font-weight:600}
    .skillhub-controls.astron-sh-catalog .astron-sh-location>span{font-size:13px;color:var(--astron-sh-muted);font-weight:400;margin-left:4px}
    .skillhub-controls.astron-sh-catalog .astron-sh-directory-tabs{margin:0;width:max-content;min-width:0;justify-content:center;height:32px;padding:2px;gap:2px;justify-self:center}
    .skillhub-controls.astron-sh-catalog .astron-sh-directory-tabs>.astron-sh-tabs-trigger{height:28px;min-height:28px;padding:0 10px;flex:none}
    .skillhub-controls.astron-sh-catalog .astron-sh-toolbar-actions{justify-self:end;display:flex;align-items:center;gap:4px}
    .skillhub-controls.astron-sh-catalog .astron-sh-toolbar-actions>button{color:var(--astron-sh-link)}
    .skillhub-controls.astron-sh-catalog .astron-sh-toolbar-actions>button[aria-label="返回会话"]{color:var(--astron-sh-muted)}
    .skillhub-controls.astron-sh-catalog .astron-sh-workbench-heading{display:flex;align-items:center;justify-content:space-between;margin-bottom:18px;gap:12px}
    .skillhub-controls.astron-sh-catalog .astron-sh-workbench-heading h1{font-size:20px;line-height:24px;margin:0;letter-spacing:-.3px}
    .skillhub-controls.astron-sh-catalog .astron-sh-workbench-heading p{font-size:13px;line-height:18px;color:var(--astron-sh-secondary);margin:4px 0 0}
    .skillhub-controls.astron-sh-catalog .astron-sh-catalog-source-label{display:inline-flex;align-items:center;gap:8px;font-size:13px;color:var(--astron-sh-secondary);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:35%}
    .skillhub-controls.astron-sh-catalog .astron-sh-search-form{display:flex;align-items:center;margin:0 0 16px;gap:12px}
    .skillhub-controls.astron-sh-catalog .astron-sh-search-field{position:relative;flex:1;min-width:0;min-height:32px}
    .skillhub-controls.astron-sh-catalog .astron-sh-search-field input{height:32px;min-height:32px;font-size:13px;padding-left:34px;width:100%}
    .skillhub-controls.astron-sh-catalog .astron-sh-search-submit{position:absolute;left:2px;top:2px;width:28px;height:28px;min-height:28px;padding:6px;z-index:1;color:var(--astron-sh-muted)}
    .skillhub-controls.astron-sh-catalog .astron-sh-search-form>label{display:block;flex:none;font-size:0}
    .skillhub-controls.astron-sh-catalog .astron-sh-search-form .astron-sh-select-trigger{width:max-content;min-width:74px}
    .skillhub-controls.astron-sh-catalog .astron-sh-catalog-filter-row{display:flex;align-items:center;justify-content:space-between;gap:16px;padding-bottom:12px;border-bottom:1px solid var(--astron-sh-border-subtle)}
    .skillhub-controls.astron-sh-catalog .astron-sh-label-filter{margin:0;gap:8px;flex-wrap:wrap}
    .skillhub-controls.astron-sh-catalog .astron-sh-label-filter button{border:0;border-radius:6px;padding:0 10px;color:var(--astron-sh-secondary);background:transparent}
    .skillhub-controls.astron-sh-catalog .astron-sh-label-filter button[aria-pressed=true]{color:var(--astron-sh-fg);background:var(--astron-sh-soft)}
    .skillhub-controls.astron-sh-catalog .astron-sh-favorite-filter{margin-left:auto;color:var(--astron-sh-link);padding:0;white-space:nowrap;flex:none}
    .skillhub-controls.astron-sh-catalog .astron-sh-result-bar{margin:12px 0;gap:12px}
    .skillhub-controls.astron-sh-catalog .astron-sh-result-summary p{display:flex;align-items:center;gap:20px;margin:0;font-size:12px;line-height:18px}
    .skillhub-controls.astron-sh-catalog .astron-sh-result-summary strong{font-size:13px;color:var(--astron-sh-fg);font-weight:500}
    .skillhub-controls.astron-sh-catalog .astron-sh-load-more{justify-content:space-between;margin-top:24px;min-height:32px;color:var(--astron-sh-muted);font-size:12px}
    .skillhub-controls.astron-sh-catalog .astron-sh-load-more button{color:var(--astron-sh-link);padding:0}
    .skillhub-controls .astron-sh-market-state{display:inline-flex;align-items:center;gap:6px;color:var(--astron-sh-link);font-size:13px;line-height:20px;white-space:nowrap}
    .skillhub-controls .astron-sh-market-state[data-state=unverified]{color:light-dark(#9a6700,#e9c979)}
    .skillhub-controls.astron-sh-catalog .astron-sh-detail-surface{max-width:1128px;margin-inline:auto}
    .skillhub-controls.astron-sh-catalog .astron-sh-detail-back{height:28px;min-height:28px;padding:0;gap:6px;font-size:13px;color:var(--astron-sh-secondary)}
    .skillhub-controls.astron-sh-catalog .astron-sh-detail-heading{display:flex;align-items:flex-start;gap:16px;margin:16px 0 20px;min-height:64px;flex-wrap:wrap}
    .skillhub-controls.astron-sh-catalog .astron-sh-detail-heading>.astron-sh-avatar{width:56px;height:56px;border-radius:12px}
    .skillhub-controls.astron-sh-catalog .astron-sh-detail-heading>.astron-sh-avatar>span{font-size:24px}
    .skillhub-controls.astron-sh-catalog .astron-sh-detail-heading h2{font-size:28px;line-height:34px;margin:0;font-weight:600}
    .skillhub-controls.astron-sh-catalog .astron-sh-detail-heading p{font-size:13px;line-height:18px;margin:8px 0 0;color:var(--astron-sh-muted)}
    .skillhub-controls.astron-sh-catalog .astron-sh-detail-identity{min-width:0;flex:1;overflow-wrap:anywhere}
    .skillhub-controls.astron-sh-catalog .astron-sh-detail-actions{margin-left:auto;margin-top:12px;display:flex;align-items:center;gap:8px;flex-wrap:wrap}
    .skillhub-controls.astron-sh-catalog .astron-sh-detail-actions .astron-sh-button{height:32px;min-height:32px;padding:0 12px}
    .skillhub-controls.astron-sh-catalog .astron-sh-detail-summary{margin:0 0 12px;font-size:15px;line-height:22px;color:var(--astron-sh-secondary)}
    .skillhub-controls.astron-sh-catalog .astron-sh-detail-meta-row{display:flex;align-items:center;justify-content:space-between;gap:16px;min-height:20px;padding-bottom:17px;border-bottom:1px solid var(--astron-sh-border-subtle)}
    .skillhub-controls.astron-sh-catalog .astron-sh-detail-meta-row>.astron-sh-metrics{gap:24px;color:var(--astron-sh-muted);font-size:13px}
    .skillhub-controls.astron-sh-catalog .astron-sh-detail-meta-row>.astron-sh-website-link{margin-left:auto;height:24px;min-height:24px;padding:0;border:0;background:transparent;color:var(--astron-sh-link);gap:6px}
    .skillhub-controls.astron-sh-catalog .astron-sh-detail-layout{display:grid;grid-template-columns:minmax(0,760px) 320px;gap:48px;max-width:1128px;margin:24px auto 0}
    .skillhub-controls.astron-sh-catalog .astron-sh-detail-main{min-width:0;grid-column:1;grid-row:1}
    .skillhub-controls.astron-sh-catalog .astron-sh-detail-aside{min-width:0;grid-column:2;grid-row:1;display:flex;flex-direction:column;gap:16px;position:static}
    .skillhub-controls.astron-sh-catalog .astron-sh-reading-card{border:0;border-radius:0;box-shadow:none;background:transparent;overflow:visible;min-height:0}
    .skillhub-controls.astron-sh-catalog .astron-sh-reading-card .astron-sh-detail-tabbar{padding:0;border-bottom:1px solid var(--astron-sh-border-subtle)}
    .skillhub-controls.astron-sh-catalog .astron-sh-reading-card .astron-sh-tabs-list{height:37px;padding:0;gap:60px;background:transparent;border:0;border-radius:0;overflow-x:auto;overflow-y:hidden}
    .skillhub-controls.astron-sh-catalog .astron-sh-reading-card .astron-sh-tabs-trigger{height:37px;min-height:37px;padding:0;font-size:14px}
    .skillhub-controls.astron-sh-catalog .astron-sh-reading-card .astron-sh-reading-body{padding:30px 0 24px;font-size:15px;line-height:26px}
    .skillhub-controls.astron-sh-catalog .astron-sh-reading-card .astron-sh-panel-footer{padding:12px 0;background:transparent;border-top:1px solid var(--astron-sh-border-subtle)}
    .skillhub-controls.astron-sh-catalog .astron-sh-reading-card .astron-sh-markdown h1{font-size:22px;line-height:30px}
    .skillhub-controls.astron-sh-catalog .astron-sh-reading-card .astron-sh-markdown h2{font-size:16px;line-height:24px;border-bottom:0;padding-bottom:0}
    .skillhub-controls.astron-sh-catalog .astron-sh-install-management{padding:20px;background:var(--astron-sh-surface);border:1px solid var(--astron-sh-border);border-radius:12px}
    .skillhub-controls.astron-sh-catalog .astron-sh-management-heading h3{font-size:16px;line-height:20px}
    .skillhub-controls.astron-sh-catalog .astron-sh-detail-update-check{display:flex;justify-content:flex-end;margin-bottom:-8px}
    .skillhub-controls.astron-sh-catalog .astron-sh-detail-update-check .astron-sh-button{height:24px;min-height:24px;padding:0;color:var(--astron-sh-muted);font-size:12px}

    .skillhub-controls.astron-sh-catalog .astron-sh-detail-layout:has(.astron-sh-reading-card[data-detail-view=files]){grid-template-columns:minmax(0,1fr);gap:0}
    .skillhub-controls.astron-sh-catalog .astron-sh-detail-layout:has(.astron-sh-reading-card[data-detail-view=files])>.astron-sh-detail-main{grid-column:1/-1;max-width:none}
    .skillhub-controls.astron-sh-catalog .astron-sh-detail-layout:has(.astron-sh-reading-card[data-detail-view=files])>.astron-sh-detail-aside{display:none}
    .skillhub-controls.astron-sh-catalog .astron-sh-detail-actions>.astron-sh-manage-installations{color:var(--astron-sh-link);padding-inline:4px}
    .skillhub-controls.astron-sh-catalog .astron-sh-detail-aside:focus-visible{outline:2px solid var(--astron-sh-link);outline-offset:4px}
    @container(max-width:1119px){.skillhub-controls.astron-sh-catalog .astron-sh-detail-layout{grid-template-columns:minmax(0,1fr) 280px;gap:24px}}
    @container(max-width:839px){.skillhub-controls.astron-sh-catalog .astron-sh-location>span{display:none}.skillhub-controls.astron-sh-catalog .astron-sh-catalog-header{padding-inline:24px}.skillhub-controls.astron-sh-catalog .astron-sh-detail-layout{grid-template-columns:minmax(0,1fr) 240px;gap:24px}}
    @container(max-width:679px){.skillhub-controls.astron-sh-catalog .astron-sh-detail-layout{display:flex;flex-direction:column}.skillhub-controls.astron-sh-catalog .astron-sh-detail-aside{order:1}.skillhub-controls.astron-sh-catalog .astron-sh-detail-heading h2{font-size:24px;line-height:30px}.skillhub-controls.astron-sh-catalog .astron-sh-detail-heading>.astron-sh-avatar{width:48px;height:48px}}
    @container(max-width:579px){.skillhub-controls.astron-sh-catalog .astron-sh-workbench-frame{padding:20px 16px 24px}.skillhub-controls.astron-sh-catalog .astron-sh-catalog-header{height:auto;min-height:64px;padding:12px 16px;grid-template-columns:minmax(0,1fr) auto;gap:8px}.skillhub-controls.astron-sh-catalog .astron-sh-directory-tabs{grid-column:1/-1;grid-row:2;justify-self:center}.skillhub-controls.astron-sh-catalog .astron-sh-toolbar-actions{grid-column:2;grid-row:1}.skillhub-controls.astron-sh-catalog .astron-sh-workbench-heading{align-items:flex-start}.skillhub-controls.astron-sh-catalog .astron-sh-catalog-source-label{display:none}.skillhub-controls.astron-sh-catalog .astron-sh-catalog-filter-row{align-items:flex-start;gap:8px}.skillhub-controls.astron-sh-catalog .astron-sh-label-filter{gap:4px}.skillhub-controls.astron-sh-catalog .astron-sh-detail-actions{width:100%;margin-left:0;margin-top:0}.skillhub-controls.astron-sh-catalog .astron-sh-detail-meta-row{flex-wrap:wrap}.skillhub-controls.astron-sh-catalog .astron-sh-reading-card .astron-sh-tabs-list{gap:28px}.skillhub-controls.astron-sh-catalog .astron-sh-detail-heading{gap:12px}.skillhub-controls.astron-sh-catalog .astron-sh-detail-aside{order:1}}
  `}</style>;
}
