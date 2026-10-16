import { UiAvailability, UiAvailabilityNotice } from './ui-availability.js';
import type { Context } from '@deepseek-ai/cordis';
import { SessionId } from '@deepseek-ai/dsh-session/types';
import type {} from '@deepseek-ai/dsh-api-gateway/client';
import type {} from '@deepseek-ai/dsh-api-session-controller/client';
import type { AgentContext } from '@deepseek-ai/dsh-api-session-controller/client';
import type {} from '@deepseek-ai/dsh-client-ui-renderer/client';
import type {} from '@deepseek-ai/dsh-client-ui-session/client';
import type {} from '@deepseek-ai/dsh-client-ui-sidebar/client';
import type { MainPanelId } from '@deepseek-ai/dsh-client-ui-layout/client';
import type {} from '@deepseek-ai/dsh-client-ui-settings/client';
import type {} from '@deepseek-ai/dsh-client-ui-plugin-manager/client';
import type { SkillHubConfig } from '../host/types.js';
import { ConfigPage } from './config-page.js';
import type { ConversationSearchResult } from '../host/search-result.js';
import { SearchToolView } from './search-tool-view.js';
import { CatalogUnavailableError, InstallToolView } from './install-tool-view.js';
import type { InstallResult } from './install-result.js';
import { QueryClient } from '@tanstack/react-query';
import { CatalogPage, createCatalogNavigation } from './catalog-page.js';
import { UpgradeRequests, RemovalRequests } from './upgrade-requests.js';
import { InstallationRequests } from './installation-requests.js';
import type { InstalledServices } from './installed-skills.js';
import type { InstallationServices } from './install-control.js';
import { TYPERT_REMOTE } from '../../lib/typert.remote-client.js';

declare module '@deepseek-ai/dsh-client-ui-slots' {
  interface SlotMap {
    'skillhub.catalog': { kind: 'single'; scope: 'session-maybe' };
  }
}

export const inject = ['remote', 'slots'];
const panelId = 'skillhub' as MainPanelId;

