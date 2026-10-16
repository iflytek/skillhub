import { NativeControlStyles } from './native-control-styles.js';
import type { ToolCallViewProps } from '@deepseek-ai/dsh-client-ui-tool/client';
import { useState } from 'react';
import { ConversationDetail } from './conversation-detail.js';
import type { SearchViewServices } from './conversation-detail.js';
import { Icon, SkillAvatar } from './skill-card.js';
import { Button, Card } from './ui.js';
import { readableDate } from './skill-presentation.js';
import { ConversationStyles } from './conversation-styles.js';
import { parseSearchResult } from './search-result.js';

/** Only the original call's validated metadata is rendered; opening a transcript makes no requests. */
export function SearchToolView(props: ToolCallViewProps & { services: SearchViewServices }) {
  return <div className="skillhub-controls" style={{ minWidth: 0, overflowWrap: 'anywhere', font: 'inherit' }}>
    <NativeControlStyles /><ConversationStyles />
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
    {props.inspect && <Button variant="ghost" size="sm" onClick={props.inspect}>查看调用详情</Button>}
  </section>;
  const result = parseSearchResult(props.block.meta);
  if (!result) return <section aria-label="SkillHub 搜索记录">
    <p>这条记录缺少可识别的搜索结果，暂时无法展示卡片。</p>
    {props.inspect && <Button variant="ghost" size="sm" onClick={props.inspect}>查看调用详情</Button>}
  </section>;
  return <section aria-label="SkillHub 搜索结果" className="astron-sh-conversation-result">
    {selection && <ConversationDetail reference={selection} callId={props.callId} originalSource={result.sourceLabel} services={props.services} close={() => setSelection(null)} browseVersions={() => {
      setOpening(true); setNavigationError('');
      void props.services.openCatalog(result).then(() => setSelection(null))
        .catch(() => setNavigationError('无法打开原来源的技能广场，请确认来源设置、会话及广场入口仍可用。'))
        .finally(() => setOpening(false));
    }} />}
    <header className="astron-sh-conversation-heading"><span className="astron-sh-conversation-mark"><Icon name="search" /></span><h3>{result.query || '最近技能'}</h3><span>SkillHub</span></header>
    <p className="astron-sh-conversation-caption">共匹配 {result.total} 项，显示 {result.returned} 项 · 查看详情，或回复序号安装</p>
    {result.items.length === 0 ? <p>没有匹配的技能，可换个关键词再搜索。</p> : <div className="astron-sh-conversation-list">
      {result.items.map(item => <Card key={item.selectionRef} className="astron-sh-conversation-skill">
        <div className="astron-sh-conversation-identity"><SkillAvatar slug={item.slug} namespace={item.namespace} /><div><h4>{item.slug}</h4><p title={`@${item.namespace}/${item.slug}`}>@{item.namespace}/{item.slug}<span> · v{item.latestVersion}</span></p></div></div>
        <p id={`skillhub-candidate-${item.selectionRef}`} className="astron-sh-conversation-summary" title={item.summary}>{item.summary || '暂无摘要'}</p>
        <Button variant="outline" className="astron-sh-conversation-detail-action" aria-label="查看技能详情" aria-describedby={`skillhub-candidate-${item.selectionRef}`} onClick={() => setSelection(item.selectionRef)}>查看详情<Icon name="arrow" /></Button>
      </Card>)}
    </div>}
    <div className="astron-sh-conversation-actions"><Button variant="outline" disabled={opening} onClick={() => {
      setOpening(true); setNavigationError('');
      void props.services.openCatalog(result)
        .catch(() => setNavigationError('无法打开原来源的技能广场，请确认来源设置、会话及广场入口仍可用。'))
        .finally(() => setOpening(false));
    }}>{opening ? '正在打开技能广场…' : '打开技能广场'}</Button>
    {props.inspect && <Button variant="ghost" size="sm" onClick={props.inspect}>查看调用详情</Button>}
    </div>
    {navigationError && <p role="alert">{navigationError}</p>}
    <details className="astron-sh-conversation-provenance"><summary>搜索记录</summary><p>{result.sourceLabel}</p><p>搜索于 <time dateTime={result.createdAt}>{readableDate(result.createdAt, true)}</time> · 本次仅搜索，未安装或运行技能。</p></details>
  </section>;
}
