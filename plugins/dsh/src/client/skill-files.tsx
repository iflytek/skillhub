import { useEffect, useLayoutEffect, useMemo, useRef, useState, type RefObject } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import type { TypertRemoteNamespaceMap, RemoteResult } from '@deepseek-ai/dsh-typert-protocol';
import { Button } from './ui.js';
import { Icon } from './skill-card.js';
import { readCacheOptions } from './read-cache.js';
import { buildSkillFileTree, fileAncestors, fileBrowserStateKey, fileLineNumbers, revealFileDirectories, type SkillFileBrowserState, type SkillFileNode } from './file-tree.js';
import '../../lib/typert.remote-client.js';

type Remote = TypertRemoteNamespaceMap['skillhub'];
class StaleFilesError extends Error {}
function value<T>(result: RemoteResult<T>): T {
  if (result.ok) return result.value;
  if (result.error.code === 'skillhub/context-stale') throw new StaleFilesError('详情已过期，正在重新读取。');
  if (result.error.code === 'skillhub/registry-error' && result.error.details.reason === 'ANONYMOUS_DENIED') {
    throw new Error('当前来源不允许匿名读取文件。');
  }
  throw new Error('文件暂时无法读取，请重试；来源或版本变化后请重新打开详情。');
}

export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  return bytes < 1024 * 1024 ? `${(bytes / 1024).toFixed(1)} KB` : `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/** Mounted only by the active Files tab. Neither directories nor other bodies load with catalog cards. */
export function SkillFiles({ token, identity, remote, onContextStale, rootName }: {
  token: string; identity: string; remote: Remote; onContextStale?(): void; rootName?: string;
}) {
  const client = useQueryClient();
  const saved = client.getQueryData<SkillFileBrowserState>(fileBrowserStateKey(identity, token));
  const [selected, setSelected] = useState<string | null>(saved?.selected ?? null);
  const [copied, setCopied] = useState<'idle' | 'pending' | 'done' | 'error'>('idle');
  const list = useQuery({ ...readCacheOptions, queryKey: ['skillhub-files', identity, token], retry: false,
    queryFn: async ({ signal }) => value(await remote.listSkillFiles(token, signal)),
  });
  const items = list.data?.state === 'ready' ? list.data.items : [];
  const file = items.find(file => file.path === selected) ?? items.find(file => file.path === 'SKILL.md') ?? items[0];
  const tree = useMemo(() => buildSkillFileTree(list.data?.state === 'ready' ? list.data.items : []), [list.data]);
  const [expanded, setExpanded] = useState<ReadonlySet<string>>(() => new Set(saved?.expanded ?? ['', ...tree.children.filter(node => node.kind === 'directory').map(node => node.path), ...fileAncestors(file?.path ?? '')]));
  const initializedTree = useRef(!!saved || list.data?.state === 'ready');
  const previousSelection = useRef(saved?.selected ?? file?.path);
  useEffect(() => {
    if (list.data?.state !== 'ready' || !initializedTree.current) return;
    client.setQueryData<SkillFileBrowserState>(fileBrowserStateKey(identity, token), { selected: file?.path ?? selected, expanded: [...expanded] });
  }, [client, identity, token, list.data?.state, file?.path, selected, expanded]);
  const packageName = rootName?.trim() || '技能包';
  const treeViewport = useRef<HTMLDivElement>(null);
  const selectedRow = useRef<HTMLButtonElement>(null);
  const revealedPath = useRef<string>();
  useEffect(() => {
    if (list.data?.state !== 'ready') return;
    const initialize = !initializedTree.current;
    const changed = previousSelection.current !== file?.path;
    initializedTree.current = true; previousSelection.current = file?.path;
    if (!initialize && !changed) return;
    setExpanded(current => {
      const next = initialize ? new Set([...current, ...tree.children.filter(node => node.kind === 'directory').map(node => node.path)]) : current;
      return file && changed ? revealFileDirectories(next, file.path) : next;
    });
  }, [list.data?.state, tree, file?.path]);
  useLayoutEffect(() => {
    const viewport = treeViewport.current, row = selectedRow.current;
    if (!file || revealedPath.current === file.path || !viewport || !row || !viewport.contains(row)) return;
    const bounds = viewport.getBoundingClientRect(), selectedBounds = row.getBoundingClientRect();
    if (selectedBounds.top < bounds.top) viewport.scrollTop -= bounds.top - selectedBounds.top;
    else if (selectedBounds.bottom > bounds.bottom) viewport.scrollTop += selectedBounds.bottom - bounds.bottom;
    revealedPath.current = file.path;
  }, [file?.path, expanded]);
  function toggleDirectory(path: string) {
    setExpanded(current => {
      const next = new Set(current);
      if (next.has(path)) next.delete(path); else next.add(path);
      return next;
    });
  }
  function selectFile(path: string) {
    setExpanded(current => revealFileDirectories(current, path));
    setSelected(path);
  }
  const preview = useQuery({ ...readCacheOptions, queryKey: ['skillhub-file', identity, token, file?.path], retry: false,
    enabled: !!file && list.data?.state === 'ready' && !list.isError,
    queryFn: async ({ signal }) => value(await remote.readSkillFile(token, file!.path, signal)),
  });
  const text = !preview.isError && preview.data?.state === 'text' ? preview.data.content : null;
  const lineNumbers = useMemo(() => text === null ? '' : fileLineNumbers(text), [text]);
  const activePath = useRef(file?.path);
  activePath.current = file?.path;
  useEffect(() => setCopied('idle'), [file?.path]);
  const notified = useRef(false);
  useEffect(() => {
    if (!notified.current && (list.error instanceof StaleFilesError || preview.error instanceof StaleFilesError)) {
      notified.current = true; onContextStale?.();
    }
  }, [list.error, preview.error, onContextStale]);
  const copy = async () => {
    if (text === null) return;
    const path = file?.path;
    setCopied('pending');
    try {
      await navigator.clipboard.writeText(text);
      if (activePath.current === path) setCopied('done');
    } catch { if (activePath.current === path) setCopied('error'); }
  };

  return <div className="astron-sh-files"><FileStyles />
    {list.isPending ? <p role="status">正在读取文件清单…</p> : list.isError ? <div role="alert"><p>{list.error.message}</p><Button onClick={() => { void list.refetch(); }}>重试</Button></div>
      : list.data.state === 'unsupported' ? <p role="status">当前来源未提供文件清单。仍可查看概览或安装可下载版本。</p>
        : items.length === 0 ? <p role="status">当前版本没有可展示的文件。</p>
          : <><div className="astron-sh-file-workspace">
            <nav className="astron-sh-file-list" aria-label="版本文件列表">
              <header><strong>文件</strong><span>{formatFileSize(items.reduce((sum, file) => sum + file.size, 0))}</span></header>
              <div className="astron-sh-file-list-items" ref={treeViewport}>
                <button type="button" className="astron-sh-file-root" aria-label={`${packageName} 根目录`} aria-expanded={expanded.has('')} title={packageName} onClick={() => toggleDirectory('')}>
                  <Icon name={expanded.has('') ? 'chevron-down' : 'chevron-right'} /><Icon name={expanded.has('') ? 'folder-open' : 'folder'} /><span>{packageName}</span>
                </button>
                {expanded.has('') && <FileTreeNodes nodes={tree.children} expanded={expanded} selected={file?.path} selectedRow={selectedRow} toggleDirectory={toggleDirectory} selectFile={selectFile} />}
              </div>
            </nav>
            <section className="astron-sh-file-content" aria-label={`文件预览 ${file!.path}`}>
              <header><span className="astron-sh-file-breadcrumb-root" title={packageName}>{packageName}</span><Icon name="chevron-right" /><strong title={file!.path}>{file!.path.split('/').join(' / ')}</strong>
                <span className="astron-sh-file-readonly">只读</span><Button size="sm" disabled={text === null || copied === 'pending'} onClick={() => { void copy(); }}>
                  <Icon name="copy" />{copied === 'pending' ? '复制中' : copied === 'done' ? '已复制' : '复制'}
                </Button></header>
              {preview.isPending ? <div className="astron-sh-file-message" role="status">正在读取文件…</div>
                : preview.isError ? <div className="astron-sh-file-message" role="alert"><p>{preview.error.message}</p><Button onClick={() => { void preview.refetch(); }}>重试</Button></div>
                  : text !== null ? <div className="astron-sh-file-code" tabIndex={0} role="region" aria-label="只读文件正文"><div className="astron-sh-file-code-grid">
                    <pre className="astron-sh-file-line-numbers" aria-hidden="true">{lineNumbers}</pre><pre className="astron-sh-file-text"><code>{text}</code></pre>
                  </div></div>
                    : <div className="astron-sh-file-message" role="status"><Icon name="info" /><p>{preview.data.state === 'binary' ? '二进制文件，仅展示文件信息。'
                      : preview.data.state === 'too-large' ? `文件超过 ${formatFileSize(preview.data.limitBytes)} 的预览上限。`
                        : '此文件无法作为 UTF-8 文本预览。'}</p></div>}
              {copied === 'error' && <p role="alert">无法写入剪贴板，可在正文中选择并复制。</p>}
            </section>
            <footer className="astron-sh-file-status"><span>{tree.fileCount} 个文件 · {tree.directoryCount} 个目录</span><span>{text !== null ? 'UTF-8 · 只读' : '只读'}</span></footer>
          </div></>}
  </div>;
}

function FileTreeNodes({ nodes, expanded, selected, selectedRow, toggleDirectory, selectFile }: {
  nodes: SkillFileNode[]; expanded: ReadonlySet<string>; selected?: string; selectedRow: RefObject<HTMLButtonElement>;
  toggleDirectory(path: string): void; selectFile(path: string): void;
}) {
  return <ul className="astron-sh-file-tree-nodes">{nodes.map(node => <li key={`${node.kind}:${node.path}`}>
    {node.kind === 'directory' ? <>
      <button type="button" aria-label={`目录 ${node.path}`} title={`${node.path}/`} aria-expanded={expanded.has(node.path)} onClick={() => toggleDirectory(node.path)}>
        <Icon name={expanded.has(node.path) ? 'chevron-down' : 'chevron-right'} /><Icon name={expanded.has(node.path) ? 'folder-open' : 'folder'} /><span>{node.name}</span>
      </button>
      {expanded.has(node.path) && <FileTreeNodes nodes={node.children} expanded={expanded} selected={selected} selectedRow={selectedRow} toggleDirectory={toggleDirectory} selectFile={selectFile} />}
    </> : <button type="button" ref={node.path === selected ? selectedRow : undefined} aria-pressed={node.path === selected}
      aria-label={`${node.path} ${formatFileSize(node.file.size)}`} title={`${node.path} · ${formatFileSize(node.file.size)}`} onClick={() => selectFile(node.path)}>
      <span className="astron-sh-file-tree-spacer" aria-hidden="true" /><Icon name="file" /><span>{node.name}</span>
    </button>}
  </li>)}</ul>;
}

function FileStyles() {
  return <style>{`
