/** Configuration-only layout composed from the shared shadcn components and DSH tokens. */
export function ConfigStyles() {
  return <style>{`
.skillhub-controls.astron-sh-config{width:100%;max-width:720px;min-width:0;padding:20px 0;overflow-wrap:anywhere}
.skillhub-controls .astron-sh-config-heading{margin-bottom:20px}
.skillhub-controls.astron-sh-config h2{margin:0 0 6px;font-size:20px;line-height:28px;font-weight:600}
.skillhub-controls .astron-sh-config-heading p{margin:0;font-size:13px;line-height:20px;color:var(--astron-sh-muted)}
.skillhub-controls .astron-sh-config-stack{display:grid;gap:16px;margin:0;padding:0;border:0;min-width:0}
.skillhub-controls.astron-sh-config .astron-sh-card{border-radius:12px;background:var(--astron-sh-bg);box-shadow:0 1px 3px #00000006;min-width:0}
.skillhub-controls.astron-sh-config .astron-sh-card-header{padding:20px 20px 14px}
.skillhub-controls.astron-sh-config .astron-sh-card-title{font-size:15px;line-height:22px;font-weight:600;margin:0}
.skillhub-controls.astron-sh-config .astron-sh-card-description{font-size:13px;line-height:20px;margin:5px 0 0;color:var(--astron-sh-muted)}
.skillhub-controls.astron-sh-config .astron-sh-card-content{padding:0 20px 20px}
.skillhub-controls .astron-sh-config-card-heading{display:flex;align-items:center;justify-content:space-between;gap:12px}
.skillhub-controls .astron-sh-config-card-heading>span{font-size:12px;line-height:18px;color:var(--astron-sh-muted);white-space:nowrap}
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
.skillhub-controls .astron-sh-config-module .astron-sh-select-trigger{width:100%;font-size:13px;min-height:36px}
.skillhub-controls .astron-sh-config-actions{display:flex;align-items:center;flex-wrap:wrap;gap:10px;padding-top:2px}
.skillhub-controls .astron-sh-config-actions>button{min-height:32px;height:32px;margin:0;padding:5px 12px;font-size:12px}
.skillhub-controls .astron-sh-config-actions>span{font-size:12px;line-height:18px;color:var(--astron-sh-muted);margin-left:auto}
.skillhub-controls .astron-sh-config-connection{display:flex;align-items:center;flex-wrap:wrap;gap:10px;margin-top:12px}
.skillhub-controls .astron-sh-config-connection>button{min-height:32px;height:32px;padding:5px 12px;font-size:12px}
.skillhub-controls .astron-sh-config-connection>span{font-size:12px;color:var(--astron-sh-muted)}
.skillhub-controls .astron-sh-config-connection-result{margin:10px 0 0;font-size:12px;line-height:19px}
.skillhub-controls .astron-sh-config-connection-result[data-passed=true]{color:light-dark(#187847,#7dddab)}
.skillhub-controls .astron-sh-config-connection-result[data-passed=false]{color:light-dark(#945414,#f0c27c)}
.skillhub-controls .astron-sh-config-feedback{font-size:13px;line-height:20px;margin:12px 0 0;min-height:20px}
.skillhub-controls .astron-sh-config-notice{font-size:13px;padding:10px 12px;background:var(--astron-sh-soft);border:1px solid var(--astron-sh-border);border-radius:8px}
.skillhub-controls .astron-sh-config-skeleton-title{width:100px;height:18px;margin-bottom:12px}
.skillhub-controls .astron-sh-config-skeleton-content{display:grid;gap:16px}
.skillhub-controls .astron-sh-config-skeleton-content>.astron-sh-skeleton{height:36px}
@media(max-width:480px){.skillhub-controls.astron-sh-config .astron-sh-card-header{padding:16px 16px 12px}.skillhub-controls.astron-sh-config .astron-sh-card-content{padding:0 16px 16px}.skillhub-controls .astron-sh-config-module{gap:12px}.skillhub-controls .astron-sh-config-module>.astron-sh-select-root{width:116px}.skillhub-controls .astron-sh-config-actions>span{width:100%;margin:2px 0 0}}
`}</style>;
}