/** Register the native catalog and configuration surfaces. */
export async function apply(ctx: Context): Promise<() => Promise<void>> {
  const availability = new UiAvailability();
  const unmount = await ctx.remote.$mount(TYPERT_REMOTE);
  ctx.inject(['configForms', 'slots', 'remote.skillhub'], (ctx) => {
    const form = ctx.configForms.get<SkillHubConfig>('astron-skillhub');
    ctx.slots.inject('plugins.bundle.config', () => availability.enable('configuration'));
    ctx.slots.inject('plugins.bundle.config', () => ctx.slots.register({
      name: 'plugins.bundle.config', key: '@astron-team/skillhub-dsh',
    }, () => <ConfigPage form={form} testConnection={(url, signal) => ctx.remote.skillhub.testRegistryConnection(url, signal)} />));
  });
  const panel = ctx.inject(['remote.skillhub', 'slots', 'sessions', 'uiSession'], (ctx) => {
    const client = new QueryClient();
    ctx.slots.inject('tool.call.toolview', () => availability.enable('conversation'));
    const navigation = createCatalogNavigation();
    const requests = new InstallationRequests(() => window.sessionStorage);
    const upgradeRequests = new UpgradeRequests(() => window.sessionStorage);
    const removalRequests = new RemovalRequests(() => window.sessionStorage);
    ctx.effect(() => () => { requests.dispose(); upgradeRequests.dispose(); removalRequests.dispose(); client.clear(); });
    function installationFor(sessionId: string): InstallationServices {
      const call = async <T,>(action: (remote: AgentContext['remote']) => Promise<T>): Promise<T> => {
        const scope = ctx.sessions.scope(SessionId(sessionId));
        if (!scope) throw new Error('当前会话已关闭，请重新打开会话。');
        return withSessionRemote(scope, action);
      };
      return { sessionId, requests, compatibility: () => ctx.remote.skillhub.getCompatibility(),
        targets: (token, signal) => call(remote => remote.skillhub.listInstallTargets(token, signal)),
        installed: (revision, signal) => call(remote => remote.skillhub.listInstalled(revision, signal)),
        start: (input, signal) => call(remote => remote.skillhub.startInstall(input, signal)),
        operation: (id, host) => call(remote => remote.skillhub.getOperation(id, host)),
      };
    }
    ctx.slots.inject('tool.call.toolview', () => ctx.slots.register({
      name: 'tool.call.toolview', key: 'astron_skillhub_install',
      inject: sessionId => ({ services: { client, installation: installationFor(sessionId), openInstalled: async (result: InstallResult) => {
        const layout = ctx.get('layout');
        if (!layout) throw new CatalogUnavailableError();
        const current = await ctx.remote.skillhub.getContext();
        if (!current.ok || current.value.sourceRevision !== result.sourceRevision || current.value.registryUrl !== result.sourceLabel
          || !ctx.sessions.scope(SessionId(sessionId))) throw new Error('Installation context changed');
        navigation.tab = 'installed'; navigation.selected = null; navigation.scrollTop = 0;
        navigation.installedOrigin = { sessionId, sourceRevision: result.sourceRevision };
        if (ctx.get('layout') !== layout) throw new CatalogUnavailableError();
        layout.selectPanel(panelId);
      } } }),
    }, props => <><UiAvailabilityNotice availability={availability} /><InstallToolView {...props} /></>));
    ctx.slots.inject('tool.call.toolview', () => ctx.slots.register({
      name: 'tool.call.toolview', key: 'astron_skillhub_search',
      inject: sessionId => ({ services: {
        client, remote: ctx.remote.skillhub,
        installation: installationFor(sessionId),
        openCatalog: async (result: ConversationSearchResult) => {
          const layout = ctx.get('layout');
          if (!layout) throw new CatalogUnavailableError();
          const current = await ctx.remote.skillhub.getContext();
          if (!current.ok || current.value.registryUrl !== result.sourceLabel
            || !ctx.sessions.scope(SessionId(sessionId)) || ctx.get('layout') !== layout) {
            throw new Error('Search source or conversation changed');
          }
          navigation.tab = 'browse'; navigation.selected = null; navigation.scrollTop = 0;
          navigation.installedOrigin = undefined;
          navigation.query = result.query; navigation.draft = result.query;
          navigation.sourceRevision = current.value.sourceRevision;
          client.setQueryData(['skillhub-context'], current.value);
          layout.selectPanel(panelId);
        },
        openDetail: async (reference: string, signal: AbortSignal) => {
          const scope = ctx.sessions.scope(SessionId(sessionId));
          if (!scope) throw new Error('当前会话已关闭，请重新打开会话。');
          return withSessionRemote(scope, remote => remote.skillhub.openConversationDetail(reference, signal));
        },
        reopenDetail: async (callId: string, reference: string, signal: AbortSignal) => {
          const scope = ctx.sessions.scope(SessionId(sessionId));
          if (!scope) throw new Error('当前会话已关闭，请重新打开会话。');
          return withSessionRemote(scope, remote => remote.skillhub.reopenConversationDetail(callId, reference, signal));
        },
        acknowledgeDetail: async (reference: string, token: string) => {
          const scope = ctx.sessions.scope(SessionId(sessionId));
          if (!scope) throw new Error('当前会话已关闭，请重新打开会话。');
          return withSessionRemote(scope, remote => remote.skillhub.acknowledgeConversationDetail(reference, token));
        },
      } }),
    }, props => <><UiAvailabilityNotice availability={availability} /><SearchToolView {...props} /></>));
    ctx.inject(['layout'], (ctx) => {
      ctx.slots.inject('main', () => availability.enable('main'));
      ctx.slots.inject('sidebar.panellist', () => availability.enable('sidebar'));
      ctx.slots.inject('main', () => ctx.slots.register({ name: 'main', key: panelId,
        children: { 'skillhub.catalog': { kind: 'single', scope: 'session-maybe' } },
      }, ({ renderSlot }) => renderSlot('skillhub.catalog', {})));
      ctx.slots.inject('skillhub.catalog', () => ctx.slots.register({ name: 'skillhub.catalog',
        inject: sessionId => ({ installation: sessionId ? installationFor(sessionId) : undefined,
          installed: sessionId ? { sessionId, upgradeRequests, removalRequests, compatibility: () => ctx.remote.skillhub.getCompatibility(),
            operation: installationFor(sessionId).operation,
            recoverOperation: async input => {
              const scope = ctx.sessions.scope(SessionId(sessionId));
              if (!scope) throw new Error('当前会话已关闭，请重新打开会话。');
              return withSessionRemote(scope, remote => remote.skillhub.recoverOperation(input));
            },
            prepareUninstall: async (revision, itemKey, signal) => {
              const scope = ctx.sessions.scope(SessionId(sessionId));
              if (!scope) throw new Error('当前会话已关闭，请重新打开会话。');
              return withSessionRemote(scope, remote => remote.skillhub.prepareUninstall(revision, itemKey, signal));
            },
            prepareUpgrade: async (revision, itemKey, signal) => {
              const scope = ctx.sessions.scope(SessionId(sessionId));
              if (!scope) throw new Error('当前会话已关闭，请重新打开会话。');
              return withSessionRemote(scope, remote => remote.skillhub.prepareUpgrade(revision, itemKey, signal));
            }, executePlan: async (input, signal) => {
              const scope = ctx.sessions.scope(SessionId(sessionId));
              if (!scope) throw new Error('当前会话已关闭，请重新打开会话。');
              return withSessionRemote(scope, remote => remote.skillhub.executePlan(input, signal));
            }, list: async (revision, signal) => {
            const scope = ctx.sessions.scope(SessionId(sessionId));
            if (!scope) throw new Error('当前会话已关闭，请重新打开会话。');
            return withSessionRemote(scope, remote => remote.skillhub.listInstalled(revision, signal));
          }, detail: async (revision, itemKey, signal) => {
            const scope = ctx.sessions.scope(SessionId(sessionId));
            if (!scope) throw new Error('当前会话已关闭，请重新打开会话。');
            return withSessionRemote(scope, remote => remote.skillhub.getInstalledDetail(revision, itemKey, signal));
          }, checkUpdate: async (revision, itemKey, signal) => {
            const scope = ctx.sessions.scope(SessionId(sessionId));
            if (!scope) throw new Error('当前会话已关闭，请重新打开会话。');
            return withSessionRemote(scope, remote => remote.skillhub.checkUpdate(revision, itemKey, signal));
          } } satisfies InstalledServices : undefined }),
      }, ({ installation, installed }) => <CatalogPage remote={ctx.remote.skillhub} navigation={navigation} client={client}
        installation={installation} installed={installed} availability={availability} onReturn={() => ctx.layout.selectPanel(null)} />));
      ctx.slots.inject('sidebar.panellist', () => ctx.slots.register({ name: 'sidebar.panellist', id: panelId, label: '技能中心', order: 10 }, () => <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><rect x="3" y="3" width="7" height="7" rx="1.6" /><rect x="3" y="14" width="7" height="7" rx="1.6" /><rect x="14" y="14" width="7" height="7" rx="1.6" /><path d="M17.5 2.5v8M13.5 6.5h8" /></svg>));
    });
  });
  await panel;
  return async () => {
    await panel.dispose();
    await unmount();
  };
}

/** The borrowed Agent scope retains identity, but each consumer must declare its Remote dependency. */
async function withSessionRemote<T>(scope: AgentContext, call: (remote: AgentContext['remote']) => Promise<T>): Promise<T> {
  let outcome: { value: T } | { error: unknown } | undefined;
  const consumer = scope.inject(['remote.skillhub'], async injected => {
    try {
      // inject creates a child context and preserves the parent's Agent scope tag.
      outcome = { value: await call(injected.remote as AgentContext['remote']) };
    } catch (error) {
      outcome = { error };
    }
  });
  try {
    await consumer;
    if (!outcome) throw new Error('当前会话服务不可用，请重新打开会话。');
    if ('error' in outcome) throw outcome.error;
    return outcome.value;
  } finally {
    await consumer.dispose();
  }
}
