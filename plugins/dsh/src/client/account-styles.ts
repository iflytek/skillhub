/** Account surfaces share the same scoped rules in the page and portaled dialogs. */
export const accountStyles = `
.skillhub-controls .astron-sh-account-page{max-width:1100px;margin:auto;padding:20px;min-width:0}
.skillhub-controls .astron-sh-account-page header,.skillhub-controls .astron-sh-account-page article,.skillhub-controls .astron-sh-account-page nav{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap}
.skillhub-controls .astron-sh-account-page nav{justify-content:flex-start;margin-block:16px}
.skillhub-controls .astron-sh-account-page article{padding:16px 0;border-bottom:1px solid var(--astron-sh-border);overflow-wrap:anywhere}
.skillhub-controls .astron-sh-account-page article>div{min-width:0}
.skillhub-controls .astron-sh-account-page article p{margin:4px 0;color:var(--astron-sh-secondary)}
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
.skillhub-controls .astron-sh-account-dialog-body input[type=file]{padding:6px;border:1px solid var(--astron-sh-border);border-radius:8px;background:var(--astron-sh-soft);font-weight:400;font-size:13px}
.skillhub-controls .astron-sh-account-dialog-body input[type=file]::file-selector-button{margin-right:10px;padding:5px 10px;border:1px solid var(--astron-sh-border);border-radius:5px;background:var(--astron-sh-surface);color:var(--astron-sh-fg);font:inherit;cursor:pointer}
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
.skillhub-controls.astron-sh-catalog[data-skillhub-appearance=dark]{color-scheme:dark}
.skillhub-controls.astron-sh-catalog[data-skillhub-appearance=light]{color-scheme:light}
.skillhub-controls .astron-sh-workbench-frame[hidden]{display:none}
@media(max-width:480px){.skillhub-controls .astron-sh-account-dialog-header{padding:20px 20px 0}.skillhub-controls .astron-sh-account-dialog-body{padding:20px}.skillhub-controls .astron-sh-account-dialog-footer{padding:14px 20px}.skillhub-controls .astron-sh-account-dialog-footer button{flex:1;white-space:normal}}
`;
