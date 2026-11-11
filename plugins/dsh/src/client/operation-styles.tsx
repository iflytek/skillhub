/** Content-sized operation surfaces; state and confirmation remain owned by their controls. */
export function OperationSurfaceStyles() {
  return <style>{`
.skillhub-controls .astron-sh-operation-dialog{width:min(416px,calc(100vw - 32px));max-width:416px;height:min(432px,calc(100dvh - 32px));min-height:0;overflow:auto;padding:20px;box-sizing:border-box;display:flex;flex-direction:column;gap:12px}
.skillhub-controls .astron-sh-operation-dialog>h2{font-size:18px;line-height:24px;margin:0;font-weight:600}
.skillhub-controls .astron-sh-operation-dialog>p{font-size:13px;line-height:20px;margin:0}
.skillhub-controls .astron-sh-operation-dialog .astron-sh-dialog-actions{margin-top:auto;padding-top:12px;gap:8px;display:flex;justify-content:flex-end;flex-wrap:wrap}
.skillhub-controls .astron-sh-operation-dialog .astron-sh-install-dialog-layout>div{min-height:0}
.skillhub-controls .astron-sh-operation-dialog .astron-sh-install-dialog-layout{display:flex;flex:1;flex-direction:column;min-width:0;gap:12px;margin-top:0}
.skillhub-controls .astron-sh-operation-dialog .astron-sh-install-dialog-identity{display:flex;align-items:center;gap:12px;min-width:0;margin:0;padding:0}
.skillhub-controls .astron-sh-operation-dialog .astron-sh-install-dialog-identity>div{min-width:0}
.skillhub-controls .astron-sh-operation-dialog .astron-sh-install-dialog-identity strong{font-size:14px;line-height:20px;overflow-wrap:anywhere}
.skillhub-controls .astron-sh-operation-dialog .astron-sh-install-dialog-identity p{font-size:12px;line-height:18px;margin:4px 0 0;color:var(--astron-sh-muted);overflow-wrap:anywhere}
.skillhub-controls .astron-sh-operation-dialog .astron-sh-install-progress-item{padding:0;border:0;box-shadow:none;background:transparent;min-height:96px}
.skillhub-controls .astron-sh-operation-dialog .astron-sh-install-progress-item>header{display:none}
.skillhub-controls .astron-sh-operation-dialog .astron-sh-install-confirmed-scope{margin:0;padding:10px 12px;border:1px solid var(--astron-sh-border);border-radius:6px;font-size:12px}
.skillhub-controls .astron-sh-operation-dialog .astron-sh-install-progress-actions{margin-top:12px}
.skillhub-controls .astron-sh-install-close-note{font-size:12px;line-height:18px;color:var(--astron-sh-muted);margin:0}
.skillhub-controls .astron-sh-package-dialog.astron-sh-operation-dialog{height:min(448px,calc(100dvh - 32px));min-height:0}
.skillhub-controls .astron-sh-package-dialog .astron-sh-package-body{min-height:96px;min-width:0}
.skillhub-controls .astron-sh-package-preview{display:grid;gap:8px;min-width:0}
.skillhub-controls .astron-sh-package-name{font-size:14px;line-height:20px;overflow-wrap:anywhere}
.skillhub-controls .astron-sh-package-summary{max-height:80px;overflow:auto;font-size:13px;line-height:20px;overflow-wrap:anywhere}
.skillhub-controls .astron-sh-package-count{font-size:12px;line-height:18px;color:var(--astron-sh-muted);margin:0}
.skillhub-controls .astron-sh-package-scope{display:flex;gap:8px;flex-wrap:wrap;padding:0;margin:0}
.skillhub-controls .astron-sh-scope-choice{display:flex;align-items:center;gap:8px;flex:1;min-width:120px;padding:8px 10px;border:1px solid var(--astron-sh-border);border-radius:6px;font-size:13px;line-height:18px;cursor:pointer}
.skillhub-controls .astron-sh-scope-choice input{width:16px;height:16px;margin:0;accent-color:var(--astron-sh-fg)}
.skillhub-controls .astron-sh-package-result{padding:12px 0 0;margin:0;min-height:76px;border-top:1px solid var(--astron-sh-border);font-size:13px}
.skillhub-controls .astron-sh-package-result-action{margin-top:8px}
.skillhub-controls .astron-sh-package-result p{font-size:12px;line-height:18px;margin:6px 0;color:var(--astron-sh-muted)}
.skillhub-controls .astron-sh-operation-confirm{width:min(416px,calc(100vw - 32px));max-width:416px;padding:20px;max-height:calc(100dvh - 32px);overflow:auto;box-sizing:border-box}
.skillhub-controls .astron-sh-operation-confirm>h2{font-size:18px;line-height:24px;margin:0 0 8px}
.skillhub-controls .astron-sh-operation-confirm>p{font-size:13px;line-height:20px}
.skillhub-controls.astron-sh-management-notice{width:min(344px,calc(100vw - 32px));padding:16px;border-radius:12px;font-size:13px;line-height:20px;max-height:calc(100dvh - 32px);overflow:auto}
.skillhub-controls.astron-sh-management-notice strong{font-size:13px;line-height:18px}
.astron-sh-operation-details{font-size:12px;line-height:19px;margin-top:12px;overflow-wrap:anywhere}
.astron-sh-operation-details summary{cursor:pointer;font-weight:500}
.astron-sh-operation-details p{font-size:12px;line-height:19px;margin:8px 0}
.astron-sh-operation-details ul{margin:8px 0;padding-left:18px}
@media(max-width:480px){.skillhub-controls .astron-sh-operation-dialog{padding:16px;max-height:calc(100dvh - 32px);overflow:auto}}
`}</style>;
}
