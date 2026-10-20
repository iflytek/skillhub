export function MarkdownStyles() {
  return <style>{`
.skillhub-controls .astron-sh-markdown{font-size:14px;line-height:1.85;overflow-wrap:anywhere;color:var(--astron-sh-fg,inherit);max-width:82ch;margin-inline:auto}
.skillhub-controls .astron-sh-markdown>:first-child{margin-top:0}
.skillhub-controls .astron-sh-markdown h1{font-size:24px;line-height:1.4;margin:1.4em 0 .7em}
.skillhub-controls .astron-sh-markdown h2{font-size:20px;line-height:1.5;margin:1.6em 0 .6em;padding-bottom:.45em;border-bottom:1px solid var(--astron-sh-border,#8883)}
.skillhub-controls .astron-sh-markdown h3{font-size:16px;line-height:1.5;margin:1.4em 0 .5em}
.skillhub-controls .astron-sh-markdown p{margin:.85em 0}
.skillhub-controls .astron-sh-markdown ul,.skillhub-controls .astron-sh-markdown ol{padding-left:1.6em;margin:.8em 0}
.skillhub-controls .astron-sh-markdown li{margin:.3em 0}
.skillhub-controls .astron-sh-markdown pre{overflow-x:auto;padding:16px;border:1px solid var(--astron-sh-border,#8883);border-radius:10px;background:var(--astron-sh-soft,#8881);line-height:1.65;font-size:12px}
.skillhub-controls .astron-sh-markdown code{font-family:ui-monospace,SFMono-Regular,Consolas,monospace;font-size:.9em}
.skillhub-controls .astron-sh-markdown :not(pre)>code{padding:2px 5px;border-radius:4px;background:var(--astron-sh-soft,#8881)}
.skillhub-controls .astron-sh-markdown blockquote{margin:1em 0;padding:2px 16px;border-left:3px solid var(--astron-sh-accent,#8886);color:var(--astron-sh-muted,inherit)}
.skillhub-controls .astron-sh-markdown table{display:block;overflow-x:auto;border-collapse:collapse;font-size:13px;margin:1em 0}
.skillhub-controls .astron-sh-markdown th,.skillhub-controls .astron-sh-markdown td{border:1px solid var(--astron-sh-border,#8883);padding:8px 12px;text-align:left}
.skillhub-controls .astron-sh-markdown th{background:var(--astron-sh-soft,#8881)}
.skillhub-controls .astron-sh-markdown hr{border:0;border-top:1px solid var(--astron-sh-border,#8883);margin:1.5em 0}
`}</style>;
}
