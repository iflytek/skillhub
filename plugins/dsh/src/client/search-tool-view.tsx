import { NativeControlStyles } from './native-control-styles.js';
import type { ToolCallViewProps } from '@deepseek-ai/dsh-client-ui-tool/client';
import { useState } from 'react';
import { ConversationDetail } from './conversation-detail.js';
import type { SearchViewServices } from './conversation-detail.js';
import { parseSearchResult } from './search-result.js';

/** Only the original call's validated metadata is rendered; opening a transcript makes no requests. */
export function SearchToolView(props: ToolCallViewProps & { services: SearchViewServices }) {
  return <div className="skillhub-controls" style={{ minWidth: 0, overflowWrap: 'anywhere', font: 'inherit' }}>
    <NativeControlStyles />
    <SearchToolViewContent {...props} />
  </div>;
}

function SearchToolViewContent(props: ToolCallViewProps & { services: SearchViewServices }) {
  const [selection, setSelection] = useState<string | null>(null);
  const [opening, setOpening] = useState(false);
  const [navigationError, setNavigationError] = useState('');
  if (props.phase === 'preparing') return <p role="status">正在准备 SkillHub 搜索…</p>;
  if (props.phase === 'start') return <p role="status">正在搜索 SkillHub…</p>;
  if (props.block.isError) return <section aria-label="SkillHub 搜索失败">
    <p role="alert">本次技能搜索未完成。</p>
    {props.inspect && <button onClick={props.inspect}>查看调用详情</button>}
  </section>;
  const result = parseSearchResult(props.block.meta);
  if (!result) return <section aria-label="SkillHub 搜索记录">
    <p>这条记录缺少可识别的搜索结果，暂时无法展示卡片。</p>
    {props.inspect && <button onClick={props.inspect}>查看调用详情</button>}
  </section>;
  return <section aria-label="SkillHub 搜索结果" style={{ border: '1px solid #8885', borderRadius: 12, padding: 16 }}>
    {selection && <ConversationDetail reference={selection} callId={props.callId} originalSource={result.sourceLabel} services={props.services} close={() => setSelection(null)} />}
    <h3 style={{ marginTop: 0 }}>SkillHub · {result.query || '最近技能'}</h3>
    <p style={{ opacity: .65, overflowWrap: 'anywhere' }}>{result.sourceLabel} · <time dateTime={result.createdAt}>{result.createdAt}</time></p>
    <p>当次搜索共匹配 {result.total} 项，显示 {result.returned} 项。搜索未安装或运行技能。</p>
    {result.items.length === 0 ? <p>没有匹配的技能，可换个关键词再搜索。</p> : <ul style={{ paddingLeft: 0, listStyle: 'none' }}>
      {result.items.map(item => <li key={item.selectionRef} style={{ marginBottom: 16, overflowWrap: 'anywhere' }}>
        <button aria-label="查看技能详情" aria-describedby={`skillhub-candidate-${item.selectionRef}`}
          style={{ display: 'block', width: '100%', textAlign: 'start', padding: 16, whiteSpace: 'normal', overflowWrap: 'anywhere' }}
          onClick={() => setSelection(item.selectionRef)}>
          <strong>{item.slug}</strong>
          <span id={`skillhub-candidate-${item.selectionRef}`} style={{ display: 'block', marginTop: 12 }}>@{item.namespace}/{item.slug} · {item.latestVersion}</span>
          <span style={{ display: 'block', marginTop: 12 }}>{item.summary || '暂无摘要'}</span>
          <span style={{ display: 'block', marginTop: 12 }}>查看技能详情 →</span>
        </button>
      </li>)}
    </ul>}
    <button disabled={opening} onClick={() => {
      setOpening(true); setNavigationError('');
      void props.services.openCatalog(result)
        .catch(() => setNavigationError('无法打开原来源的技能广场，请确认来源设置、会话及广场入口仍可用。'))
        .finally(() => setOpening(false));
    }}>{opening ? '正在打开技能广场…' : '打开技能广场'}</button>
    {navigationError && <p role="alert">{navigationError}</p>}
    {props.inspect && <button onClick={props.inspect}>查看调用详情</button>}
  </section>;
}
