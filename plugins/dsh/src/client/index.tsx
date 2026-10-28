import { ManagementToolView, type ManagementViewServices } from './management-tool-view.js';
import { SkillDraftRetentions } from './use-skill-drafts.js';
import { newSkillConversation } from './use-skill-navigation.js';
import type {} from '@deepseek-ai/dsh-client-locale/client';
import { captureSkillUse } from './use-skill-context.js';
import { SkillUseBinding, SkillUseController, SkillUseFocus, SkillCaseChoices } from './use-skill.js';
import type {} from '@deepseek-ai/dsh-client-ui-conversation/client';
import { resolveReceiptContext, receiptSessionGuard, type ReceiptReads } from './receipt-context.js';
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
import { installedNavigationFor } from './installed-navigation.js';
import { CatalogPage, createCatalogNavigation } from './catalog-page.js';
import { UpgradeRequests, RemovalRequests } from './upgrade-requests.js';
import { InstallationRequests } from './installation-requests.js';
import type { InstalledServices } from './installed-skills.js';
import type { UpgradeServices } from './upgrade-control.js';
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
    const drafts = new SkillDraftRetentions(ctx);
    ctx.effect(() => () => drafts.dispose());
    const useSkills = new SkillUseController({
      capture: (sessionId, actions) => captureSkillUse(ctx, sessionId, actions),
      revision: async () => {
        const response = await ctx.remote.skillhub.getContext();
        if (!response.ok || !response.value.configured) throw new Error('来源不可用，请检查插件设置。');
        return response.value.sourceRevision;
      },
      prepare: async (sessionId, revision, itemKey, signal) => {
        const scope = ctx.sessions.scope(SessionId(sessionId));
        if (!scope) throw new Error('当前会话已关闭。');
        return withSessionRemote(scope, remote => remote.skillhub.prepareSkillUse(revision, itemKey, signal));
      },
      newConversation: (sessionId, revision, key, signal) => newSkillConversation(ctx, sessionId, revision, key, signal, async (target, source, abort) => {
        const scope = ctx.sessions.scope(SessionId(target));
        if (!scope) throw new Error('新对话尚未就绪，请重试。');
        const response = await withSessionRemote(scope, remote => remote.skillhub.listInstalled(source, abort));
        if (!response.ok) throw new Error('来源或安装记录已变化，请重试。');
        return response.value;
      }, drafts),
      current: sessionId => (ctx.sessions.list.getSnapshot().byId[SessionId(sessionId)]?.retainedBy.mainView ?? 0) > 0,
      subscribeContext: listener => ctx.sessions.list.subscribe(listener),
      locale: () => ctx.get('locale')?.getLocale().active,
      subscribeLocale: listener => ctx.on('locale/change', listener),
      reveal: () => ctx.get('layout')?.selectPanel(null),
    });
    ctx.effect(() => () => useSkills.dispose());
    ctx.slots.inject('conversation.input.left', () => ctx.slots.register({ name: 'conversation.input.left', id: 'skillhub-use-focus' },
      props => <SkillUseBinding controller={useSkills} sessionId={props.sessionId} actions={props.inputActions}><SkillUseFocus controller={useSkills} sessionId={props.sessionId} /></SkillUseBinding>));
    ctx.slots.inject('conversation.input.dock', () => ctx.slots.register({ name: 'conversation.input.dock', id: 'skillhub-case-choices' },
      props => <SkillUseBinding controller={useSkills} sessionId={props.sessionId} actions={props.inputActions}><SkillCaseChoices controller={useSkills} sessionId={props.sessionId} /></SkillUseBinding>));
    function installationFor(sessionId: string, catalog = false): InstallationServices {
      let management: ManagementViewServices | undefined;
      const call = async <T,>(action: (remote: AgentContext['remote']) => Promise<T>): Promise<T> => {
        const scope = ctx.sessions.scope(SessionId(sessionId));
        if (!scope) throw new Error('当前会话已关闭，请重新打开会话。');
        return withSessionRemote(scope, action);
      };
      const upgrades: UpgradeServices = { sessionId, upgradeRequests, compatibility: () => ctx.remote.skillhub.getCompatibility(),
        prepareUpgrade: (revision, itemKey, signal) => call(remote => remote.skillhub.prepareUpgrade(revision, itemKey, signal)),
        prepareVersionUpgrade: (revision, itemKey, version, signal) => call(remote => remote.skillhub.prepareVersionUpgrade(revision, itemKey, version, signal)),
        executePlan: (input, signal) => call(remote => remote.skillhub.executePlan(input, signal)),
        operation: (id, host) => call(remote => remote.skillhub.getOperation(id, host)),
        recoverOperation: input => call(remote => remote.skillhub.recoverOperation(input)),
      };
      return { sessionId, requests, upgrades, get management() { return management ??= managementFor(sessionId); }, skillUse: catalog ? useSkills.catalogServices(sessionId) : useSkills.services(sessionId), registryIdentity: async revision => { const source = await ctx.remote.skillhub.getContext(); if (!source.ok || !source.value.configured || source.value.sourceRevision !== revision) throw new Error('来源已变化，请重新读取详情。'); return source.value.registryUrl; },
        checkVersionUpdate: (revision, itemKey, version, signal) => call(remote => remote.skillhub.checkVersionUpdate(revision, itemKey, version, signal)),
        manageInstalled: (sourceRevision, item) => {
        const layout = ctx.get('layout');
        if (!layout || !ctx.sessions.scope(SessionId(sessionId))) throw new Error('当前会话不可用。');
        const installed = installedNavigationFor(navigation, sessionId, sourceRevision);
        Object.assign(installed, { scope: item.scope, keyword: '', selected: item, intent: 'detail', completed: null, removalCompleted: null, triggerKey: item.itemKey });
        navigation.selected = null; navigation.tab = 'installed'; navigation.scrollTop = 0;
        navigation.installedOrigin = { sessionId, sourceRevision };
        layout.selectPanel(panelId);
      }, compatibility: () => ctx.remote.skillhub.getCompatibility(),
        targets: (token, signal) => call(remote => remote.skillhub.listInstallTargets(token, signal)),
        installed: (revision, signal) => call(remote => remote.skillhub.listInstalled(revision, signal)),
        start: (input, signal) => call(remote => remote.skillhub.startInstall(input, signal)),
        operation: (id, host) => call(remote => remote.skillhub.getOperation(id, host)),
      };
    }
    const receiptGenerations = new WeakMap<AgentContext, { cwd: string | undefined; generation: string }>();
    function receiptReadsFor(sessionId: string): ReceiptReads {
      const currentSession = () => ({ scope: ctx.sessions.scope(SessionId(sessionId)),
        cwd: ctx.sessions.list.getSnapshot().byId[SessionId(sessionId)]?.cwd });
      const { scope, cwd } = currentSession();
      const assertCurrent = receiptSessionGuard(currentSession);
      const previous = scope ? receiptGenerations.get(scope) : undefined;
      const sessionGeneration = previous && previous.cwd === cwd ? previous.generation : crypto.randomUUID();
      if (scope) receiptGenerations.set(scope, { cwd, generation: sessionGeneration });
      return { sessionGeneration, assertCurrent, context: () => ctx.remote.skillhub.getContext(),
        operation: (id, host) => { assertCurrent(); return withSessionRemote(scope!, remote => remote.skillhub.getOperation(id, host)); },
        installed: (revision, signal) => { assertCurrent(); return withSessionRemote(scope!, remote => remote.skillhub.listInstalled(revision, signal)); },
      };
    }
    function managementFor(sessionId: string): ManagementViewServices {
      const receipt = receiptReadsFor(sessionId);
      const call = async <T,>(action: (remote: AgentContext['remote']) => Promise<T>): Promise<T> => {
        receipt.assertCurrent();
        if (!(ctx.sessions.list.getSnapshot().byId[SessionId(sessionId)]?.retainedBy.mainView ?? 0)) throw new Error('请回到原会话后重试。');
        const scope = ctx.sessions.scope(SessionId(sessionId));
        if (!scope) throw new Error('当前会话已关闭。');
        return withSessionRemote(scope, action);
      };
      const services: ManagementViewServices = { selectSkillUse: (revision, namespace, slug, signal) => call(remote => remote.skillhub.selectSkillUse(revision, namespace, slug, signal)), client, installation: installationFor(sessionId), assertCurrent: receipt.assertCurrent,
        refreshInstalled: signal => call(async remote => {
          const source = await ctx.remote.skillhub.getContext();
          if (!source.ok || !source.value.configured) throw new Error('当前技能来源不可用。');
          receipt.assertCurrent();
          const installed = await remote.skillhub.listInstalled(source.value.sourceRevision, signal);
          if (!installed.ok) throw new Error('无法读取当前安装记录。');
          receipt.assertCurrent();
          return { sourceRevision: source.value.sourceRevision, list: installed.value };
        }),
        availability: (revision, itemKey, signal) => call(remote => remote.skillhub.checkAvailability(revision, itemKey, signal)),
        check: (revision, itemKey, signal) => call(remote => remote.skillhub.checkUpdate(revision, itemKey, signal)),
        begin: (revision, itemKey, action, version, requestKey, signal) => call(remote => remote.skillhub.beginConversationManagement(revision, itemKey, action, version, requestKey, signal)),
        confirm: (reference, confirmed, signal) => call(remote => remote.skillhub.confirmConversationManagement(reference, confirmed, signal)),
      };
      return services;
    }
    for (const name of ['astron_skillhub_check_availability', 'astron_skillhub_list_installed', 'astron_skillhub_check_update', 'astron_skillhub_upgrade', 'astron_skillhub_uninstall', 'astron_skillhub_enable', 'astron_skillhub_disable', 'astron_skillhub_operation']) {
      ctx.slots.inject('tool.call.toolview', () => ctx.slots.register({ name: 'tool.call.toolview', key: name,
        inject: sessionId => {
          return { services: managementFor(sessionId) };
        },
      }, props => <SkillUseBinding controller={useSkills} sessionId={props.sessionId} actions={props.inputActions}><ManagementToolView {...props} /></SkillUseBinding>));
    }
    ctx.slots.inject('tool.call.toolview', () => ctx.slots.register({
      name: 'tool.call.toolview', key: 'astron_skillhub_install',
      inject: sessionId => {
        const receipt = receiptReadsFor(sessionId);
        return { services: { client, installation: installationFor(sessionId), receipt,
          confirmation: { client, remote: ctx.remote.skillhub, installation: installationFor(sessionId),
            openCatalog: async () => { throw new Error('请从技能中心重新搜索。'); },
            openDetail: async (reference: string, signal: AbortSignal) => {
              receipt.assertCurrent();
              const scope = ctx.sessions.scope(SessionId(sessionId));
              if (!scope) throw new Error('当前会话已关闭，请重新搜索技能。');
              return withSessionRemote(scope, remote => remote.skillhub.openConversationDetail(reference, signal));
            },
            // Pending install history is not trusted search history and must never be reissued.
            reopenDetail: async () => { throw new Error('此安装选择已过期，请在当前会话重新搜索技能。'); },
            acknowledgeDetail: async (reference: string, token: string) => {
              receipt.assertCurrent();
              const scope = ctx.sessions.scope(SessionId(sessionId));
              if (!scope) throw new Error('当前会话已关闭，请重新搜索技能。');
              return withSessionRemote(scope, remote => remote.skillhub.acknowledgeConversationDetail(reference, token));
            },
          }, openInstalled: async (result: InstallResult) => {
        const layout = ctx.get('layout');
        if (!layout) throw new CatalogUnavailableError();
        const reads = receipt;
        const active = () => (ctx.sessions.list.getSnapshot().byId[SessionId(sessionId)]?.retainedBy.mainView ?? 0) > 0;
        if (!active()) throw new Error('当前会话已变化。');
        const current = await resolveReceiptContext(reads, result, new AbortController().signal);
        reads.assertCurrent();
        if (ctx.get('layout') !== layout) throw new CatalogUnavailableError();
        if (!active()) throw new Error('当前会话已变化。');
        navigation.tab = 'installed'; navigation.selected = null; navigation.scrollTop = 0;
        navigation.installedOrigin = { sessionId, sourceRevision: current.sourceRevision };
        client.setQueryData(['skillhub-context'], current);
        layout.selectPanel(panelId);
      } } };
      },
    }, props => <SkillUseBinding controller={useSkills} sessionId={props.sessionId} actions={props.inputActions}><UiAvailabilityNotice availability={availability} /><InstallToolView {...props} /></SkillUseBinding>));
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
    }, props => <SkillUseBinding controller={useSkills} sessionId={props.sessionId} actions={props.inputActions}><UiAvailabilityNotice availability={availability} /><SearchToolView {...props} /></SkillUseBinding>));
    ctx.inject(['layout'], (ctx) => {
      ctx.slots.inject('main', () => availability.enable('main'));
      ctx.slots.inject('sidebar.panellist', () => availability.enable('sidebar'));
      ctx.slots.inject('main', () => ctx.slots.register({ name: 'main', key: panelId,
        children: { 'skillhub.catalog': { kind: 'single', scope: 'session-maybe' } },
      }, ({ renderSlot }) => renderSlot('skillhub.catalog', {})));
      ctx.slots.inject('skillhub.catalog', () => ctx.slots.register({ name: 'skillhub.catalog',
        inject: sessionId => ({ installation: sessionId ? installationFor(sessionId, true) : undefined,
          installed: sessionId ? { sessionId, management: managementFor(sessionId), skillUse: useSkills.catalogServices(sessionId), upgradeRequests, removalRequests, compatibility: () => ctx.remote.skillhub.getCompatibility(),
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
            prepareVersionUpgrade: installationFor(sessionId).upgrades!.prepareVersionUpgrade,
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
      }, ({ installation, installed, inputActions, sessionId }) => <SkillUseBinding controller={useSkills} sessionId={sessionId} actions={inputActions}><CatalogPage remote={ctx.remote.skillhub} navigation={navigation} client={client}
        installation={installation} installed={installed} availability={availability} onReturn={() => ctx.layout.selectPanel(null)} /></SkillUseBinding>));
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
