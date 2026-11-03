/** Configuration-only layout composed from the shared shadcn components and DSH tokens. */
export function ConfigStyles() {
  return <style>{`
.skillhub-controls.astron-sh-config{width:100%;max-width:720px;min-width:0;padding:24px 0;overflow-wrap:anywhere}
.skillhub-controls .astron-sh-config-heading{margin-bottom:20px}
.skillhub-controls.astron-sh-config h2{margin:0 0 6px;font-size:22px;line-height:30px;font-weight:600}
.skillhub-controls .astron-sh-config-heading p{margin:0;font-size:13px;line-height:20px;color:var(--astron-sh-muted)}
.skillhub-controls .astron-sh-config-stack{display:grid;gap:16px;margin:0;padding:0;border:0;min-width:0}
.skillhub-controls.astron-sh-config .astron-sh-card{border-radius:12px;background:var(--astron-sh-bg);box-shadow:0 1px 3px #00000006;min-width:0}
.skillhub-controls.astron-sh-config .astron-sh-card-header{padding:20px 20px 14px}
.skillhub-controls.astron-sh-config .astron-sh-card-content{padding:0 20px 20px}
.skillhub-controls .astron-sh-config-field{display:block;font-size:13px;line-height:20px;font-weight:500;margin-bottom:8px}
.skillhub-controls.astron-sh-config input{width:100%;height:40px;font-size:13px;line-height:20px;padding:9px 12px}
.skillhub-controls .astron-sh-config-help{margin:10px 0 0;font-size:12px;line-height:19px;color:var(--astron-sh-muted)}
.skillhub-controls .astron-sh-config-modules{display:grid}
.skillhub-controls .astron-sh-config-module{display:flex;align-items:center;justify-content:space-between;gap:20px;padding:14px 0;border-bottom:1px solid var(--astron-sh-border)}
.skillhub-controls .astron-sh-config-module:first-child{padding-top:0}
.skillhub-controls .astron-sh-config-module:last-child{border-bottom:0}
.skillhub-controls .astron-sh-config-module>div{min-width:0}
.skillhub-controls .astron-sh-config-module strong{font-size:13px;line-height:20px;font-weight:500}
.skillhub-controls .astron-sh-config-module p{font-size:12px;line-height:19px;color:var(--astron-sh-muted);margin:3px 0 0}
.skillhub-controls .astron-sh-config-module>.astron-sh-select-root{width:132px;flex:none}
.skillhub-controls .astron-sh-config-module .astron-sh-select-trigger{width:100%;min-width:0;font-size:13px;min-height:36px}
.skillhub-controls .astron-sh-config-actions{display:flex;align-items:center;flex-wrap:wrap;gap:8px;padding-top:18px;border-top:1px solid var(--astron-sh-border)}
.skillhub-controls .astron-sh-config-actions>button{min-height:36px;height:36px;margin:0;padding:7px 14px;font-size:13px}
.skillhub-controls .astron-sh-config-actions>span{font-size:12px;line-height:18px;color:var(--astron-sh-muted);margin-left:auto}
.skillhub-controls .astron-sh-config-connection-result{margin:10px 0 0;font-size:12px;line-height:19px}
.skillhub-controls .astron-sh-config-connection-result[data-passed=true]{color:light-dark(#187847,#7dddab)}
.skillhub-controls .astron-sh-config-connection-result[data-passed=false]{color:light-dark(#945414,#f0c27c)}
.skillhub-controls .astron-sh-config-feedback{font-size:13px;line-height:20px;margin:12px 0 0;min-height:20px}
.skillhub-controls .astron-sh-config-notice{font-size:13px;padding:10px 12px;background:var(--astron-sh-soft);border:1px solid var(--astron-sh-border);border-radius:8px}
.skillhub-controls .astron-sh-config-skeleton-title{width:100px;height:18px;margin-bottom:12px}
.skillhub-controls .astron-sh-config-skeleton-content{display:grid;gap:16px}
.skillhub-controls .astron-sh-config-skeleton-content>.astron-sh-skeleton{height:36px}

.skillhub-controls .astron-sh-config-tabs{display:inline-flex;width:auto;gap:4px;padding:4px;margin:0 0 24px;border-radius:999px;background:var(--astron-sh-soft);border:1px solid var(--astron-sh-border)}
.skillhub-controls .astron-sh-config-tabs>.astron-sh-tabs-trigger{min-width:108px;min-height:34px;padding:6px 20px;border:0;border-radius:999px;background:transparent;font-size:13px;line-height:20px;font-weight:500;color:var(--astron-sh-muted)}
.skillhub-controls .astron-sh-config-tabs>.astron-sh-tabs-trigger[data-state=active]{background:var(--astron-sh-bg);color:var(--astron-sh-fg);box-shadow:0 1px 3px #00000014}
.skillhub-controls .astron-sh-config-panel{min-width:0;outline-offset:4px}
.skillhub-controls .astron-sh-config-panel[data-state=inactive]{display:none}
.skillhub-controls .astron-sh-config-section-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:16px;margin-bottom:20px}
.skillhub-controls .astron-sh-config-section-heading h3{margin:0;font-size:15px;line-height:22px;font-weight:600}
.skillhub-controls .astron-sh-config-section-heading p{margin:4px 0 0;font-size:13px;line-height:20px;color:var(--astron-sh-muted)}
.skillhub-controls .astron-sh-config-state{flex:none;padding:3px 9px;border-radius:999px;background:var(--astron-sh-soft);color:var(--astron-sh-muted);font-size:12px;line-height:18px}
.skillhub-controls .astron-sh-config-feedback[data-tone=success]{color:light-dark(#187847,#7dddab)}
.skillhub-controls .astron-sh-config-feedback[data-tone=error]{color:light-dark(#945414,#f0c27c)}
.skillhub-controls.astron-sh-config .astron-sh-config-content-card{border:1px solid var(--astron-sh-border);border-radius:12px;background:var(--astron-sh-bg);box-shadow:none}
.skillhub-controls .astron-sh-config-content-card .astron-sh-config-section-heading{margin:0}
.skillhub-controls .astron-sh-source-list{display:grid;min-width:0}
.skillhub-controls .astron-sh-source-entry{min-width:0;border-bottom:1px solid var(--astron-sh-border);padding:14px 0}
.skillhub-controls .astron-sh-source-entry:first-child{padding-top:0}
.skillhub-controls .astron-sh-source-row{display:flex;align-items:flex-start;gap:12px;min-width:0}
.skillhub-controls .astron-sh-source-choice{display:grid;grid-template-columns:16px minmax(0,1fr);gap:4px 10px;align-items:start;min-width:0;flex:1;cursor:pointer}
.skillhub-controls.astron-sh-config input[type=radio]{width:16px;height:16px;min-width:16px;padding:0;margin:3px 0 0;accent-color:var(--astron-sh-accent)}
.skillhub-controls .astron-sh-source-text{display:grid;gap:3px;min-width:0}
.skillhub-controls .astron-sh-source-text>strong{font-size:13px;line-height:20px;font-weight:600}
.skillhub-controls .astron-sh-source-text>span{font-size:12px;line-height:18px;color:var(--astron-sh-muted);overflow-wrap:anywhere}
.skillhub-controls .astron-sh-source-choice>.astron-sh-config-state{grid-column:2;width:fit-content;padding:1px 7px;font-size:11px;line-height:17px}
.skillhub-controls .astron-sh-source-entry[data-selected=true] .astron-sh-config-state{color:var(--astron-sh-accent);background:color-mix(in srgb,var(--astron-sh-accent) 9%,var(--astron-sh-bg))}
.skillhub-controls .astron-sh-source-actions{display:flex;gap:2px;flex:none}
.skillhub-controls .astron-sh-source-actions>button{padding:5px 7px;min-height:30px;height:30px;font-size:12px}
.skillhub-controls .astron-sh-source-editor{display:grid;grid-template-columns:minmax(100px,1fr) minmax(180px,2fr);gap:12px;margin:14px 0 0 26px}
.skillhub-controls .astron-sh-source-editor>label{font-size:12px;line-height:18px;color:var(--astron-sh-muted);min-width:0}
.skillhub-controls .astron-sh-source-editor input{margin-top:6px}
.skillhub-controls .astron-sh-source-disconnect{display:flex;align-items:center;gap:10px;padding:16px 0 0;font-size:13px;line-height:20px;cursor:pointer}
.skillhub-controls .astron-sh-source-disconnect input[type=radio]{margin-top:0}
.skillhub-controls .astron-sh-source-footer{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-top:20px}
.skillhub-controls .astron-sh-source-footer .astron-sh-config-help{margin:0}
.skillhub-controls .astron-sh-source-footer>button{flex:none}
.skillhub-controls .astron-sh-config-empty{margin:0;color:var(--astron-sh-muted);font-size:13px;line-height:20px}
@media(max-width:480px){.skillhub-controls.astron-sh-config{padding:16px 0}.skillhub-controls .astron-sh-config-tabs{display:flex}.skillhub-controls .astron-sh-config-tabs>.astron-sh-tabs-trigger{flex:1;min-width:0}.skillhub-controls.astron-sh-config .astron-sh-card-header{padding:16px 16px 12px}.skillhub-controls.astron-sh-config .astron-sh-card-content{padding:0 16px 16px}.skillhub-controls .astron-sh-source-row{flex-wrap:wrap;gap:8px}.skillhub-controls .astron-sh-source-actions{margin-left:26px}.skillhub-controls .astron-sh-source-choice{flex-basis:100%}.skillhub-controls .astron-sh-source-editor{grid-template-columns:minmax(0,1fr)}.skillhub-controls .astron-sh-config-module{gap:12px}.skillhub-controls .astron-sh-config-module>.astron-sh-select-root{width:116px}.skillhub-controls .astron-sh-config-actions>span{width:100%;margin:2px 0 0}.skillhub-controls .astron-sh-source-footer{align-items:flex-start;flex-direction:column}}
`}</style>;
}