.skillhub-controls .astron-sh-files{margin-top:24px;padding:0;font-size:13px;line-height:20px;min-width:0;container-type:inline-size}
.skillhub-controls .astron-sh-file-workspace{display:grid;grid-template-columns:232px minmax(0,1fr);grid-template-rows:minmax(0,1fr) 26px;height:clamp(480px,65dvh,640px);box-sizing:border-box;border:1px solid var(--astron-sh-border);border-radius:10px;overflow:hidden;min-width:0;background:var(--astron-sh-surface)}
.skillhub-controls .astron-sh-file-list{display:flex;flex-direction:column;min-height:0;min-width:0;padding:0;background:var(--astron-sh-soft);border-right:1px solid var(--astron-sh-border);overflow:hidden}
.skillhub-controls .astron-sh-file-list>header{display:flex;align-items:center;justify-content:space-between;gap:8px;flex:none;min-height:44px;padding:8px 12px;box-sizing:border-box;border-bottom:1px solid var(--astron-sh-border-subtle)}
.skillhub-controls .astron-sh-file-list>header>strong{font-size:12px;font-weight:500;color:var(--astron-sh-secondary)}
.skillhub-controls .astron-sh-file-list>header>span{font-size:11px;color:var(--astron-sh-muted);white-space:nowrap}
.skillhub-controls .astron-sh-file-list-items{flex:1;min-height:0;padding:8px;overflow:auto}
.skillhub-controls .astron-sh-file-list button{width:100%;display:flex;align-items:center;gap:6px;padding:4px 8px;text-align:left;border:0;border-radius:4px;background:transparent;color:var(--astron-sh-fg);font-family:inherit;font-size:12px;line-height:20px;cursor:pointer;min-height:28px;height:28px;box-sizing:border-box}
.skillhub-controls .astron-sh-file-list .astron-sh-file-root{height:32px;min-height:32px;font-weight:500}
.skillhub-controls .astron-sh-file-list button:hover{background:light-dark(#eceff3,#25292f)}
.skillhub-controls .astron-sh-file-list button[aria-pressed=true]{background:light-dark(#eaf4ff,#153047);color:light-dark(#155e92,#a8d4f5);box-shadow:inset 2px 0 var(--astron-sh-link)}
.skillhub-controls .astron-sh-file-list button:focus-visible{outline:2px solid var(--astron-sh-link);outline-offset:-2px}
.skillhub-controls .astron-sh-file-list svg{width:14px;height:14px;flex:none;color:var(--astron-sh-muted)}
.skillhub-controls .astron-sh-file-list button[aria-pressed=true] svg{color:inherit}
.skillhub-controls .astron-sh-file-list button>span:last-child{min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.skillhub-controls .astron-sh-file-list .astron-sh-file-tree-spacer{display:block;width:14px;flex:none}
.skillhub-controls .astron-sh-file-tree-nodes{margin:0 0 0 16px;padding:0;border-left:1px solid var(--astron-sh-border);list-style:none}
.skillhub-controls .astron-sh-file-tree-nodes>li{margin:0;padding:0;list-style:none}
.skillhub-controls .astron-sh-file-content{display:flex;flex-direction:column;min-height:0;min-width:0;overflow:hidden;background:var(--astron-sh-surface)}
.skillhub-controls .astron-sh-file-content>header{display:flex;align-items:center;gap:8px;padding:7px 16px;border-bottom:1px solid var(--astron-sh-border-subtle);min-width:0;min-height:44px;box-sizing:border-box;flex:none}
.skillhub-controls .astron-sh-file-content>header>svg{width:14px;height:14px;flex:none;color:var(--astron-sh-muted)}
.skillhub-controls .astron-sh-file-content>header>strong{flex:1;min-width:0;font-size:12px;font-weight:400;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.skillhub-controls .astron-sh-file-content>header>span{font-size:12px;color:var(--astron-sh-muted);white-space:nowrap;flex:none}
.skillhub-controls .astron-sh-file-content>header>.astron-sh-file-breadcrumb-root{max-width:140px;overflow:hidden;text-overflow:ellipsis}
.skillhub-controls .astron-sh-file-content>header>button{flex:none}
.skillhub-controls .astron-sh-file-content>p[role=alert]{padding:8px 16px;margin:0;font-size:12px;line-height:18px;flex:none;max-height:72px;overflow:auto;border-top:1px solid var(--astron-sh-border-subtle)}
.skillhub-controls .astron-sh-file-code{box-sizing:border-box;flex:1;min-height:0;max-width:100%;overflow:auto;background:var(--astron-sh-surface);color:var(--astron-sh-fg);font:14px/24px ui-monospace,SFMono-Regular,Menlo,Monaco,Consolas,"Liberation Mono","Courier New",monospace;tab-size:2}
.skillhub-controls .astron-sh-file-code-grid{display:grid;grid-template-columns:max-content minmax(max-content,1fr);width:max-content;min-width:100%;min-height:100%}
.skillhub-controls .astron-sh-file-code-grid>pre{margin:0;border:0;border-radius:0;white-space:pre;font:inherit;color:inherit;box-sizing:border-box}
.skillhub-controls .astron-sh-file-code-grid>.astron-sh-file-line-numbers{position:sticky;left:0;z-index:1;padding:16px 12px;min-width:48px;text-align:right;user-select:none;background:var(--astron-sh-surface);border-right:1px solid var(--astron-sh-border-subtle);color:var(--astron-sh-muted)}
.skillhub-controls .astron-sh-file-text{padding:16px;background:transparent}
.skillhub-controls .astron-sh-file-text>code{font:inherit;color:inherit}
.skillhub-controls .astron-sh-file-message{box-sizing:border-box;flex:1;min-height:0;padding:24px;overflow:auto}
.skillhub-controls .astron-sh-file-message>svg{width:20px;height:20px;color:var(--astron-sh-muted)}
.skillhub-controls .astron-sh-file-status{grid-column:1/-1;display:flex;align-items:center;justify-content:space-between;gap:12px;min-width:0;padding:3px 12px;border-top:1px solid var(--astron-sh-border);background:var(--astron-sh-soft);font-size:11px;line-height:18px;color:var(--astron-sh-muted);white-space:nowrap}
@container(max-width:680px){
 .skillhub-controls .astron-sh-file-workspace{grid-template-columns:minmax(0,1fr);grid-template-rows:144px minmax(0,1fr) 26px;height:clamp(480px,80dvh,640px);max-height:calc(100dvh - 48px)}
 .skillhub-controls .astron-sh-file-list{border-right:0;border-bottom:1px solid var(--astron-sh-border)}
 .skillhub-controls .astron-sh-file-list>header{min-height:32px;padding:5px 12px}
 .skillhub-controls .astron-sh-file-list-items{padding:4px 8px}
 .skillhub-controls .astron-sh-file-content>header{gap:6px;padding:7px 12px}
 .skillhub-controls .astron-sh-file-content>header>.astron-sh-file-breadcrumb-root,.skillhub-controls .astron-sh-file-content>header>svg{display:none}
 .skillhub-controls .astron-sh-file-code{font-size:13px;line-height:22px}
 .skillhub-controls .astron-sh-file-text{padding:16px 12px}
 .skillhub-controls .astron-sh-file-code-grid>.astron-sh-file-line-numbers{padding:16px 8px;min-width:36px}
 .skillhub-controls .astron-sh-file-message{padding:20px}
 .skillhub-controls .astron-sh-file-status{gap:8px;padding-inline:8px;font-size:10px}
}
`}</style>;
}
