/** Account surfaces share the same scoped rules in the page and portaled dialogs. */
export const accountStyles = `
.skillhub-controls .astron-sh-account-page{max-width:1152px;margin:auto;padding:24px;min-width:0}
.skillhub-controls .astron-sh-account-breadcrumbs{display:flex;align-items:center;gap:8px;flex-wrap:wrap;margin-bottom:24px;color:var(--astron-sh-muted);font-size:12px}
.skillhub-controls .astron-sh-account-breadcrumbs button{padding:0 6px;min-height:28px;height:28px;color:var(--astron-sh-secondary)}
.skillhub-controls .astron-sh-namespace-heading,.skillhub-controls .astron-sh-namespace-section-heading{display:flex;align-items:center;justify-content:space-between;gap:16px;flex-wrap:wrap}
.skillhub-controls .astron-sh-namespace-heading{margin-bottom:24px}
.skillhub-controls .astron-sh-namespace-heading>div,.skillhub-controls .astron-sh-namespace-section-heading>div{min-width:0}
.skillhub-controls .astron-sh-namespace-heading h1{margin:0;font-size:20px;line-height:28px;font-weight:600;overflow-wrap:anywhere}
.skillhub-controls .astron-sh-namespace-title{display:flex;align-items:center;gap:10px;flex-wrap:wrap}
.skillhub-controls .astron-sh-namespace-heading p,.skillhub-controls .astron-sh-namespace-section-heading p{margin:6px 0 0;font-size:12px;line-height:20px;color:var(--astron-sh-secondary);overflow-wrap:anywhere}
.skillhub-controls .astron-sh-namespace-section-heading{margin-bottom:20px}
.skillhub-controls .astron-sh-namespace-section-heading h2{margin:0;font-size:14px;line-height:22px;font-weight:600}
.skillhub-controls .astron-sh-namespace-notice{margin:0 0 20px;padding:12px 14px;border:1px solid var(--astron-sh-border);border-radius:8px;background:var(--astron-sh-soft);font-size:12px;line-height:20px;color:var(--astron-sh-secondary)}
.skillhub-controls .astron-sh-namespace-tabs{display:flex;gap:24px;border:0;border-bottom:1px solid var(--astron-sh-border);border-radius:0;background:transparent;padding:0;margin:0 0 24px}
.skillhub-controls .astron-sh-namespace-tabs>.astron-sh-tabs-trigger{height:44px;min-height:44px;padding:10px 4px;border:0;border-bottom:2px solid transparent;border-radius:0;font-size:13px;line-height:22px;background:transparent;transition:color .15s}
.skillhub-controls .astron-sh-namespace-tabs>.astron-sh-tabs-trigger[data-state=active]{border-bottom-color:var(--astron-sh-primary);color:var(--astron-sh-fg);box-shadow:none}
.skillhub-controls .astron-sh-namespace-panel{min-width:0}
.skillhub-controls .astron-sh-namespace-view{display:flex;align-items:center;gap:4px;padding:3px;border:1px solid var(--astron-sh-border);border-radius:8px;background:var(--astron-sh-soft)}
.skillhub-controls .astron-sh-namespace-list,.skillhub-controls .astron-sh-namespace-members{list-style:none;margin:0;padding:0}
.skillhub-controls .astron-sh-namespace-list,.skillhub-controls .astron-sh-namespace-member-table{border:1px solid var(--astron-sh-border);border-radius:10px;overflow:hidden;background:var(--astron-sh-surface)}
.skillhub-controls .astron-sh-namespace-list-row{display:flex;align-items:center;justify-content:space-between;gap:16px;flex-wrap:wrap;padding:18px 20px}
.skillhub-controls .astron-sh-namespace-list-row+.astron-sh-namespace-list-row,.skillhub-controls .astron-sh-namespace-member-row+.astron-sh-namespace-member-row{border-top:1px solid var(--astron-sh-border-subtle)}
.skillhub-controls .astron-sh-namespace-identity{min-width:0;overflow-wrap:anywhere}
.skillhub-controls .astron-sh-namespace-identity strong{font-size:13px;line-height:20px;font-weight:600}
.skillhub-controls .astron-sh-namespace-identity p{margin:5px 0 0;font-size:12px;line-height:18px;color:var(--astron-sh-muted)}
.skillhub-controls .astron-sh-namespace-user-id{font-family:ui-monospace,SFMono-Regular,Consolas,monospace}
.skillhub-controls .astron-sh-namespace-role,.skillhub-controls .astron-sh-namespace-protected{font-size:12px;color:var(--astron-sh-secondary)}
.skillhub-controls .astron-sh-namespace-self{margin-left:4px;font-weight:400;color:var(--astron-sh-secondary)}
.skillhub-controls .astron-sh-namespace-member-header,.skillhub-controls .astron-sh-namespace-member-row{display:grid;grid-template-columns:minmax(0,1fr) 100px 184px;gap:20px;align-items:center;padding:16px 20px}
.skillhub-controls .astron-sh-namespace-member-header{padding-block:10px;background:var(--astron-sh-soft);border-bottom:1px solid var(--astron-sh-border-subtle);font-size:12px;color:var(--astron-sh-muted)}
.skillhub-controls .astron-sh-namespace-member-header>span:last-child{text-align:right}
.skillhub-controls .astron-sh-namespace-member-row>.astron-sh-badge{justify-self:start}
.skillhub-controls .astron-sh-namespace-member-row>.astron-sh-account-row-actions{justify-content:flex-end}
.skillhub-controls .astron-sh-namespace-member-header>span:nth-child(2){text-align:left}
.skillhub-controls .astron-sh-namespace-empty{padding:20px 0;margin:0;font-size:13px;color:var(--astron-sh-secondary);overflow-wrap:anywhere}
.skillhub-controls .astron-sh-namespace-pagination{display:flex;align-items:center;justify-content:flex-end;gap:12px;margin-top:20px;font-size:12px;color:var(--astron-sh-secondary)}
@container(max-width:600px){.skillhub-controls .astron-sh-account-page{padding:20px 16px}.skillhub-controls .astron-sh-namespace-member-header{display:none}.skillhub-controls .astron-sh-namespace-member-row{grid-template-columns:minmax(0,1fr) auto;gap:12px}.skillhub-controls .astron-sh-namespace-member-row>.astron-sh-badge{justify-self:end}.skillhub-controls .astron-sh-namespace-member-row>.astron-sh-account-row-actions{grid-column:1/-1}.skillhub-controls .astron-sh-namespace-member-row,.skillhub-controls .astron-sh-namespace-list-row{padding:16px}.skillhub-controls .astron-sh-namespace-heading h1{font-size:18px}.skillhub-controls .astron-sh-namespace-list-row>.astron-sh-account-row-actions{width:100%}.skillhub-controls .astron-sh-namespace-list-row>.astron-sh-account-row-actions>button:last-child{margin-left:auto}}
.skillhub-controls .astron-sh-account-row-actions{display:flex;align-items:center;gap:8px;flex-wrap:wrap}
.skillhub-controls .astron-sh-account-menu{min-width:184px;padding:6px;border:1px solid var(--astron-sh-border);border-radius:8px;background:var(--astron-sh-surface);color:var(--astron-sh-fg);z-index:100}
.skillhub-controls .astron-sh-account-menu [role=menuitem]{padding:8px 10px;border-radius:5px;outline:none;cursor:pointer}
.skillhub-controls .astron-sh-account-menu [data-highlighted]{background:var(--astron-sh-soft)}
.skillhub-controls .astron-sh-account-menu [role=separator]{height:1px;margin:6px;background:var(--astron-sh-border)}
.skillhub-controls .astron-sh-dialog.astron-sh-account-form{display:flex;flex-direction:column;width:min(480px,calc(100vw - 32px));max-height:calc(100dvh - 32px);padding:0;overflow:hidden}
.skillhub-controls .astron-sh-account-dialog-header{display:flex;align-items:flex-start;justify-content:space-between;gap:16px;padding:24px 24px 0}
.skillhub-controls .astron-sh-account-dialog-header>div{min-width:0}
.skillhub-controls .astron-sh-account-dialog-header h2{margin:0;font-size:18px;line-height:28px;font-weight:600}
.skillhub-controls .astron-sh-account-dialog-header p{margin:6px 0 0;color:var(--astron-sh-secondary);overflow-wrap:anywhere}
.skillhub-controls .astron-sh-account-dialog-header>button{flex:none;width:32px;min-height:32px;padding:0;color:var(--astron-sh-muted)}
.skillhub-controls .astron-sh-account-dialog-body{display:grid;gap:20px;min-height:0;overflow:auto;padding:20px 24px}
.skillhub-controls .astron-sh-account-dialog-body>p{margin:0}
.skillhub-controls .astron-sh-account-dialog-body form{display:grid;gap:8px;min-width:0;margin:0}
.skillhub-controls .astron-sh-account-dialog-body label{display:grid;gap:8px;font-size:13px;font-weight:500;min-width:0}
.skillhub-controls .astron-sh-account-dialog-body select,.skillhub-controls .astron-sh-account-dialog-body input,.skillhub-controls .astron-sh-account-dialog-body .astron-sh-select-root,.skillhub-controls .astron-sh-account-dialog-body .astron-sh-select-trigger{width:100%;min-width:0}
.skillhub-controls .astron-sh-account-dialog-body select,.skillhub-controls .astron-sh-account-dialog-body input:not([type=file]){height:40px}
.skillhub-controls .astron-sh-account-dialog-body select:focus,.skillhub-controls .astron-sh-account-dialog-body input:focus,.skillhub-controls .astron-sh-account-dialog-body .astron-sh-select-trigger:focus{outline:none;border-color:var(--astron-sh-accent)}
.skillhub-controls .astron-sh-account-dialog-body input[type=password]{font-family:ui-monospace,SFMono-Regular,Consolas,monospace}
.skillhub-controls .astron-sh-account-dialog-body input[type=password]::placeholder{font-family:var(--dsw-font-family,system-ui,sans-serif)}
.skillhub-controls .astron-sh-upload-field{display:grid;gap:10px;min-width:0}
.skillhub-controls .astron-sh-account-dialog-body .astron-sh-upload-dropzone{display:flex;flex-direction:column;gap:12px;width:100%;height:auto;padding:24px 16px;border:1px dashed var(--astron-sh-border);border-radius:10px;background:var(--astron-sh-soft);color:var(--astron-sh-secondary);font-weight:400;white-space:normal;text-align:center}
.skillhub-controls .astron-sh-account-dialog-body .astron-sh-upload-dropzone[data-dragging=true],.skillhub-controls .astron-sh-account-dialog-body .astron-sh-upload-dropzone:hover:not(:disabled){border-color:var(--astron-sh-accent)}
.skillhub-controls .astron-sh-upload-dropzone>svg{width:24px;height:24px;color:var(--astron-sh-muted)}
.skillhub-controls .astron-sh-upload-dropzone strong{font-size:13px;line-height:20px}
.skillhub-controls .astron-sh-upload-picker-label{padding:6px 14px;border:1px solid var(--astron-sh-border);border-radius:6px;background:var(--astron-sh-surface);color:var(--astron-sh-fg);font-size:13px}
.skillhub-controls .astron-sh-upload-file{display:flex;align-items:center;gap:10px;padding:12px;border:1px solid var(--astron-sh-border);border-radius:8px;background:var(--astron-sh-surface)}
.skillhub-controls .astron-sh-upload-file>svg{flex:none;color:var(--astron-sh-muted)}
.skillhub-controls .astron-sh-upload-file>div{display:grid;gap:4px;min-width:0;flex:1;overflow-wrap:anywhere;font-size:13px}
.skillhub-controls .astron-sh-upload-file span{font-size:12px;color:var(--astron-sh-muted)}
.skillhub-controls .astron-sh-upload-file>button{flex:none}
@media(max-width:480px){.skillhub-controls .astron-sh-upload-file{flex-wrap:wrap;align-items:flex-start}.skillhub-controls .astron-sh-upload-file>div{flex-basis:calc(100% - 26px)}.skillhub-controls .astron-sh-upload-file>button{margin-left:auto}}
.skillhub-controls .astron-sh-account-dialog-body .astron-sh-account-source{display:grid;gap:5px;margin:0;padding:12px 14px;border:1px solid var(--astron-sh-border-subtle);border-radius:8px;background:var(--astron-sh-soft)}
.skillhub-controls .astron-sh-account-source dt{font-size:12px;color:var(--astron-sh-muted)}
.skillhub-controls .astron-sh-account-source dd{margin:0;font-size:13px;line-height:20px;overflow-wrap:anywhere}
.skillhub-controls .astron-sh-account-source dd+dt{margin-top:8px}
.skillhub-controls .astron-sh-account-token-help{display:flex;align-items:center;gap:8px;flex-wrap:wrap;font-size:12px;color:var(--astron-sh-secondary)}
.skillhub-controls .astron-sh-account-token-help .astron-sh-website-link{margin:0;padding:0;min-height:28px;border:0;background:transparent;color:var(--astron-sh-link);font-size:12px}
.skillhub-controls .astron-sh-account-dialog-footer{display:flex;justify-content:flex-end;gap:8px;flex:none;padding:16px 24px;border-top:1px solid var(--astron-sh-border-subtle)}
.skillhub-controls .astron-sh-account-dialog-footer button{min-height:36px;padding:7px 16px}
.skillhub-controls .astron-sh-account-setting-row{display:flex;align-items:center;justify-content:space-between;gap:16px;font-size:13px}
.skillhub-controls .astron-sh-account-hint{font-size:12px;font-weight:400;line-height:18px;color:var(--astron-sh-muted)}
.skillhub-controls .astron-sh-account-summary{padding:14px;border:1px solid var(--astron-sh-border);border-radius:8px;background:var(--astron-sh-soft);overflow-wrap:anywhere}
.skillhub-controls .astron-sh-account-summary p{margin:8px 0 0}
.skillhub-controls .astron-sh-account-dialog-body .astron-sh-account-search{grid-template-columns:minmax(0,1fr) auto;align-items:end;gap:8px}
.skillhub-controls .astron-sh-account-search>button{min-height:40px}
.skillhub-controls .astron-sh-account-candidates{display:grid;gap:8px;max-height:220px;overflow:auto}
.skillhub-controls .astron-sh-account-candidates .astron-sh-account-candidate{display:grid;justify-content:stretch;gap:4px;width:100%;height:auto;padding:12px;text-align:left;white-space:normal;overflow-wrap:anywhere}
.skillhub-controls .astron-sh-account-candidate span{font-size:12px;color:var(--astron-sh-secondary)}
.skillhub-controls .astron-sh-account-candidate[aria-pressed=true]{border-color:var(--astron-sh-accent);background:var(--astron-sh-soft)}
.skillhub-controls .astron-sh-account-form .astron-sh-account-error{margin:0;padding:10px 12px;border-radius:6px;background:var(--astron-sh-soft);color:light-dark(#b42318,#ffb4ab);overflow-wrap:anywhere}
.skillhub-controls .astron-sh-account-source-row{position:relative;min-width:0}
.skillhub-controls .astron-sh-account-source-row .astron-sh-account-source{padding-right:100px}
.skillhub-controls .astron-sh-account-source-row>button{position:absolute;top:12px;right:12px}
.skillhub-controls .astron-sh-toolbar-actions>span{display:inline-flex;align-items:center;gap:6px}
.skillhub-controls .astron-sh-workbench-frame[hidden]{display:none}
@media(max-width:480px){.skillhub-controls .astron-sh-account-dialog-header{padding:20px 20px 0}.skillhub-controls .astron-sh-account-dialog-body{padding:20px}.skillhub-controls .astron-sh-account-dialog-footer{padding:14px 20px}.skillhub-controls .astron-sh-account-dialog-footer button{flex:1;white-space:normal}}
`;
