import ReactMarkdown from 'react-markdown';
import rehypeSanitize from 'rehype-sanitize';
import type { TypertRemoteNamespaceMap } from '@deepseek-ai/dsh-typert-protocol';
import type { SkillDetail, SkillHubConfig } from '../host/types.js';
import { DetailTabs } from './detail-tabs.js';
import { stripMarkdownFrontmatter } from './markdown-content.js';
import { MarkdownStyles } from './markdown-styles.js';
import '../../lib/typert.remote-client.js';

export function DetailContent({ detail, modes, remote, retry, onSelectVersion, versionActionLabel, cacheIdentity, onContextStale }: {
  detail: SkillDetail; modes: SkillHubConfig['detailTabs'];
  remote: TypertRemoteNamespaceMap['skillhub']; retry: () => void; onSelectVersion?(version: string): void; versionActionLabel?: string; cacheIdentity?: string; onContextStale?(): void;
}) {
  return <>
    <MarkdownStyles />
    <DetailTabs key={cacheIdentity ?? `${detail.namespace}/${detail.slug}/${detail.version}`} cacheIdentity={cacheIdentity} onContextStale={onContextStale} detail={detail} modes={modes} remote={remote} onSelectVersion={onSelectVersion} versionActionLabel={versionActionLabel}>
    {detail.documentation.state === 'error' ? <div role="alert"><p>当前版本的说明暂时不可读取。</p><button onClick={retry}>重试</button></div>
      : <div className="astron-sh-markdown"><ReactMarkdown rehypePlugins={[rehypeSanitize]} skipHtml components={{
        img: ({ alt }) => <span>{alt ? `[图片：${alt}]` : '[图片]'}</span>,
        a: ({ href, children }) => href && /^https?:\/\//i.test(href) ? <a href={href} target="_blank" rel="noopener noreferrer">{children}</a> : <span>{children}</span>,
      }}>{stripMarkdownFrontmatter(detail.documentation.content)}</ReactMarkdown></div>}
  </DetailTabs></>;
}
