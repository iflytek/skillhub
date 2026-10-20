/** Scoped shadcn component styles; never reset the DSH document or other plugins. */
export function NativeControlStyles() {
  return <style>{`
.skillhub-controls {
 --astron-sh-bg:var(--dsw-alias-bg-base,Canvas);--astron-sh-surface:var(--dsw-alias-bg-layer-1,Canvas);
 --astron-sh-fg:var(--dsw-alias-label-primary,CanvasText);--astron-sh-muted:var(--dsw-alias-label-secondary,GrayText);
 --astron-sh-border:var(--dsw-alias-border-l1,color-mix(in srgb,CanvasText 16%,Canvas));
 --astron-sh-accent:light-dark(#515ce5,#a1aaff);--astron-sh-soft:color-mix(in srgb,var(--astron-sh-fg) 4%,var(--astron-sh-bg));
 color:var(--astron-sh-fg);font-family:var(--dsw-font-family,system-ui,-apple-system,BlinkMacSystemFont,'Segoe UI','Microsoft YaHei','PingFang SC',sans-serif);
 font-size:14px;line-height:1.6;box-sizing:border-box;
}
 .skillhub-controls .astron-sh-install-dialog-identity {display:flex;align-items:center;gap:12px;padding:14px 0;border-bottom:1px solid var(--astron-sh-border);margin-bottom:14px}
.skillhub-controls .astron-sh-install-dialog-identity p {margin:2px 0;color:var(--astron-sh-muted);font-size:12px}
.skillhub-controls .astron-sh-install-dialog-control {padding:0;border:0;background:transparent}
 .skillhub-controls .astron-sh-label-filter { display:flex;align-items:center;flex-wrap:wrap;gap:6px;margin:12px 0 4px;font-size:12px;line-height:18px; }
.skillhub-controls .astron-sh-label-filter > span { color:var(--astron-sh-muted);margin-right:4px; }
.skillhub-controls .astron-sh-label-filter button { min-height:28px;height:28px;padding:4px 10px;font-size:12px;border-radius:999px;margin:0;border:1px solid transparent; }
.skillhub-controls .astron-sh-label-filter button[aria-pressed="true"] { color:var(--astron-sh-accent);background:color-mix(in srgb,var(--astron-sh-accent) 10%,var(--astron-sh-bg));border-color:color-mix(in srgb,var(--astron-sh-accent) 30%,var(--astron-sh-border)); }
.skillhub-controls .astron-sh-label-note { font-size:12px;line-height:18px;color:var(--astron-sh-muted); }
.skillhub-controls :where(*, *::before, *::after){box-sizing:border-box}
.skillhub-controls :where(button,input,select,textarea){font:inherit;color:inherit;max-width:100%}
.skillhub-controls :where(button,input,select){border:1px solid var(--astron-sh-border);background:var(--astron-sh-bg);border-radius:7px;padding:8px 12px}
.skillhub-controls :where(button){cursor:pointer;min-height:34px;font-size:13px;line-height:20px;transition:background-color .15s,border-color .15s,box-shadow .15s}
.skillhub-controls :where(button):not(:disabled):hover{background:var(--astron-sh-soft)}
.skillhub-controls :where(button):disabled{opacity:.5;cursor:not-allowed}
.skillhub-controls :where(button,input,select,textarea):focus-visible{outline:2px solid var(--astron-sh-accent);outline-offset:3px}
.skillhub-controls :where(h1,h2,h3,p){margin-top:0}
.skillhub-controls :where(h1){font-size:25px;line-height:1.4;font-weight:600}
.skillhub-controls :where(h2){font-size:22px;line-height:1.4;font-weight:600}
.skillhub-controls :where(h3){font-size:16px;font-weight:600}
.skillhub-controls .astron-sh-button{display:inline-flex;align-items:center;justify-content:center;gap:7px;white-space:nowrap;font-weight:500}
.skillhub-controls .astron-sh-button-default{background:var(--astron-sh-accent);color:light-dark(#fff,#171b38);border-color:transparent}
.skillhub-controls .astron-sh-button-default:not(:disabled):hover{background:color-mix(in srgb,var(--astron-sh-accent) 88%,var(--astron-sh-fg))}
.skillhub-controls .astron-sh-button-ghost{border-color:transparent;background:transparent;box-shadow:none}
.skillhub-controls .astron-sh-button-destructive,.skillhub-controls .skillhub-danger{color:light-dark(#b42318,#ffb4ab)}
.skillhub-controls .astron-sh-input{height:40px;min-width:0;background:var(--astron-sh-soft)}
.skillhub-controls .astron-sh-catalog-header{display:flex;align-items:start;justify-content:space-between;gap:16px;margin-bottom:24px;flex-wrap:wrap}
.skillhub-controls .astron-sh-catalog-header h1{margin:0 0 6px}.skillhub-controls .astron-sh-source{font-size:12px;color:var(--astron-sh-muted);overflow-wrap:anywhere;margin:0}
.skillhub-controls.astron-sh-catalog{height:100%;overflow:auto;padding:28px 32px;container-type:inline-size}
.skillhub-controls .astron-sh-tabs-list{display:flex;gap:26px;border-bottom:1px solid var(--astron-sh-border);margin-bottom:22px;overflow-x:auto}
.skillhub-controls .astron-sh-tabs-trigger{border:0;border-radius:0;background:transparent;padding:11px 0;font-size:14px;white-space:nowrap;color:var(--astron-sh-muted)}
.skillhub-controls .astron-sh-tabs-trigger[data-state=active]{color:var(--astron-sh-fg);box-shadow:inset 0 -2px var(--astron-sh-accent)}
.skillhub-controls .astron-sh-search-form{display:flex;gap:8px;margin-bottom:20px;align-items:center;flex-wrap:wrap}.skillhub-controls .astron-sh-search-field{flex:1;min-width:180px;position:relative}
.skillhub-controls .astron-sh-search-field input{width:100%;padding-left:38px}.skillhub-controls .astron-sh-search-field>svg{position:absolute;left:12px;top:12px;width:16px;height:16px;color:var(--astron-sh-muted)}
.skillhub-controls .astron-sh-result-bar{display:flex;justify-content:space-between;align-items:center;gap:12px;margin-bottom:16px;flex-wrap:wrap;font-size:12px;color:var(--astron-sh-muted)}
.skillhub-controls .astron-sh-result-bar p{margin:0}.skillhub-controls .astron-sh-result-bar label{display:flex;align-items:center;gap:8px}.skillhub-controls .astron-sh-result-bar select{padding:5px 9px;font-size:12px}
.skillhub-controls .astron-sh-card-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:18px}
.skillhub-controls .astron-sh-card{border:1px solid var(--astron-sh-border);border-radius:11px;background:var(--astron-sh-bg)}
.skillhub-controls .astron-sh-skill-card{height:232px;display:flex;flex-direction:column;position:relative;min-width:0;transition:border-color .15s,box-shadow .15s}
.skillhub-controls .astron-sh-skill-card:hover{border-color:color-mix(in srgb,var(--astron-sh-accent) 35%,var(--astron-sh-border));box-shadow:0 4px 18px color-mix(in srgb,var(--astron-sh-fg) 5%,transparent)}
.skillhub-controls .astron-sh-skill-card:focus-within{outline:2px solid var(--astron-sh-accent);outline-offset:2px}
.skillhub-controls .astron-sh-skill-heading{display:flex;gap:12px;align-items:center;padding:17px 18px 12px;min-width:0}.skillhub-controls .astron-sh-skill-heading>div{min-width:0}
.skillhub-controls .astron-sh-skill-heading h3{font-size:16px;line-height:22px;margin:0;font-weight:600;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.skillhub-controls .astron-sh-skill-heading p{font-size:12px;line-height:18px;margin:4px 0 0;color:var(--astron-sh-muted);overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.skillhub-controls .astron-sh-avatar{display:inline-flex;width:48px;height:48px;border-radius:11px;overflow:hidden;flex-shrink:0;--astron-sh-avatar-tint:var(--astron-sh-accent)}
.skillhub-controls .astron-sh-avatar img{width:100%;height:100%;object-fit:cover}.skillhub-controls .astron-sh-avatar>span{display:flex;width:100%;height:100%;align-items:center;justify-content:center;font-size:21px;font-weight:500;color:color-mix(in srgb,var(--astron-sh-avatar-tint) 65%,var(--astron-sh-fg));background:color-mix(in srgb,var(--astron-sh-avatar-tint) 9%,var(--astron-sh-bg));border:1px solid color-mix(in srgb,var(--astron-sh-avatar-tint) 15%,var(--astron-sh-border));border-radius:inherit}
.skillhub-controls .astron-sh-tone-1{--astron-sh-avatar-tint:#8a75ad}.skillhub-controls .astron-sh-tone-2{--astron-sh-avatar-tint:#688e79}.skillhub-controls .astron-sh-tone-3{--astron-sh-avatar-tint:#ad8064}.skillhub-controls .astron-sh-tone-4{--astron-sh-avatar-tint:#a4758b}.skillhub-controls .astron-sh-tone-5{--astron-sh-avatar-tint:#678b9d}
.skillhub-controls .astron-sh-skill-body{padding:0 18px 12px}.skillhub-controls .astron-sh-skill-summary{font-size:14px;line-height:21px;margin:0;min-height:42px;max-height:42px;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden;color:color-mix(in srgb,var(--astron-sh-fg) 85%,var(--astron-sh-bg))}
.skillhub-controls .astron-sh-skill-meta{display:flex;align-items:center;gap:8px;font-size:12px;line-height:18px;color:var(--astron-sh-muted);margin-top:10px;min-width:0}.skillhub-controls .astron-sh-skill-meta>span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.skillhub-controls .astron-sh-skill-meta>span:first-child{margin-right:auto}
.skillhub-controls .astron-sh-installation-status{display:inline-block;min-width:0;max-width:100%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;vertical-align:middle;font-size:11px;line-height:16px;padding:1px 6px;border-radius:5px;background:var(--astron-sh-soft);color:var(--astron-sh-muted)}
.skillhub-controls .astron-sh-skill-meta>.astron-sh-installation-status{max-width:76%}
.skillhub-controls .astron-sh-installation-status[data-tone=success]{background:light-dark(#eaf6ee,#19392c);color:light-dark(#187847,#7dddab)}
.skillhub-controls .astron-sh-installation-status[data-tone=warning]{background:light-dark(#fff3df,#3e3020);color:light-dark(#945414,#f0c27c)}
.skillhub-controls .astron-sh-skill-card>.astron-sh-card-footer{margin-top:auto;padding:10px 17px;min-height:43px;border-top:1px solid var(--astron-sh-border);display:flex;justify-content:space-between;align-items:center;gap:8px;flex-wrap:wrap}
.skillhub-controls .astron-sh-metrics{display:flex;gap:12px;align-items:center;font-size:12px;line-height:18px;color:var(--astron-sh-muted);font-variant-numeric:tabular-nums}.skillhub-controls .astron-sh-metrics>span{display:inline-flex;align-items:center;gap:4px;white-space:nowrap}
.skillhub-controls .astron-sh-metrics svg,.skillhub-controls .astron-sh-card-open svg{width:13px;height:13px;flex-shrink:0}
.skillhub-controls .astron-sh-card-open{position:absolute;inset:0;width:100%;height:100%;border:0;border-radius:11px;background:transparent;padding:0;z-index:1}
.skillhub-controls .astron-sh-card-open:not(:disabled):hover{background:transparent}
.skillhub-controls .astron-sh-card-open:focus-visible{outline:2px solid var(--astron-sh-accent);outline-offset:2px}
.skillhub-controls .astron-sh-card-actions{position:relative;z-index:2;display:flex;align-items:center;gap:6px;flex-wrap:wrap;justify-content:flex-end}
.skillhub-controls .astron-sh-card-actions button{height:28px;min-height:28px;padding:3px 9px;font-size:12px}
.skillhub-controls .astron-sh-card-actions .astron-sh-select-trigger{min-width:86px;gap:6px}
.skillhub-controls .astron-sh-skill-card>.astron-sh-card-footer{min-height:43px;height:auto}


.skillhub-controls .astron-sh-load-more{display:flex;justify-content:center;align-items:center;gap:16px;margin-top:24px;font-size:12px;color:var(--astron-sh-muted)}
.skillhub-controls .astron-sh-empty,.skillhub-controls .astron-sh-error{padding:24px;border:1px solid var(--astron-sh-border);border-radius:10px;background:var(--astron-sh-soft);margin-block:16px}.skillhub-controls .astron-sh-empty{text-align:center}
.skillhub-controls .astron-sh-detail-layout{display:grid;grid-template-columns:minmax(0,1fr) 290px;gap:32px;margin-top:24px}.skillhub-controls .astron-sh-detail-main{min-width:0;grid-column:1;grid-row:1}.skillhub-controls .astron-sh-detail-aside{grid-column:2;grid-row:1;min-width:0}
.skillhub-controls .astron-sh-detail-heading{display:flex;align-items:center;gap:16px;margin:20px 0}.skillhub-controls .astron-sh-detail-heading h2{margin:0 0 5px}.skillhub-controls .astron-sh-detail-heading p{font-size:12px;color:var(--astron-sh-muted);margin:0;overflow-wrap:anywhere}
.skillhub-controls .astron-sh-detail-summary{font-size:14px;line-height:22px;color:var(--astron-sh-muted);margin-bottom:24px}
.skillhub-controls .astron-sh-documentation{font-size:14px;line-height:1.8;overflow-wrap:anywhere}.skillhub-controls .astron-sh-documentation h2{font-size:19px;margin-top:24px}.skillhub-controls .astron-sh-documentation h3{font-size:16px;margin-top:20px}.skillhub-controls .astron-sh-documentation pre{max-width:100%}.skillhub-controls .astron-sh-documentation table{display:block;max-width:100%;overflow:auto}.skillhub-controls .astron-sh-documentation img{max-width:100%}
.skillhub-controls .astron-sh-install-panel{border:1px solid var(--astron-sh-border);border-radius:10px;padding:20px;background:var(--astron-sh-soft);margin-block:16px}.skillhub-controls .astron-sh-detail-aside>.astron-sh-install-panel{margin-top:0}.skillhub-controls .astron-sh-install-panel h3{font-size:18px;margin:0 0 14px}.skillhub-controls .astron-sh-install-panel>button{margin-top:12px;width:100%}
.skillhub-controls .astron-sh-scope-field{display:grid;gap:8px;font-size:12px}.skillhub-controls .astron-sh-scope-field select{width:100%}.skillhub-controls .astron-sh-install-panel p{font-size:13px;overflow-wrap:anywhere}
.skillhub-controls .astron-sh-dialog-overlay{position:fixed;inset:0;z-index:1000;background:color-mix(in srgb,#000 40%,transparent)}
.skillhub-controls .astron-sh-dialog{position:fixed;z-index:1001;top:50%;left:50%;transform:translate(-50%,-50%);width:min(460px,calc(100vw - 32px));max-height:calc(100dvh - 32px);overflow:auto;border:1px solid var(--astron-sh-border);border-radius:12px;padding:24px;background:var(--astron-sh-bg);color:var(--astron-sh-fg);box-shadow:0 16px 60px #0003}
.skillhub-controls .astron-sh-dialog h2{font-size:18px}.skillhub-controls .astron-sh-dialog p{font-size:14px;color:var(--astron-sh-muted)}.skillhub-controls .astron-sh-dialog dl{font-size:13px;padding:14px;background:var(--astron-sh-soft);border-radius:7px;overflow-wrap:anywhere}.skillhub-controls .astron-sh-dialog dt{color:var(--astron-sh-muted)}.skillhub-controls .astron-sh-dialog dd{margin:0 0 8px}.skillhub-controls .astron-sh-dialog-actions{display:flex;justify-content:flex-end;gap:8px;margin-top:20px}
.skillhub-controls .astron-sh-install-panel .astron-sh-scope-help{font-size:12px;line-height:1.5;margin:7px 0 16px;color:var(--astron-sh-muted)}
.skillhub-controls .astron-sh-current-install{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:12px;border:1px solid var(--astron-sh-border);border-radius:8px;background:var(--astron-sh-bg);margin:12px 0}
.skillhub-controls .astron-sh-current-install strong{font-size:13px;font-weight:500;color:light-dark(#218650,#7dddab)}
.skillhub-controls .astron-sh-current-install[data-state]:not([data-state=present]) strong{color:light-dark(#a76917,#f0c27c)}
.skillhub-controls .astron-sh-current-install button{min-height:28px;height:28px;padding:3px 10px;font-size:12px}
.skillhub-controls .astron-sh-other-install{font-size:12px;color:var(--astron-sh-muted);margin:12px 0}
.skillhub-controls .astron-sh-removal-identity{display:flex;align-items:center;gap:12px;margin:22px 0 18px;min-width:0}
.skillhub-controls .astron-sh-removal-identity>div{min-width:0;overflow-wrap:anywhere}
.skillhub-controls .astron-sh-removal-identity strong{font-size:15px;font-weight:600}
.skillhub-controls .astron-sh-removal-identity p{font-size:12px;margin:3px 0 0;overflow-wrap:anywhere}
.skillhub-controls .astron-sh-removal-scope{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:12px 14px;background:var(--astron-sh-soft);border-radius:8px;font-size:12px}
.skillhub-controls .astron-sh-removal-scope>span{color:var(--astron-sh-muted)}
.skillhub-controls .astron-sh-removal-scope strong{font-weight:500;font-size:13px}
.skillhub-controls .astron-sh-removal-dialog .astron-sh-removal-impact{margin:12px 0 0;font-size:12px;line-height:1.7}
.skillhub-controls .astron-sh-removal-dialog .astron-sh-dialog-actions button{min-height:32px;padding:5px 14px;font-size:13px}
.skillhub-controls .astron-sh-removal-dialog .astron-sh-dialog-actions .astron-sh-button-destructive{background:light-dark(#be3027,#f28b82);color:light-dark(#fff,#3b1410);border-color:transparent}
.skillhub-controls .astron-sh-removal-dialog .astron-sh-dialog-actions .astron-sh-button-destructive:not(:disabled):hover{filter:brightness(.94)}
.skillhub-controls .astron-sh-removal-status{margin-top:16px;font-size:13px}
.skillhub-controls .astron-sh-upgrade-versions{display:flex;align-items:center;justify-content:space-between;gap:12px;background:var(--astron-sh-soft);border-radius:8px;padding:14px;font-size:16px}
.skillhub-controls .astron-sh-upgrade-versions>strong{color:var(--astron-sh-accent);font-weight:600}
.skillhub-controls .astron-sh-upgrade-dialog .astron-sh-removal-impact{font-size:12px;margin:12px 0 0;line-height:1.7}
.skillhub-controls .astron-sh-version-action>button{width:100%;margin-top:4px}
.skillhub-controls .astron-sh-version-action>p{font-size:12px;line-height:1.7;margin:10px 0 0;color:var(--astron-sh-muted)}
/* Installation stays in one modal; the right column appears after submission. */
.skillhub-controls .astron-sh-dialog.astron-sh-dialog-expanded{width:min(800px,calc(100vw - 32px));left:clamp(16px,calc(50% - 230px),calc(100vw - 816px));transform:translateY(-50%)}
.skillhub-controls .astron-sh-install-dialog-layout{display:grid;grid-template-columns:minmax(0,1fr);gap:24px;margin-top:18px}
.skillhub-controls .astron-sh-dialog-expanded .astron-sh-install-dialog-layout{grid-template-columns:minmax(0,1fr) minmax(0,1fr)}
.skillhub-controls .astron-sh-install-dialog-layout>div{min-width:0}
.skillhub-controls .astron-sh-dialog .astron-sh-install-dialog-control{padding:0;border:0;background:transparent;margin:0}
.skillhub-controls .astron-sh-install-dialog-layout>aside{min-width:0;border-left:1px solid var(--astron-sh-border);padding-left:24px}
.skillhub-controls .astron-sh-dialog .astron-sh-install-confirmed-scope{padding:12px 14px;margin:16px 0}
.skillhub-controls .astron-sh-install-confirmed-scope dd{margin:4px 0 0;font-weight:500}
.skillhub-controls .astron-sh-dialog .astron-sh-install-close-note{font-size:12px;line-height:1.7}
.skillhub-controls .astron-sh-install-progress-item{border:1px solid var(--astron-sh-border);border-radius:10px;background:var(--astron-sh-soft);padding:18px;min-height:200px}
.skillhub-controls .astron-sh-install-progress-item>header{display:flex;align-items:center;gap:10px;margin-bottom:24px}
.skillhub-controls .astron-sh-install-progress-item>header>div{min-width:0;overflow-wrap:anywhere}
.skillhub-controls .astron-sh-install-progress-item>header strong{font-size:13px}
.skillhub-controls .astron-sh-install-progress-item>header p{margin:3px 0 0;font-size:12px}
.skillhub-controls .astron-sh-install-progress>strong{font-size:14px;font-weight:600}
.skillhub-controls .astron-sh-install-progress>p{font-size:12px;line-height:1.7;margin:10px 0 0}
.skillhub-controls .astron-sh-install-progress-track{height:6px;background:color-mix(in srgb,var(--astron-sh-accent) 12%,var(--astron-sh-bg));border-radius:999px;overflow:hidden;margin-top:14px}
.skillhub-controls .astron-sh-install-progress-track>span{display:block;width:38%;height:100%;border-radius:inherit;background:var(--astron-sh-accent);animation:astron-sh-installing 1.4s ease-in-out infinite}
.skillhub-controls .astron-sh-install-progress-track[data-state=complete]>span{width:100%;animation:none;background:light-dark(#218650,#7dddab)}
.skillhub-controls .astron-sh-install-progress-track[data-state=stopped]>span{animation:none;background:light-dark(#a76917,#f0c27c)}
@keyframes astron-sh-installing{0%,100%{transform:translateX(0)}50%{transform:translateX(160%)}}
@media(max-width:640px){.skillhub-controls .astron-sh-dialog-expanded .astron-sh-install-dialog-layout{grid-template-columns:minmax(0,1fr)}.skillhub-controls .astron-sh-install-dialog-layout>aside{border-left:0;border-top:1px solid var(--astron-sh-border);padding-left:0;padding-top:18px}}
.skillhub-controls .astron-sh-chat-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,260px),1fr));gap:12px;padding:0;list-style:none}.skillhub-controls .astron-sh-chat-results>li{min-width:0}
.skillhub-controls.astron-sh-detail-drawer{background:var(--astron-sh-bg);color:var(--astron-sh-fg)}
@container(max-width:1000px){.skillhub-controls .astron-sh-card-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.skillhub-controls .astron-sh-detail-layout{grid-template-columns:minmax(0,1fr) 260px;gap:24px}}
@container(max-width:680px){.skillhub-controls .astron-sh-card-grid{grid-template-columns:1fr}.skillhub-controls .astron-sh-detail-layout{display:flex;flex-direction:column}.skillhub-controls .astron-sh-load-more{flex-wrap:wrap}.skillhub-controls .astron-sh-skill-heading h3{font-size:16px}}
@media(prefers-reduced-motion:reduce){.skillhub-controls :where(*){transition:none!important;animation:none!important}}

.skillhub-controls .astron-sh-catalog-header{min-height:54px;margin-bottom:20px;border-bottom:1px solid var(--astron-sh-border);padding-bottom:12px;gap:12px}
.skillhub-controls .astron-sh-location{display:flex;align-items:center;gap:12px;font-size:13px}.skillhub-controls .astron-sh-location strong{font-size:15px;font-weight:600}.skillhub-controls .astron-sh-location>span{color:var(--astron-sh-muted)}.skillhub-controls .astron-sh-location>svg{color:var(--astron-sh-accent)}
.skillhub-controls .astron-sh-toolbar-actions{display:flex;gap:6px}.skillhub-controls .astron-sh-icon-button{height:32px;min-height:32px;width:32px;padding:6px}.skillhub-controls .astron-sh-source-info{padding:10px 14px;border-radius:7px;background:var(--astron-sh-soft);font-size:12px;color:var(--astron-sh-muted);overflow-wrap:anywhere}
.skillhub-controls .astron-sh-skill-card{height:auto;min-height:210px}.skillhub-controls .astron-sh-avatar{width:38px;height:38px;border-radius:9px}.skillhub-controls .astron-sh-avatar>span{font-size:19px}.skillhub-controls .astron-sh-skill-heading{padding:15px 16px 10px;gap:11px}.skillhub-controls .astron-sh-skill-heading h3{font-size:15px;line-height:20px;font-weight:600}.skillhub-controls .astron-sh-skill-heading p{font-size:12px;line-height:18px;margin-top:3px}.skillhub-controls .astron-sh-skill-body{padding:0 16px 10px}.skillhub-controls .astron-sh-skill-summary{font-size:13px;line-height:20px;min-height:40px;max-height:40px}.skillhub-controls .astron-sh-skill-meta{margin-top:8px;font-size:12px;line-height:18px}.skillhub-controls .astron-sh-skill-card>.astron-sh-card-footer{padding:7px 15px;min-height:43px;height:auto}.skillhub-controls .astron-sh-metrics{font-size:12px;gap:11px}.skillhub-controls .astron-sh-metrics svg{width:12px;height:12px;color:color-mix(in srgb,var(--astron-sh-accent) 60%,var(--astron-sh-muted))}.skillhub-controls .astron-sh-card-open{color:var(--astron-sh-accent);font-size:12px}.skillhub-controls .astron-sh-detail-heading .astron-sh-avatar{width:46px;height:46px}
.skillhub-controls .astron-sh-select-root{display:inline-flex;min-width:0}.skillhub-controls .astron-sh-select-trigger{width:100%;min-width:120px;display:flex;align-items:center;justify-content:space-between;gap:12px;font-size:13px;height:38px;border:1px solid var(--astron-sh-border);border-radius:7px;background:var(--astron-sh-bg);padding:8px 11px;text-align:start}.skillhub-controls .astron-sh-select-trigger>svg{width:14px;height:14px;color:var(--astron-sh-muted)}
.skillhub-controls .astron-sh-select-content{z-index:1100;max-height:var(--radix-select-content-available-height);min-width:var(--radix-select-trigger-width);overflow:hidden;border:1px solid var(--astron-sh-border);border-radius:8px;background:var(--astron-sh-bg);color:var(--astron-sh-fg);box-shadow:0 7px 24px #0002;padding:4px;font-size:13px}.skillhub-controls .astron-sh-select-item{display:flex;justify-content:space-between;align-items:center;gap:16px;padding:8px 10px;border-radius:5px;cursor:default;outline:none}.skillhub-controls .astron-sh-select-item[data-disabled]{opacity:.45;cursor:not-allowed}.skillhub-controls .astron-sh-select-item[data-highlighted]{background:var(--astron-sh-soft);color:var(--astron-sh-accent)}.skillhub-controls .astron-sh-select-item[data-state=checked]{color:var(--astron-sh-accent);font-weight:500}.skillhub-controls .astron-sh-select-scroll{display:flex;justify-content:center;height:20px;color:var(--astron-sh-muted)}.skillhub-controls .astron-sh-scope-field>.astron-sh-select-root{width:100%}
.skillhub-controls .astron-sh-reading-card{overflow:hidden;border-radius:11px;box-shadow:0 2px 5px #00000004}.skillhub-controls .astron-sh-reading-card .astron-sh-tabs-list{margin:0;padding:0 24px;gap:24px;height:50px;border-bottom:1px solid var(--astron-sh-border)}.skillhub-controls .astron-sh-reading-card .astron-sh-tabs-trigger{height:50px;padding:14px 0;font-size:13px;transition:none}.skillhub-controls .astron-sh-reading-card .astron-sh-tabs-trigger[data-state=active]{color:var(--astron-sh-accent)}.skillhub-controls .astron-sh-reading-card [role=tabpanel]{min-height:440px;display:flex;flex-direction:column;outline-offset:-3px}.skillhub-controls .astron-sh-reading-card [role=tabpanel][hidden]{display:none}.skillhub-controls .astron-sh-reading-body{padding:24px;flex:1;min-width:0;font-size:13px;line-height:23px}.skillhub-controls .astron-sh-reading-body>p{font-size:13px;line-height:23px}.skillhub-controls .astron-sh-reading-body h2{font-size:17px;line-height:25px}.skillhub-controls .astron-sh-reading-body h3{font-size:14px;line-height:22px}
.skillhub-controls .astron-sh-panel-footer{min-height:49px;margin-top:auto;padding:9px 24px;display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:10px;border-top:1px solid var(--astron-sh-border);background:var(--astron-sh-soft);font-size:11px;color:var(--astron-sh-muted)}.skillhub-controls .astron-sh-panel-footer>div{display:flex;gap:8px;align-items:center}.skillhub-controls .astron-sh-panel-footer button{white-space:nowrap;font-size:12px;min-height:30px;padding:4px 8px}
.skillhub-controls .astron-sh-review-overview{display:flex;justify-content:space-between;align-items:center;padding-bottom:22px;border-bottom:1px solid var(--astron-sh-border);gap:12px}.skillhub-controls .astron-sh-review-overview>div{display:flex;align-items:center;gap:16px}.skillhub-controls .astron-sh-review-overview strong{font-size:32px;line-height:40px;font-weight:600}.skillhub-controls .astron-sh-review-overview p{font-size:11px;color:var(--astron-sh-muted);margin:6px 0 0}.skillhub-controls .astron-sh-read-only{white-space:nowrap;font-size:11px;background:var(--astron-sh-soft);border:1px solid var(--astron-sh-border);padding:1px 7px;border-radius:5px;color:var(--astron-sh-muted)}
.skillhub-controls .astron-sh-stars{display:inline-flex;align-items:center;gap:3px;color:var(--astron-sh-border);margin:7px 0}.skillhub-controls .astron-sh-stars svg{width:13px;height:13px}.skillhub-controls .astron-sh-stars [data-filled=true] svg{color:#b88d35;fill:currentColor}.skillhub-controls .astron-sh-rating-number{font-size:11px;color:var(--astron-sh-muted);margin-left:6px}.skillhub-controls .astron-sh-review{display:flex;align-items:flex-start;gap:12px;padding:22px 0;border-bottom:1px solid var(--astron-sh-border)}.skillhub-controls .astron-sh-review>.astron-sh-avatar{width:32px;height:32px;border-radius:50%}.skillhub-controls .astron-sh-review>.astron-sh-avatar>span{font-size:14px}.skillhub-controls .astron-sh-review>div{flex:1;min-width:0}.skillhub-controls .astron-sh-review header{display:flex;justify-content:space-between;gap:12px;align-items:center}.skillhub-controls .astron-sh-review header strong{font-size:13px;font-weight:500}.skillhub-controls .astron-sh-review time{font-size:11px;color:var(--astron-sh-muted);white-space:nowrap}.skillhub-controls .astron-sh-review-text{white-space:pre-wrap;overflow-wrap:anywhere;font-size:13px;line-height:24px;margin:5px 0}.skillhub-controls .astron-sh-review-note{font-size:11px;line-height:18px;color:var(--astron-sh-muted);display:flex;gap:7px;align-items:flex-start;margin:18px 0 0}.skillhub-controls .astron-sh-review-note svg{width:12px;height:12px;margin-top:3px;flex-shrink:0}
.skillhub-controls .astron-sh-module-empty,.skillhub-controls .astron-sh-audit-empty{display:flex;flex-direction:column;align-items:center;text-align:center;justify-content:center;padding:34px 24px;color:var(--astron-sh-muted);gap:10px}.skillhub-controls .astron-sh-audit-empty>svg,.skillhub-controls .astron-sh-module-empty>svg{width:28px;height:28px;color:var(--astron-sh-accent);margin-bottom:8px}.skillhub-controls .astron-sh-audit-empty p{max-width:420px}.skillhub-controls .astron-sh-module-empty h3,.skillhub-controls .astron-sh-audit-empty h3{color:var(--astron-sh-fg);margin:0}
.skillhub-controls .astron-sh-version-row{display:flex;gap:15px;padding:4px 0 22px}.skillhub-controls .astron-sh-version-row>div{min-width:0;flex:1}.skillhub-controls .astron-sh-version-row h3{margin:0 0 8px;display:flex;gap:10px;align-items:center;flex-wrap:wrap}.skillhub-controls .astron-sh-version-dot{width:9px;height:9px;flex-shrink:0;border-radius:50%;background:var(--astron-sh-accent);margin-top:7px}.skillhub-controls .astron-sh-version-current{font-size:10px;font-weight:400;padding:0 6px;border-radius:4px;background:var(--astron-sh-soft);color:var(--astron-sh-accent)}.skillhub-controls .astron-sh-version-meta{font-size:11px;line-height:18px;color:var(--astron-sh-muted);margin-top:9px}
@container(max-width:680px){.skillhub-controls .astron-sh-skill-card{height:auto;min-height:210px}.skillhub-controls .astron-sh-reading-card [role=tabpanel]{min-height:0}.skillhub-controls .astron-sh-reading-card .astron-sh-tabs-list{padding-inline:18px;gap:18px}.skillhub-controls .astron-sh-reading-body{padding:20px 18px}.skillhub-controls .astron-sh-panel-footer{padding-inline:18px}.skillhub-controls .astron-sh-review header{flex-direction:column;align-items:flex-start;gap:3px}.skillhub-controls .astron-sh-location{gap:8px;font-size:12px}}
@media(max-width:680px){.skillhub-controls.astron-sh-detail-drawer .astron-sh-reading-card [role=tabpanel]{min-height:0}.skillhub-controls.astron-sh-detail-drawer .astron-sh-reading-body{padding:20px 18px}.skillhub-controls.astron-sh-detail-drawer .astron-sh-review header{flex-direction:column;align-items:flex-start;gap:3px}}

.skillhub-controls .astron-sh-catalog-header{height:54px;min-height:54px;max-height:54px;align-items:center;flex-wrap:nowrap;padding-bottom:12px}
.skillhub-controls .astron-sh-location{flex:1 1 auto;min-width:0;width:auto;white-space:nowrap;line-height:20px;gap:12px}
.skillhub-controls .astron-sh-location>svg{width:16px;height:16px;min-width:16px;flex:0 0 16px;display:block}
.skillhub-controls .astron-sh-location>strong,.skillhub-controls .astron-sh-location>span{white-space:nowrap;flex-shrink:0}
.skillhub-controls .astron-sh-location>span:last-child{min-width:0;flex:0 1 auto;overflow:hidden;text-overflow:ellipsis}
.skillhub-controls .astron-sh-toolbar-actions{flex:0 0 auto;white-space:nowrap}.skillhub-controls .astron-sh-icon-button{flex:0 0 32px}.skillhub-controls .astron-sh-icon-button>svg{width:16px;height:16px;flex:0 0 16px}
.skillhub-controls .astron-sh-directory-tabs{display:inline-flex;align-items:center;width:fit-content;max-width:100%;height:38px;padding:3px;gap:4px;border:1px solid var(--astron-sh-border);border-radius:9px;background:var(--astron-sh-soft);margin-bottom:20px;overflow-x:auto}
.skillhub-controls .astron-sh-directory-tabs>.astron-sh-tabs-trigger{height:30px;min-height:30px;padding:4px 16px;line-height:20px;font-size:13px;white-space:nowrap;border:0;border-radius:6px;box-shadow:none;transition:none}
.skillhub-controls .astron-sh-directory-tabs>.astron-sh-tabs-trigger[data-state=active]{background:var(--astron-sh-bg);color:var(--astron-sh-accent);box-shadow:0 1px 3px #00000012}
/* Reading tabs use text and underline feedback, independently of directory segments. */
.skillhub-controls .astron-sh-reading-card .astron-sh-tabs-trigger:not(:disabled):hover{background:transparent;color:var(--astron-sh-accent)}
.skillhub-controls .astron-sh-reading-card .astron-sh-tabs-trigger[data-state=inactive]:not(:disabled):hover{box-shadow:inset 0 -1px color-mix(in srgb,var(--astron-sh-accent) 45%,transparent)}
.skillhub-controls .astron-sh-reading-card .astron-sh-tabs-trigger[data-state=active]{background:transparent;box-shadow:inset 0 -2px var(--astron-sh-accent)}
.skillhub-controls .astron-sh-reading-card .astron-sh-tabs-trigger:focus-visible{outline:2px solid var(--astron-sh-accent);outline-offset:-3px}

.skillhub-controls .astron-sh-skill-card,.skillhub-controls .astron-sh-installed-card{background:color-mix(in srgb,var(--astron-sh-bg) 96%,var(--astron-sh-accent));border-color:color-mix(in srgb,var(--astron-sh-fg) 22%,var(--astron-sh-bg));box-shadow:0 2px 6px color-mix(in srgb,var(--astron-sh-fg) 6%,transparent)}
.skillhub-controls .astron-sh-skill-card:hover,.skillhub-controls .astron-sh-installed-card:hover{border-color:color-mix(in srgb,var(--astron-sh-accent) 55%,var(--astron-sh-border));box-shadow:0 4px 14px color-mix(in srgb,var(--astron-sh-fg) 10%,transparent)}
.skillhub-controls .astron-sh-installed-meta{display:flex;gap:10px;justify-content:space-between;flex-wrap:wrap;font-size:12px;color:var(--astron-sh-muted);margin-bottom:9px}.skillhub-controls .astron-sh-state-badge{display:inline-block;border:1px solid var(--astron-sh-border);padding:1px 7px;border-radius:5px;font-size:11px;background:var(--astron-sh-bg)}.skillhub-controls .astron-sh-state-badge[data-state=present]{color:light-dark(#246b52,#9adbc2);background:light-dark(#eaf5ef,#183a30);border-color:light-dark(#c8e3d5,#31574a)}
.skillhub-controls .astron-sh-history{border-top:1px solid var(--astron-sh-border);margin-top:22px;padding-top:16px}.skillhub-controls .astron-sh-history>h3{font-size:13px;font-weight:500;color:var(--astron-sh-muted);margin:0 0 10px}.skillhub-controls .astron-sh-history-entry{border:1px solid var(--astron-sh-border);border-radius:8px;background:var(--astron-sh-bg);margin-bottom:8px}.skillhub-controls .astron-sh-history-entry>summary{padding:12px 14px;font-size:12px;cursor:pointer;overflow-wrap:anywhere}.skillhub-controls .astron-sh-history-entry>summary>strong{font-weight:600}.skillhub-controls .astron-sh-history-entry>summary>span{margin-left:14px;color:var(--astron-sh-muted)}.skillhub-controls .astron-sh-history-entry>section{border:0!important;margin:0!important;border-top:1px solid var(--astron-sh-border)!important;border-radius:0!important}
.skillhub-controls .astron-sh-operation-fold{border-top:1px solid var(--astron-sh-border);padding-top:12px;margin-top:16px;font-size:12px}.skillhub-controls .astron-sh-operation-fold>summary,.skillhub-controls .astron-sh-check-time>summary{cursor:pointer;color:var(--astron-sh-muted)}.skillhub-controls .astron-sh-operation-fold>p{font-size:12px}.skillhub-controls .astron-sh-current-install{margin-top:16px}.skillhub-controls .astron-sh-current-install strong{font-size:14px;font-weight:600}.skillhub-controls .astron-sh-current-install p{font-size:12px;color:var(--astron-sh-muted);margin:7px 0}.skillhub-controls .astron-sh-install-state{color:var(--astron-sh-muted);font-size:12px;margin-top:14px}.skillhub-controls .astron-sh-check-time{margin-top:6px;font-size:11px}.skillhub-controls .astron-sh-version-row button{margin-top:10px;font-size:12px;min-height:30px;padding:4px 10px}
  @keyframes astron-sh-skeleton-pulse{0%,100%{opacity:1}50%{opacity:.45}}
.skillhub-controls .astron-sh-skeleton{height:12px;min-width:0;border-radius:5px;background:color-mix(in srgb,var(--astron-sh-fg) 10%,var(--astron-sh-bg));animation:astron-sh-skeleton-pulse 1.8s ease-in-out infinite}
.skillhub-controls .astron-sh-loading-status{font-size:12px;line-height:18px;color:var(--astron-sh-muted);margin:8px 0 12px}
.skillhub-controls .astron-sh-skeleton-card{height:192px;padding:16px;display:flex;flex-direction:column;gap:16px;pointer-events:none}
.skillhub-controls .astron-sh-skeleton-identity{display:flex;gap:10px;align-items:center}
.skillhub-controls .astron-sh-skeleton-identity>div:last-child{display:grid;gap:8px;flex:1}
.skillhub-controls .astron-sh-skeleton-avatar{height:38px;width:38px;flex:none;border-radius:9px}
.skillhub-controls .astron-sh-skeleton-title{height:16px;width:65%}
.skillhub-controls .astron-sh-skeleton-coordinate{width:85%;height:10px}
.skillhub-controls .astron-sh-skeleton-copy{display:grid;gap:9px}
.skillhub-controls .astron-sh-skeleton-short{width:72%}
.skillhub-controls .astron-sh-skeleton-bottom{display:flex;justify-content:space-between;margin-top:auto;padding-top:14px;border-top:1px solid var(--astron-sh-border)}
.skillhub-controls .astron-sh-skeleton-bottom>div{width:30%}
.skillhub-controls .astron-sh-skeleton-document{min-height:440px;overflow:hidden}
.skillhub-controls .astron-sh-skeleton-tabs{display:flex;gap:24px;padding:18px 24px;border-bottom:1px solid var(--astron-sh-border)}
.skillhub-controls .astron-sh-skeleton-tabs>div{width:40px}
.skillhub-controls .astron-sh-skeleton-document-copy{padding:24px;display:grid;gap:18px}
.skillhub-controls .astron-sh-skeleton-install{padding:20px;display:grid;gap:20px}
.skillhub-controls .astron-sh-skeleton-input{height:36px}
@container(max-width:680px){.skillhub-controls .astron-sh-skeleton-document{min-height:280px}.skillhub-controls .astron-sh-skeleton-card{height:188px}}
@media(prefers-reduced-motion:reduce){.skillhub-controls .astron-sh-skeleton{animation:none}}
`}</style>;
}
