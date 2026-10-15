import ReactMarkdown from 'react-markdown';
import rehypeSanitize from 'rehype-sanitize';
import type { TypertRemoteNamespaceMap } from '@deepseek-ai/dsh-typert-protocol';
import type { SkillDetail, SkillHubConfig } from '../host/types.js';
import { DetailTabs } from './detail-tabs.js';
import '../../lib/typert.remote-client.js';

export function DetailContent({ detail, modes, remote, retry }: {
  detail: SkillDetail; modes: SkillHubConfig['detailTabs'];
  remote: TypertRemoteNamespaceMap['skillhub']; retry: () => void;
}) {
  const pendingModules = (['versions', 'securityAudit', 'reviews'] as const)
    .filter(module => modes[module] === 'auto' && detail.tabs[module] === 'unknown')
    .map(module => ({ versions: '版本', securityAudit: '安全审核', reviews: '评论' })[module]);
  return <>
    {pendingModules.length > 0 && <div role="status">
      <p>{pendingModules.join('、')}是否可读取尚未确认，不代表没有内容。</p>
      <button onClick={retry}>重新检查可选内容</button>
    </div>}
    <DetailTabs key={detail.detailToken} detail={detail} modes={modes} remote={remote}>
    {detail.documentation.state === 'error' ? <div role="alert"><p>当前版本的说明暂时不可读取。</p><button onClick={retry}>重试</button></div>
      : <ReactMarkdown rehypePlugins={[rehypeSanitize]} skipHtml components={{
        img: ({ alt }) => <span>{alt ? `[图片：${alt}]` : '[图片]'}</span>,
        a: ({ href, children }) => href && /^https?:\/\//i.test(href) ? <a href={href} target="_blank" rel="noopener noreferrer">{children}</a> : <span>{children}</span>,
        pre: ({ children }) => <pre style={{ overflowX: 'auto', padding: 12, background: '#8881' }}>{children}</pre>,
      }}>{detail.documentation.content}</ReactMarkdown>}
  </DetailTabs></>;
}
