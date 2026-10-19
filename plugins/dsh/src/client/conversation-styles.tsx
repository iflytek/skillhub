/** Conversation-only presentation: catalog cards and modal detail layouts stay independent. */
export function ConversationStyles() {
  return <style>{`
.skillhub-controls .astron-sh-conversation-result{min-width:0;padding:16px;border:1px solid var(--astron-sh-border);border-radius:12px;background:var(--astron-sh-bg);font-size:13px;line-height:20px;container-type:inline-size}
.skillhub-controls .astron-sh-conversation-heading{display:flex;align-items:center;gap:8px;margin-bottom:6px;min-width:0}
.skillhub-controls .astron-sh-conversation-heading h3{margin:0;min-width:0;font-size:14px;line-height:20px;font-weight:600}
.skillhub-controls .astron-sh-conversation-heading>span:last-child{margin-left:auto;color:var(--astron-sh-muted);font-size:12px;white-space:nowrap}
.skillhub-controls .astron-sh-conversation-mark{display:flex;align-items:center;justify-content:center;width:24px;height:24px;flex:none;border-radius:6px;color:var(--astron-sh-accent);background:color-mix(in srgb,var(--astron-sh-accent) 9%,var(--astron-sh-bg))}
.skillhub-controls .astron-sh-conversation-mark svg{width:14px;height:14px}
.skillhub-controls .astron-sh-conversation-caption{margin:0 0 12px;color:var(--astron-sh-muted);font-size:12px;line-height:18px}
.skillhub-controls .astron-sh-conversation-list{display:grid;gap:8px}
.skillhub-controls .astron-sh-conversation-skill{position:relative;display:grid;grid-template-columns:minmax(0,1fr) auto;grid-template-rows:auto 1fr;column-gap:16px;row-gap:8px;min-height:112px;padding:14px;background:color-mix(in srgb,var(--astron-sh-accent) 3%,var(--astron-sh-bg));border:1px solid color-mix(in srgb,var(--astron-sh-fg) 20%,var(--astron-sh-bg));border-radius:9px;box-shadow:0 1px 3px #00000008}
.skillhub-controls .astron-sh-conversation-skill:hover{border-color:color-mix(in srgb,var(--astron-sh-accent) 45%,var(--astron-sh-border))}
.skillhub-controls .astron-sh-conversation-identity{display:flex;align-items:center;gap:9px;min-width:0}
.skillhub-controls .astron-sh-conversation-identity>.astron-sh-avatar{width:32px;height:32px;flex:none;font-size:14px}
.skillhub-controls .astron-sh-conversation-identity>div{min-width:0}
.skillhub-controls .astron-sh-conversation-identity h4{font-size:14px;font-weight:600;line-height:20px;margin:0;overflow:hidden;white-space:nowrap;text-overflow:ellipsis}
.skillhub-controls .astron-sh-conversation-identity p{font-size:12px;line-height:18px;color:var(--astron-sh-muted);margin:0;overflow:hidden;white-space:nowrap;text-overflow:ellipsis}
.skillhub-controls .astron-sh-conversation-summary{grid-column:1;min-width:0;font-size:13px;line-height:20px}
.skillhub-controls .astron-sh-conversation-summary>p{margin:0 0 6px;display:-webkit-box;-webkit-box-orient:vertical;-webkit-line-clamp:2;overflow:hidden}
.skillhub-controls .astron-sh-conversation-detail-action{grid-column:2;grid-row:1/3;align-self:center;display:flex;align-items:center;gap:5px;min-height:40px;padding:7px 10px;font-size:12px;color:var(--astron-sh-accent);background:var(--astron-sh-bg);border-color:color-mix(in srgb,var(--astron-sh-accent) 28%,var(--astron-sh-border))}
.skillhub-controls .astron-sh-conversation-detail-action svg{width:13px;height:13px;flex:none}
.skillhub-controls .astron-sh-conversation-skill:focus-within{outline:2px solid var(--astron-sh-accent);outline-offset:2px}
.skillhub-controls .astron-sh-conversation-actions{display:flex;align-items:center;flex-wrap:wrap;gap:10px;margin-top:14px;padding-top:12px;border-top:1px solid var(--astron-sh-border)}
.skillhub-controls .astron-sh-conversation-actions>button{margin:0;font-size:12px;min-height:34px;padding:6px 10px}
.skillhub-controls .astron-sh-conversation-provenance{margin-top:12px;font-size:12px;line-height:18px;color:var(--astron-sh-muted)}
.skillhub-controls .astron-sh-conversation-provenance summary{cursor:pointer;padding:4px 0}
.skillhub-controls .astron-sh-conversation-provenance p{margin:6px 0;overflow-wrap:anywhere}
.skillhub-controls .astron-sh-conversation-install>p{margin:8px 0;font-size:13px;line-height:20px}
.skillhub-controls .astron-sh-conversation-install>button{margin:8px 8px 0 0;font-size:12px;padding:6px 10px}
.skillhub-controls .astron-sh-conversation-install [aria-label="技能发现核验"]{font-size:12px;line-height:18px;color:var(--astron-sh-muted)}
.skillhub-controls .astron-sh-conversation-install [aria-label="技能发现核验"] p{margin:5px 0}
.skillhub-controls .astron-sh-conversation-install [aria-label="技能发现核验"] button{font-size:12px;padding:4px 8px;min-height:30px}
.skillhub-controls .astron-sh-conversation-install [aria-label="当前安装记录"]{margin-top:12px;padding:12px;background:var(--astron-sh-soft);border:1px solid var(--astron-sh-border);border-radius:8px;font-size:13px}
.skillhub-controls .astron-sh-conversation-install [aria-label="当前安装记录"] h4{margin:0 0 6px;font-size:13px;font-weight:600}
.skillhub-controls .astron-sh-conversation-install [aria-label="当前安装记录"] p,.skillhub-controls .astron-sh-conversation-install [aria-label="当前安装记录"] ul{margin:6px 0}
.skillhub-controls .astron-sh-conversation-install [aria-label="当前安装记录"] button{font-size:12px;padding:4px 8px;min-height:30px}
.skillhub-controls .astron-sh-install-actions{display:flex;align-items:center;flex-wrap:wrap;gap:8px;margin-top:12px}
.skillhub-controls .astron-sh-install-actions>button{width:auto;min-height:32px;margin:0;padding:5px 10px;font-size:12px}
.skillhub-controls .astron-sh-install-badge{margin-left:auto;flex:none;border-radius:999px;padding:3px 8px;font-size:12px;line-height:18px;font-weight:500;background:var(--astron-sh-soft);color:var(--astron-sh-accent)}
.skillhub-controls .astron-sh-install-badge[data-tone=success]{color:light-dark(#187847,#7dddab);background:light-dark(#eaf6ee,#19392c)}
.skillhub-controls .astron-sh-install-badge[data-tone=warning]{color:light-dark(#945414,#f0c27c);background:light-dark(#fff3df,#3e3020)}
.skillhub-controls .astron-sh-conversation-install>.astron-sh-install-outcome{font-size:12px;margin:4px 0;color:var(--astron-sh-muted)}
.skillhub-controls .astron-sh-install-record{margin-top:12px;padding-top:10px;border-top:1px solid var(--astron-sh-border);font-size:12px;line-height:18px}
.skillhub-controls .astron-sh-install-record>summary{cursor:pointer;font-size:12px;line-height:20px;color:var(--astron-sh-muted);padding:4px 0}
.skillhub-controls .astron-sh-install-record>p{margin:8px 0}
.skillhub-controls .astron-sh-install-record>button{font-size:12px;min-height:30px;padding:4px 8px;margin-top:8px}
@container(max-width:380px){.skillhub-controls .astron-sh-conversation-skill{column-gap:8px;padding:12px;min-height:112px}.skillhub-controls .astron-sh-conversation-summary{grid-column:1/-1}.skillhub-controls .astron-sh-conversation-detail-action{grid-row:1;padding:6px 8px}.skillhub-controls .astron-sh-conversation-detail-action svg{display:none}}
`}</style>;
}
