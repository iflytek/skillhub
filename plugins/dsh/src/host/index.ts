import { Context, symbols } from '@deepseek-ai/cordis';
import { Remote, RemoteError, RemoteScope, TypertRemoteService } from '@deepseek-ai/dsh-typert-protocol';
import type {} from '@deepseek-ai/dsh-agent';
import type {} from '@deepseek-ai/dsh-session-query';
import { historicalSearchCandidate } from './conversation-history.js';
import type {} from '@deepseek-ai/dsh-sandbox-policy';
import { SessionId } from '@deepseek-ai/dsh-session/types';
import { randomUUID } from 'node:crypto';
import { dirname, isAbsolute, join, relative, sep } from 'node:path';
import { Config } from './config.js';
import { ConversationSelections } from './conversation-selections.js';
import type { SelectionSnapshot } from './conversation-selections.js';
import { registerSearchTool } from './search-tool.js';
import { registerInstallTool } from './install-tool.js';
import type { ConversationSearchContext, ConversationSearchResult } from './search-tool.js';
import type { CatalogContext, ModuleAvailability, ReviewPage, ConversationDetail, SkillDetail, SkillList, VersionPage } from './types.js';
import { RegistryAdapter, RegistryError } from './registry-adapter.js';
import { LocalRuntime } from './local-runtime.js';
import { assertRuntimeCompatibility } from './runtime-compatibility.js';
import type { HostCompatibility } from './types.js';
import { InstallationTargets } from './installation-targets.js';
import { UpgradePlans, RemovalPlans } from './upgrade-plans.js';
import type { UpgradePlanView, UninstallPlanView, HostExecutePlanRequest, HostRecoveryRequest } from './types.js';
import type { ResolvedInstallationTarget } from './installation-targets.js';
import { assertInstallationMutation, assertRemovalMutation, resolveInstallationPath, resolveInstalledPath } from './installation-paths.js';
import { observeDshInstallation } from './skill-discovery.js';
import { validateDshSkill } from './skill-format.js';
import { installRequest, operationQuery, executePlanRequest } from './install-request.js';
import { conversationInstallKey, conversationInstallRequest } from './conversation-install-request.js';
import type { PreparedNativeInstall } from './local-runtime.js';
import type { NativeOperation, HostInstallRequest } from './types.js';
import type { NativeRecoveryResult } from './generated/native-contract.js';
import type { InstallationTargetList } from './types.js';
import type { LocalRuntimeStatus } from './local-runtime.js';
import type { InstalledSkillList, InstalledSkillDetail, InstalledUpdateCheck } from './types.js';
import { installationFailureCode } from './installation-failure.js';
import type { InstalledProjectionItem } from './generated/native-contract.js';

declare module '@deepseek-ai/cordis' {
  interface Context { skillhub: SkillHubService }
}

/** Native SkillHub service with session-scoped installation and read-only catalog methods. */
export class SkillHubService extends TypertRemoteService {
  static Config = Config;
  private readonly configuration: ReturnType<typeof Config>;
  private sourceRevision = randomUUID();
  private registry?: RegistryAdapter;
  private sourceInput = '';
  private readonly lifetime = new AbortController();
  private localRuntime?: LocalRuntime;
  private readonly selections = new ConversationSelections();
  private readonly installationTargets = new InstallationTargets();
  private readonly upgradePlans = new UpgradePlans();
  private readonly removalPlans = new RemovalPlans();
  private readonly installedItems = new Map<string, { sessionId: string; cwd: string; sourceRevision: string; item: InstalledProjectionItem }>();
  private readonly capabilities = new Map<string, ModuleAvailability>();
  private readonly details = new Map<string, {
    sourceRevision: string; namespace: string; slug: string; version: string;
    skillId: number; versionId: number; fingerprint: string;
  }>();

  constructor(ctx: Context, configuration: ReturnType<typeof Config> = Config({})) {
    super(ctx, 'skillhub', { namespace: 'skillhub' });
    this.configuration = configuration;
    ctx.inject(['sessions'], ctx => {
      ctx.effect(() => {
        const runtime = new LocalRuntime(undefined, () => this.assertRuntimeBaseline());
        this.localRuntime = runtime;
        void runtime.start();
        return async () => {
          this.installationTargets.clear();
          this.upgradePlans.clear();
          this.removalPlans.clear();
          if (this.localRuntime === runtime) this.localRuntime = undefined;
          await runtime.dispose();
        };
      });
    });
    ctx.inject(['tools'], ctx => {
      ctx.effect(() => registerSearchTool(ctx.tools, (query, context, signal) => this.searchConversation(query, context, signal)));
      ctx.effect(() => registerInstallTool(ctx.tools, (request, context, signal) => this.installConversation(request, context, signal)));
    });
    ctx.effect(() => () => { this.lifetime.abort(); this.details.clear(); this.capabilities.clear(); this.selections.clear(); this.installationTargets.clear(); this.installedItems.clear(); this.upgradePlans.clear(); this.removalPlans.clear(); });
  }

  /** Internal diagnostics, deliberately not a Remote method. */
  getLocalRuntimeStatus(): LocalRuntimeStatus | null {
    return this.localRuntime?.getStatus() ?? null;
  }

  private refreshSource(): void {
    const source = this.configuration.get().registryUrl.trim();
    if (source === this.sourceInput) return;
    let registry: RegistryAdapter | undefined;
    try { registry = source ? new RegistryAdapter(source) : undefined; }
    catch {
      throw new RemoteError('skillhub/registry-error', 'Invalid SkillHub source address.', {
        reason: 'INVALID_SOURCE', retryable: false,
      });
    }
    this.details.clear();
    this.selections.clear();
    this.installationTargets.clear();
    this.installedItems.clear();
    this.upgradePlans.clear();
    this.removalPlans.clear();
    this.capabilities.clear();
    this.registry = registry;
    this.sourceInput = source;
    this.sourceRevision = randomUUID();
  }

  @Remote
  getStatus(): string {
    return 'SkillHub: connected';
  }

  private assertRuntimeBaseline(): void {
    assertRuntimeCompatibility({
      packageOf: (name, parentURL) => this.ctx.get('pluginPackages')?.packageOf(name, parentURL),
      clientPath: name => this.ctx.get('clientModules')?.clientPath(name),
    }, import.meta.url);
  }

  @Remote
  getCompatibility(): HostCompatibility {
    try {
      this.assertRuntimeBaseline();
      return { dependencyBaseline: 'matched', candidateDshVersion: '0.2.0-rc.2', localWritesAllowed: true };
    } catch {
      return { dependencyBaseline: 'unverified', candidateDshVersion: '0.2.0-rc.2', localWritesAllowed: false };
    }
  }

  @Remote
  getContext(): CatalogContext {
    this.refreshSource();
    return {
      sourceRevision: this.sourceRevision,
      registryUrl: this.registry?.source ?? '',
      configured: !!this.registry,
      detailTabs: { ...this.configuration.get().detailTabs },
    };
  }

  @Remote
  async listSkills(sourceRevision: string, query: string, signal: AbortSignal): Promise<SkillList> {
    return this.readSource(sourceRevision, signal, async (registry, signal) => {
      const result = await registry.search(query, signal);
      return {
        items: result.items.map(item => ({ ...item, summary: item.summary ?? '' })),
        total: result.total, returned: result.items.length, limit: result.limit,
      };
    });
  }

  @Remote
  async getSkillDetail(
    sourceRevision: string, namespace: string, slug: string, version: string, signal: AbortSignal,
  ): Promise<SkillDetail> {
    return this.readSource(sourceRevision, signal, async (registry, signal) => {
      const resolved = await registry.resolve(namespace, slug, version, signal);
      const metadata = await registry.metadata(namespace, slug, signal);
      let documentation: SkillDetail['documentation'];
      try {
        documentation = { state: 'ready', content: await registry.documentation(namespace, slug, resolved.version, signal) };
      } catch (error) {
        if (!(error instanceof RegistryError) || error.code === 'CANCELLED') throw error;
        documentation = { state: 'error', reason: error.code, retryable: error.retryable };
      }
      const modes = this.configuration.get().detailTabs;
      const probe = async (key: string, mode: 'auto' | 'show' | 'hide', read: () => Promise<unknown>): Promise<ModuleAvailability> => {
        if (mode === 'hide') return 'hidden';
        if (mode === 'show') return 'unknown';
        const cached = this.capabilities.get(key);
        if (cached) return cached;
        const remember = (value: ModuleAvailability) => {
          this.assertSource(sourceRevision);
          if (this.capabilities.size >= 200) this.capabilities.clear();
          this.capabilities.set(key, value);
          return value;
        };
        try { await read(); return remember('available'); }
        catch (error) {
          if (!(error instanceof RegistryError) || error.code === 'CANCELLED') throw error;
          return error.code === 'ANONYMOUS_DENIED' ? remember('anonymous-denied') : 'unknown';
        }
      };
      const versions = await probe(JSON.stringify(['versions', namespace, slug]), modes.versions, () => registry.versions(namespace, slug, 0, 1, signal));
      const reviews = await probe(JSON.stringify(['reviews', metadata.id]), modes.reviews, () => registry.reviews(metadata.id, 0, 1, signal));
      this.assertSource(sourceRevision);
      const detailToken = randomUUID();
      // Bounded read-only snapshots; evicted references require reopening the detail.
      if (this.details.size >= 100) this.details.delete(this.details.keys().next().value!);
      this.details.set(detailToken, { sourceRevision, namespace, slug, version: resolved.version,
        skillId: metadata.id, versionId: resolved.versionId, fingerprint: resolved.fingerprint });
      return { detailToken, title: metadata.displayName || slug, summary: metadata.summary ?? '',
        namespace, slug, version: resolved.version, versionId: resolved.versionId, documentation,
        tabs: { versions, reviews, securityAudit: modes.securityAudit === 'hide' ? 'hidden' : 'anonymous-denied' } };

    });
  }

  @Remote
  async listVersions(detailToken: string, page: number, signal: AbortSignal): Promise<VersionPage> {
    const detail = this.readDetail(detailToken, 'versions');
    return this.readSource(detail.sourceRevision, signal,
      (registry, signal) => registry.versions(detail.namespace, detail.slug, page, 20, signal));
  }

  @Remote
  async listReviews(detailToken: string, page: number, signal: AbortSignal): Promise<ReviewPage> {
    const detail = this.readDetail(detailToken, 'reviews');
    return this.readSource(detail.sourceRevision, signal,
      (registry, signal) => registry.reviews(detail.skillId, page, 20, signal));
  }

  @RemoteScope('agent')
  async listInstalled(sourceRevision: string, signal: AbortSignal): Promise<InstalledSkillList> {
    this.assertSource(sourceRevision);
    const id = this.ctx.invocation?.request.args.agentId;
    const agent = typeof id === 'string' ? this.ctx.get('agents')?.get(SessionId(id)) : undefined;
    const cwd = agent?.session.header.cwd;
    const fs = agent?.ctx.get('fs');
    const runtime = this.localRuntime;
    if (!agent || !cwd || !fs || !runtime) throw new RemoteError('skillhub/context-stale', 'Session unavailable.', { retryable: false });
    const source = this.getContext();
    if (!source.configured) throw new RemoteError('skillhub/source-not-configured', 'Configure a SkillHub source first.', { retryable: false });
    const combined = AbortSignal.any([signal, this.lifetime.signal]);
    const targets = await Promise.all((['user', 'project'] as const).map(async scope => {
      try { return { scope, target: await resolveInstalledPath({ scope, cwd, fs, signal: combined }) }; }
      catch { combined.throwIfAborted(); return { scope, target: undefined }; }
    }));
    const roots = targets.flatMap(entry => entry.target ? [entry.target] : []);
    const result = await runtime.listInstalled(source.registryUrl, roots);
    combined.throwIfAborted();
    this.assertSource(sourceRevision);
    if (agent.session.header.cwd !== cwd || serviceIdentity(agent.ctx.get('fs')) !== serviceIdentity(fs)
      || this.ctx.get('agents')?.get(SessionId(agent.session.id)) !== agent || this.localRuntime !== runtime) {
      throw new RemoteError('skillhub/context-stale', 'Session changed.', { retryable: false });
    }
    for (const target of roots) {
      const current = await resolveInstalledPath({ scope: target.scope, cwd, fs, signal: combined });
      if (current.root !== target.root) throw new RemoteError('skillhub/context-stale', 'Skill root changed.', { retryable: false });
    }
    combined.throwIfAborted();
    if (agent.session.header.cwd !== cwd || serviceIdentity(agent.ctx.get('fs')) !== serviceIdentity(fs)
      || this.ctx.get('agents')?.get(SessionId(agent.session.id)) !== agent || this.localRuntime !== runtime) {
      throw new RemoteError('skillhub/context-stale', 'Session changed.', { retryable: false });
    }
    this.assertSource(sourceRevision);
    const unavailable = targets.filter(entry => !entry.target).map(entry => entry.scope);
    const items = result.items.map(item => {
      const itemKey = randomUUID();
      if (this.installedItems.size >= 400) this.installedItems.delete(this.installedItems.keys().next().value!);
      this.installedItems.set(itemKey, { sessionId: agent.session.id, cwd, sourceRevision, item: { ...item } });
      return { itemKey, namespace: item.namespace, slug: item.slug, version: item.version, scope: item.scope, state: item.state };
    });
    return { items, complete: result.complete && !unavailable.length, issues: result.issues, unavailable };
  }

  @RemoteScope('agent')
  async getInstalledDetail(sourceRevision: string, itemKey: string, signal: AbortSignal): Promise<InstalledSkillDetail> {
    const { item, valid } = await this.readInstalledItem(sourceRevision, itemKey, signal);
    const combined = AbortSignal.any([signal, this.lifetime.signal]);
    let content: InstalledSkillDetail['content'];
    try {
      content = { state: 'ready', detail: await this.getSkillDetail(sourceRevision, item.namespace, item.slug, item.version, combined) };
    } catch (error) {
      combined.throwIfAborted();
      valid();
      content = { state: 'local-only', reason: installationFailureCode(error) };
    }
    valid();
    combined.throwIfAborted();
    return { item: { itemKey, namespace: item.namespace, slug: item.slug, version: item.version, scope: item.scope, state: item.state }, content };
  }

  @RemoteScope('agent')
  async checkUpdate(sourceRevision: string, itemKey: string, signal: AbortSignal): Promise<InstalledUpdateCheck> {
    const { item, valid } = await this.readInstalledItem(sourceRevision, itemKey, signal);
    const base = { currentVersion: item.version, candidateVersion: null, checkedAt: new Date().toISOString() };
    if (item.state !== 'present') return { ...base, state: 'blocked', reason: 'local-record-unverified' };
    let candidate;
    try {
      candidate = await this.readSource(sourceRevision, signal, (registry, signal) => registry.resolve(item.namespace, item.slug, undefined, signal));
    } catch (error) {
      signal.throwIfAborted(); valid();
      return { ...base, state: 'unavailable', reason: installationFailureCode(error) };
    }
    const after = await this.readInstalledItem(sourceRevision, itemKey, signal);
    if (after.item.state !== item.state || after.item.fingerprint !== item.fingerprint) {
      throw new RemoteError('skillhub/context-stale', 'Installed record changed during update check.', { retryable: false });
    }
    const assessment = await this.localRuntime!.assessUpdate(item, candidate);
    after.valid();
    signal.throwIfAborted();
    this.lifetime.signal.throwIfAborted();
    return { ...assessment, checkedAt: new Date().toISOString() };
  }

  @RemoteScope('agent')
  async prepareUpgrade(sourceRevision: string, itemKey: string, signal: AbortSignal): Promise<UpgradePlanView> {
    const { item, valid, agent, runtime, fs } = await this.readInstalledItem(sourceRevision, itemKey, signal);
    const combined = AbortSignal.any([signal, this.lifetime.signal]);
    const context = this.selectionScope(agent!);
    const base: UpgradePlanView = { hostInstanceId: runtime!.getStatus().hostInstanceId!, status: 'blocked', reason: '', planId: null, expiresAt: null,
      currentVersion: item.version, candidateVersion: null, targets: [] };
    if (runtime!.getStatus().pendingOperations > 0) return { ...base, reason: 'recovery-required' };
    if (item.state !== 'present') return { ...base, reason: 'local-record-unverified' };
    const policy = agent!.ctx.get('sandboxPolicy')?.resolve({ session: agent!.session });
    const roots = [];
    for (const scope of ['user', 'project'] as const) {
      try { roots.push(await resolveInstallationPath({ scope, cwd: context.cwd, fs: fs!, policy, signal: combined })); }
      catch { combined.throwIfAborted(); }
    }
    const baseline = await runtime!.inspectUpgrade({ registry: this.getContext().registryUrl,
      namespace: item.namespace, slug: item.slug, version: item.version }, roots);
    valid(); combined.throwIfAborted();
    if (baseline.status === 'blocked') return { ...base, reason: baseline.reason };
    const candidate = await this.readSource(sourceRevision, combined,
      (registry, signal) => registry.resolve(item.namespace, item.slug, undefined, signal));
    const assessment = await runtime!.assessUpdate(baseline.inventoryItem, candidate);
    valid(); combined.throwIfAborted();
    if (assessment.state !== 'upgradable') return { ...base,
      status: assessment.state === 'current' ? 'unchanged' : 'blocked', reason: assessment.reason, candidateVersion: candidate.version };
    for (const target of baseline.targets) {
      const current = await resolveInstallationPath({ scope: target.scope, cwd: context.cwd, fs: fs!,
        policy: agent!.ctx.get('sandboxPolicy')?.resolve({ session: agent!.session }), signal: combined });
      if (current.root !== target.root) throw new RemoteError('skillhub/context-stale', 'Upgrade target changed.', { retryable: false });
    }
    valid(); combined.throwIfAborted();
    const plan = this.upgradePlans.issue(context, { baseline, candidate });
    return { ...base, ...plan, status: 'ready', reason: 'newer-version', candidateVersion: candidate.version,
      targets: baseline.targets.map(target => ({ scope: target.scope, path: target.path })) };
  }

  @RemoteScope('agent')
  async prepareUninstall(sourceRevision: string, itemKey: string, signal: AbortSignal): Promise<UninstallPlanView> {
    const { item, valid, agent, runtime, fs } = await this.readInstalledItem(sourceRevision, itemKey, signal);
    const combined = AbortSignal.any([signal, this.lifetime.signal]);
    const context = this.selectionScope(agent!);
    const base: UninstallPlanView = { hostInstanceId: runtime!.getStatus().hostInstanceId!, status: 'blocked', reason: '',
      kind: null, planId: null, expiresAt: null, currentVersion: item.version, target: null };
    if (runtime!.getStatus().pendingOperations > 0) return { ...base, reason: 'recovery-required' };
    if (!['present', 'missing'].includes(item.state)) return { ...base, reason: 'local-record-unverified' };
    const policy = agent!.ctx.get('sandboxPolicy')?.resolve({ session: agent!.session });
    const target = await resolveInstallationPath({ scope: item.scope, cwd: context.cwd, fs: fs!, policy, signal: combined });
    const baseline = await runtime!.inspectRemoval({ registry: this.getContext().registryUrl,
      namespace: item.namespace, slug: item.slug, version: item.version }, target);
    valid(); combined.throwIfAborted();
    if (baseline.status === 'blocked') return { ...base, reason: baseline.reason };
    const currentPolicy = agent!.ctx.get('sandboxPolicy')?.resolve({ session: agent!.session });
    const current = await resolveInstallationPath({ scope: item.scope, cwd: context.cwd, fs: fs!, policy: currentPolicy, signal: combined });
    if (current.root !== target.root || current.dshHome !== target.dshHome) {
      throw new RemoteError('skillhub/context-stale', 'Removal target changed.', { retryable: false });
    }
    const paths = [target.root, baseline.target.path];
    if (baseline.kind === 'uninstall') paths.push(join(dirname(target.root), '.skillhub-trash'));
    await assertRemovalMutation(target.root, item.slug, randomUUID(), paths, currentPolicy);
    valid(); combined.throwIfAborted();
    const plan = this.removalPlans.issue(context, baseline);
    return { ...base, ...plan, status: 'ready', kind: baseline.kind, reason: baseline.kind,
      target: { scope: baseline.target.scope, path: baseline.target.path } };
  }

  private async readInstalledItem(sourceRevision: string, itemKey: string, signal: AbortSignal) {
    this.assertSource(sourceRevision);
    const id = this.ctx.invocation?.request.args.agentId;
    const agent = typeof id === 'string' ? this.ctx.get('agents')?.get(SessionId(id)) : undefined;
    const saved = this.installedItems.get(itemKey);
    const runtime = this.localRuntime;
    const fs = agent?.ctx.get('fs');
    const valid = () => {
      this.assertSource(sourceRevision);
      if (!saved || !agent || !runtime || !fs || saved.sessionId !== agent.session.id
        || saved.sourceRevision !== sourceRevision || saved.cwd !== agent.session.header.cwd
        || this.installedItems.get(itemKey) !== saved || this.localRuntime !== runtime
        || this.ctx.get('agents')?.get(SessionId(agent.session.id)) !== agent
        || serviceIdentity(agent.ctx.get('fs')) !== serviceIdentity(fs)) {
        throw new RemoteError('skillhub/context-stale', 'Reopen the installed skill list.', { retryable: false });
      }
    };
    valid();
    const combined = AbortSignal.any([signal, this.lifetime.signal]);
    const target = await resolveInstalledPath({ scope: saved!.item.scope, cwd: saved!.cwd, fs: fs!, signal: combined });
    if (join(target.root, saved!.item.slug) !== saved!.item.targetPath) {
      throw new RemoteError('skillhub/context-stale', 'Installed target changed.', { retryable: false });
    }
    const result = await runtime!.listInstalled(this.getContext().registryUrl, [target]);
    combined.throwIfAborted();
    valid();
    const item = result.items.find(item => item.targetPath === saved!.item.targetPath && item.namespace === saved!.item.namespace
      && item.slug === saved!.item.slug && item.version === saved!.item.version && item.scope === saved!.item.scope);
    if (!item) throw new RemoteError('skillhub/context-stale', 'Installed record changed or unavailable.', { retryable: false });
    return { item, valid, agent, runtime, fs };
  }

  @RemoteScope('agent')
  async executePlan(input: HostExecutePlanRequest, signal: AbortSignal): Promise<NativeOperation | null> {
    const parsed = executePlanRequest.safeParse(this.ctx.invocation?.request.args.input ?? input);
    if (!parsed.success) throw new RemoteError('skillhub/invalid-installation-request', 'Invalid mutation request.', { retryable: false });
    const id = this.ctx.invocation?.request.args.agentId;
    const agent = typeof id === 'string' ? this.ctx.get('agents')?.get(SessionId(id)) : undefined;
    const runtime = this.localRuntime;
    const hostInstanceId = runtime?.getStatus().hostInstanceId;
    if (!agent || !runtime || !hostInstanceId) throw new RemoteError('skillhub/context-stale', 'Mutation context unavailable.', { retryable: false });
    const request = parsed.data;
    try {
      const previous = await runtime.findPlanOperation({ hostInstanceId, ownerId: agent.session.id, requestKey: request.requestKey,
        mode: 'retry', parameters: { planId: request.planId } });
      if (previous) return previous;
      if (this.removalPlans.has(request.planId)) {
        return await runtime.submitRemoval({ hostInstanceId, ownerId: agent.session.id, requestKey: request.requestKey,
          mode: 'new', parameters: { planId: request.planId } }, async () => {
          const combined = AbortSignal.any([signal, this.lifetime.signal]);
          combined.throwIfAborted();
          const context = this.selectionScope(agent);
          const baseline = this.removalPlans.read(request.planId, context);
          const source = this.getContext();
          const filesystem = agent.ctx.get('fs');
          if (!filesystem) throw new RemoteError('skillhub/context-stale', 'Filesystem unavailable.', { retryable: false });
          const policy = agent.ctx.get('sandboxPolicy')?.resolve({ session: agent.session });
          const target = await resolveInstallationPath({ scope: baseline.target.scope, cwd: context.cwd, fs: filesystem, policy, signal: combined });
          if (target.root !== baseline.target.root) throw new RemoteError('skillhub/context-stale', 'Removal root changed.', { retryable: false });
          const valid = () => {
            if (agent.session.header.cwd !== context.cwd || serviceIdentity(agent.ctx.get('fs')) !== serviceIdentity(filesystem)
              || this.ctx.get('agents')?.get(SessionId(agent.session.id)) !== agent || this.localRuntime !== runtime) {
              throw new RemoteError('skillhub/context-stale', 'Removal session changed.', { retryable: false });
            }
          };
          valid(); combined.throwIfAborted(); this.assertSource(context.sourceRevision);
          this.removalPlans.claim(request.planId, context, request.requestKey);
          const item = baseline.inventoryItem;
          const operationSignal = new AbortController().signal;
          return {
            acceptance: { kind: baseline.kind, namespace: item.namespace, slug: item.slug, version: item.version,
              targetLabel: target.scope === 'user' ? '当前用户' : '当前项目', targetPaths: [baseline.target.path],
              context: { sourceIdentity: source.registryUrl, sourceLabel: source.registryUrl, sourceRevision: context.sourceRevision,
                hostLabel: 'DSH Host', initiatedFrom: 'plaza' as const } },
            execution: { baseline, authorize: async (paths: readonly string[], _phase: string, operationId: string) => {
              valid();
              const currentPolicy = agent.ctx.get('sandboxPolicy')?.resolve({ session: agent.session });
              const current = await resolveInstallationPath({ scope: target.scope, cwd: context.cwd, fs: filesystem,
                policy: currentPolicy, signal: operationSignal });
              valid();
              if (current.root !== target.root || current.dshHome !== target.dshHome) throw new Error('Removal root changed');
              await assertRemovalMutation(target.root, item.slug, operationId, paths, currentPolicy);
            } },
          };
        }) ?? null;
      }
      return await runtime.submitUpgrade({ hostInstanceId, ownerId: agent.session.id, requestKey: request.requestKey,
        mode: 'new', parameters: { planId: request.planId } }, async () => {
        const combined = AbortSignal.any([signal, this.lifetime.signal]);
        combined.throwIfAborted();
        const context = this.selectionScope(agent);
        const snapshot = this.upgradePlans.read(request.planId, context);
        const source = this.getContext();
        const filesystem = agent.ctx.get('fs');
        if (!filesystem) throw new RemoteError('skillhub/context-stale', 'Filesystem unavailable.', { retryable: false });
        const policy = agent.ctx.get('sandboxPolicy')?.resolve({ session: agent.session });
        const targets: ResolvedInstallationTarget[] = [];
        for (const target of snapshot.baseline.targets) {
          const current = await resolveInstallationPath({ scope: target.scope, cwd: context.cwd, fs: filesystem, policy, signal: combined });
          if (current.root !== target.root) throw new RemoteError('skillhub/context-stale', 'Upgrade root changed.', { retryable: false });
          targets.push(current);
        }
        combined.throwIfAborted();
        this.assertSource(context.sourceRevision);
        if (agent.session.header.cwd !== context.cwd || serviceIdentity(agent.ctx.get('fs')) !== serviceIdentity(filesystem)
          || this.ctx.get('agents')?.get(SessionId(agent.session.id)) !== agent || this.localRuntime !== runtime) {
          throw new RemoteError('skillhub/context-stale', 'Upgrade session changed.', { retryable: false });
        }
        this.upgradePlans.claim(request.planId, context, request.requestKey);
        const candidate = snapshot.candidate;
        const operationSignal = new AbortController().signal;
        return {
          acceptance: { namespace: candidate.namespace, slug: candidate.slug, version: candidate.version,
            targetLabel: targets.map(target => target.scope === 'user' ? '当前用户' : '当前项目').join('、'),
            targetPaths: snapshot.baseline.targets.map(target => target.path),
            context: { sourceIdentity: source.registryUrl, sourceLabel: source.registryUrl, sourceRevision: context.sourceRevision,
              hostLabel: 'DSH Host', initiatedFrom: 'plaza' as const } },
          execution: { registry: source.registryUrl, namespace: candidate.namespace, slug: candidate.slug,
            version: candidate.version, versionId: candidate.versionId, fingerprint: candidate.fingerprint, baseline: snapshot.baseline,
            authorize: async (paths: readonly string[]) => {
              const fs = agent.ctx.get('fs');
              if (!fs || serviceIdentity(fs) !== serviceIdentity(filesystem) || agent.session.header.cwd !== context.cwd
                || this.ctx.get('agents')?.get(SessionId(agent.session.id)) !== agent) throw new Error('Upgrade session changed');
              const currentPolicy = agent.ctx.get('sandboxPolicy')?.resolve({ session: agent.session });
              const roots = [];
              for (const target of targets) {
                const current = await resolveInstallationPath({ scope: target.scope, cwd: context.cwd, fs, policy: currentPolicy, signal: operationSignal });
                if (current.root !== target.root || current.dshHome !== target.dshHome) throw new Error('Upgrade root changed');
                roots.push(current.root);
              }
              for (const path of paths) {
                // A mutation batch can contain paths from both approved roots.
                let allowed = false;
                for (const root of roots) {
                  try { await assertInstallationMutation(root, [path], currentPolicy); allowed = true; break; }
                  catch { /* Try the other explicitly confirmed root. */ }
                }
                if (!allowed) throw new Error('Upgrade mutation is outside the confirmed roots');
              }
            },
            validateStaged: (directory: string) => validateDshSkill(agent.ctx, directory, operationSignal),
          }
        };
      }) ?? null;
    } catch (error) {
      if (error instanceof RemoteError) throw error;
      if (error && typeof error === 'object' && 'code' in error && error.code === 'REQUEST_KEY_CONFLICT') {
        throw new RemoteError('skillhub/request-conflict', 'Request parameters changed.', { retryable: false });
      }
      throw new RemoteError('skillhub/installation-unavailable', 'Mutation request could not be accepted.', { retryable: true });
    }
  }

  @RemoteScope('agent')
  async openConversationDetail(selectionRef: string, signal: AbortSignal): Promise<ConversationDetail> {
    const id = this.ctx.invocation?.request.args.agentId;
    const agent = typeof id === 'string' ? this.ctx.get('agents')?.get(SessionId(id)) : undefined;
    if (!agent) throw new RemoteError('skillhub/context-stale', 'Conversation unavailable.', { retryable: false });
    const selection = this.conversationSelection(agent, selectionRef);
    const detail = await this.getSkillDetail(selection.sourceRevision, selection.namespace, selection.slug, selection.version, signal);
    this.conversationSelection(agent, selectionRef);
    const snapshot = this.details.get(detail.detailToken);
    if (!snapshot) throw new RemoteError('skillhub/context-stale', 'Detail expired.', { retryable: false });
    const fixedRef = this.selections.stage(selectionRef, this.selectionScope(agent), detail.detailToken, snapshot);
    return { ...detail, selectionRef: fixedRef };
  }

  /** Reissue only from this session's trusted search log; never restore old authorization. */
  @RemoteScope('agent')
  async reopenConversationDetail(callId: string, selectionRef: string, signal: AbortSignal): Promise<ConversationDetail> {
    const stale = () => new RemoteError('skillhub/context-stale', 'Search history unavailable. Search again in this conversation.', { retryable: false });
    const id = this.ctx.invocation?.request.args.agentId;
    const agent = typeof id === 'string' ? this.ctx.get('agents')?.get(SessionId(id)) : undefined;
    const query = this.ctx.get('sessionQuery');
    if (!agent || !query || !callId || callId.length > 1024 || typeof selectionRef !== 'string') throw stale();
    const scope = this.selectionScope(agent);
    const source = this.registry!.source;
    const check = () => {
      signal.throwIfAborted();
      this.lifetime.signal.throwIfAborted();
      const current = this.selectionScope(agent);
      if (this.ctx.get('agents')?.get(SessionId(scope.sessionId)) !== agent
        || serviceIdentity(this.ctx.get('sessionQuery')) !== serviceIdentity(query)
        || current.sessionId !== scope.sessionId || current.cwd !== scope.cwd
        || current.sourceRevision !== scope.sourceRevision) throw stale();
    };
    check();
    let history;
    try { history = await query.readSession(SessionId(scope.sessionId)); }
    catch { throw stale(); }
    check();
    const candidate = historicalSearchCandidate(history, scope, source, callId, selectionRef);
    if (!candidate) throw stale();
    const detail = await this.getSkillDetail(scope.sourceRevision, candidate.namespace, candidate.slug, candidate.version, signal);
    check();
    const snapshot = this.details.get(detail.detailToken);
    if (!snapshot) throw stale();
    const fresh = this.selections.add({ ...scope, callId }, candidate);
    const fixed = this.selections.stage(fresh, scope, detail.detailToken, snapshot);
    return { ...detail, selectionRef: fixed };
  }

  @RemoteScope('agent')
  acknowledgeConversationDetail(selectionRef: string, detailToken: string): boolean {
    const id = this.ctx.invocation?.request.args.agentId;
    const agent = typeof id === 'string' ? this.ctx.get('agents')?.get(SessionId(id)) : undefined;
    if (!agent) throw new RemoteError('skillhub/context-stale', 'Conversation unavailable.', { retryable: false });
    this.conversationSelection(agent, selectionRef);
    if (!this.details.has(detailToken)) throw new RemoteError('skillhub/context-stale', 'Detail expired.', { retryable: false });
    return this.selections.acknowledge(selectionRef, this.selectionScope(agent), detailToken);
  }

  @RemoteScope('agent')
  async listInstallTargets(detailToken: string, signal: AbortSignal): Promise<InstallationTargetList> {
    const id = this.ctx.invocation?.request.args.agentId;
    const agent = typeof id === 'string' ? this.ctx.get('agents')?.get(SessionId(id)) : undefined;
    if (!agent) throw new RemoteError('skillhub/context-stale', 'Conversation unavailable.', { retryable: false });
    return this.installationTargetsFor(agent.session.id, detailToken, signal);
  }

  private async installationTargetsFor(agentId: string, detailToken: string, signal: AbortSignal): Promise<InstallationTargetList> {
    const agent = this.ctx.get('agents')?.get(SessionId(agentId));
    if (!agent) throw new RemoteError('skillhub/context-stale', 'Conversation unavailable.', { retryable: false });
    const context = { ...this.selectionScope(agent), detailToken };
    const detail = this.details.get(detailToken);
    const runtime = this.localRuntime;
    if (!detail || detail.sourceRevision !== context.sourceRevision || !detail.fingerprint) {
      throw new RemoteError('skillhub/context-stale', 'Detail expired.', { retryable: false });
    }
    if (!runtime || runtime.getStatus().state !== 'ready') {
      throw new RemoteError('skillhub/installation-unavailable', 'Local installation service is unavailable.', { retryable: true });
    }
    const fs = agent.ctx.get('fs');
    if (!fs) return { hostInstanceId: runtime.getStatus().hostInstanceId!, targets: [], unavailable: ['user', 'project'] };
    const combined = AbortSignal.any([signal, this.lifetime.signal]);
    const policy = agent.ctx.get('sandboxPolicy')?.resolve({ session: agent.session });
    const resolved = await Promise.all((['user', 'project'] as const).map(async scope => {
      try { return { scope, target: await resolveInstallationPath({ scope, cwd: context.cwd, fs, policy, signal: combined }) }; }
      catch { combined.throwIfAborted(); return { scope, target: undefined }; }
    }));
    combined.throwIfAborted();
    const current = this.selectionScope(agent);
    const currentPolicy = agent.ctx.get('sandboxPolicy')?.resolve({ session: agent.session });
    if (current.cwd !== context.cwd || current.sourceRevision !== context.sourceRevision
      || this.ctx.get('agents')?.get(SessionId(context.sessionId)) !== agent
      || serviceIdentity(agent.ctx.get('fs')) !== serviceIdentity(fs)
      || currentPolicy?.mode !== policy?.mode || currentPolicy?.workspaceRoot !== policy?.workspaceRoot
      || this.localRuntime !== runtime || runtime.getStatus().state !== 'ready' || this.details.get(detailToken) !== detail) {
      throw new RemoteError('skillhub/context-stale', 'Installation context changed.', { retryable: false });
    }
    return {
      hostInstanceId: runtime.getStatus().hostInstanceId!,
      targets: resolved.flatMap(({ target }) => target ? [this.installationTargets.issue(context, target)] : []),
      unavailable: resolved.filter(({ target }) => !target).map(({ scope }) => scope),
    };
  }

  @RemoteScope('agent')
  async startInstall(input: HostInstallRequest, signal: AbortSignal): Promise<NativeOperation | null> {
    const id = this.ctx.invocation?.request.args.agentId;
    const agent = typeof id === 'string' ? this.ctx.get('agents')?.get(SessionId(id)) : undefined;
    if (!agent) throw new RemoteError('skillhub/context-stale', 'Conversation unavailable.', { retryable: false });
    // The generated object codec strips unknown nested fields; validate the original wire object too.
    const parsed = installRequest.safeParse(this.ctx.invocation?.request.args.input ?? input);
    if (!parsed.success) throw new RemoteError('skillhub/invalid-installation-request', 'Invalid installation request.', { retryable: false });
    try { return await this.submitInstallation(agent.session.id, parsed.data, signal) ?? null; }
    catch (error) {
      if (error instanceof RemoteError) throw error;
      const code = error && typeof error === 'object' && 'code' in error ? error.code : undefined;
      if (code === 'REQUEST_KEY_CONFLICT') throw new RemoteError('skillhub/request-conflict', 'Request parameters changed.', { retryable: false });
      if (code === 'HOST_MISMATCH') throw new RemoteError('skillhub/context-stale', 'Host identity changed.', { retryable: false });
      throw new RemoteError('skillhub/installation-unavailable', 'Installation request could not be accepted.', { retryable: true });
    }
  }

  @RemoteScope('agent')
  async getOperation(operationId: string, hostInstanceId: string): Promise<NativeOperation | null> {
    const id = this.ctx.invocation?.request.args.agentId;
    const agent = typeof id === 'string' ? this.ctx.get('agents')?.get(SessionId(id)) : undefined;
    if (!agent) throw new RemoteError('skillhub/context-stale', 'Conversation unavailable.', { retryable: false });
    if (!operationQuery.safeParse({ operationId, hostInstanceId }).success) {
      throw new RemoteError('skillhub/invalid-installation-request', 'Invalid operation query.', { retryable: false });
    }
    const runtime = this.localRuntime;
    if (!runtime || runtime.getStatus().state !== 'ready') throw new RemoteError('skillhub/installation-unavailable', 'Local service unavailable.', { retryable: true });
    if (runtime.getStatus().hostInstanceId !== hostInstanceId) throw new RemoteError('skillhub/context-stale', 'Host identity changed.', { retryable: false });
    try {
      const operation = await runtime.getOperation(operationId, agent.session.id);
      if (!operation) return null;
      if (operation.phase !== 'completed' || !['install', 'upgrade'].includes(operation.kind)) return operation;
      const cwd = agent.session.header.cwd;
      const fs = agent.ctx.get('fs');
      const skills = this.ctx.get('skills');
      if (!cwd || !fs || !skills) return operation;
      const signal = AbortSignal.any([this.lifetime.signal, AbortSignal.timeout(2000)]);
      try {
        const roots = await Promise.all((['user', 'project'] as const).map(async scope => {
          try { return await resolveInstalledPath({ scope, cwd, fs, signal }); }
          catch { return undefined; }
        }));
        const confirmed = await runtime.observeInstallation(operationId, agent.session.id,
          roots.flatMap(root => root ? [root.root] : []),
          paths => observeDshInstallation(agent.ctx, paths, signal,
            name => skills.get(name, { scope: agent, cwd, signal })));
        signal.throwIfAborted();
        if (confirmed && agent.session.header.cwd === cwd && this.localRuntime === runtime
          && this.ctx.get('agents')?.get(SessionId(agent.session.id)) === agent
          && serviceIdentity(agent.ctx.get('fs')) === serviceIdentity(fs)
          && serviceIdentity(this.ctx.get('skills')) === serviceIdentity(skills)) return { ...operation, discovery: 'confirmed' };
      } catch { /* Discovery uncertainty does not replace a successful file result. */ }
      return { ...operation, discovery: 'unconfirmed' };
    }
    catch { throw new RemoteError('skillhub/installation-unavailable', 'Operation could not be read.', { retryable: true }); }
  }

  @RemoteScope('agent')
  async recoverOperation(input: HostRecoveryRequest): Promise<NativeOperation | null> {
    const parsed = operationQuery.safeParse(this.ctx.invocation?.request.args.input ?? input);
    if (!parsed.success) throw new RemoteError('skillhub/invalid-installation-request', 'Invalid recovery request.', { retryable: false });
    const { operationId, hostInstanceId } = parsed.data;
    const operation = await this.getOperation(operationId, hostInstanceId);
    if (!operation) return null;
    if (!['install', 'upgrade', 'uninstall', 'remove-stale-record'].includes(operation.kind)) {
      throw new RemoteError('skillhub/installation-unavailable', 'Recovery is unavailable for this operation kind.', { retryable: false });
    }
    const agentId = this.ctx.invocation!.request.args.agentId as string;
    try {
      await this.recoverInstallationOperation(agentId, operationId);
      return await this.getOperation(operationId, hostInstanceId);
    } catch {
      throw new RemoteError('skillhub/installation-unavailable', 'Recovery could not be confirmed. Query the original operation.', { retryable: true });
    }
  }

  private selectionScope(agent: { session: { id: string; header: { cwd?: string } } }) {
    this.refreshSource();
    if (!agent.session.header.cwd) throw new RemoteError('skillhub/context-stale', 'Project unavailable.', { retryable: false });
    this.assertSource(this.sourceRevision);
    return { sessionId: agent.session.id, cwd: agent.session.header.cwd, sourceRevision: this.sourceRevision };
  }

  /** Trusted coordinator shared by the scoped Remote and the forthcoming installation tool. */
  submitInstallation(agentId: string, input: HostInstallRequest, signal: AbortSignal): Promise<NativeOperation | undefined> {
    const runtime = this.localRuntime;
    const agent = this.ctx.get('agents')?.get(SessionId(agentId));
    if (!agent || !runtime) return Promise.reject(new RemoteError('skillhub/installation-unavailable', 'Installation context unavailable.', { retryable: true }));
    const request = { ...input };
    return runtime.submitInstall({ ownerId: agent.session.id, hostInstanceId: request.hostInstanceId,
      requestKey: request.requestKey, mode: request.mode, parameters: { detailToken: request.detailToken,
        targetToken: request.targetToken, initiatedFrom: request.initiatedFrom } }, () => this.prepareInstallation(agentId, request.detailToken, request.targetToken, request.initiatedFrom, signal));
  }

  private async prepareInstallation(agentId: string, detailToken: string, targetToken: string,
    initiatedFrom: 'plaza' | 'conversation', signal: AbortSignal): Promise<PreparedNativeInstall> {
    const agent = this.ctx.get('agents')?.get(SessionId(agentId));
    if (!agent) throw new RemoteError('skillhub/context-stale', 'Conversation unavailable.', { retryable: false });
    const { target, snapshot } = await this.resolveInstallationTarget(agentId, detailToken, targetToken, signal);
    const source = this.getContext();
    const cwd = agent.session.header.cwd!;
    const filesystem = agent.ctx.get('fs');
    // An accepted operation owns its lifetime; closing the requesting panel must not cancel it.
    const operationSignal = new AbortController().signal;
    return {
      acceptance: { namespace: snapshot.namespace, slug: snapshot.slug, version: snapshot.version,
        targetLabel: target.scope === 'user' ? '当前用户' : '当前项目', targetPaths: [join(target.root, snapshot.slug)],
        context: { sourceIdentity: source.registryUrl, sourceLabel: source.registryUrl, sourceRevision: source.sourceRevision,
          hostLabel: 'DSH Host', initiatedFrom } },
      execution: { registry: source.registryUrl, namespace: snapshot.namespace, slug: snapshot.slug,
        version: snapshot.version, versionId: snapshot.versionId, fingerprint: snapshot.fingerprint, root: target.root, scope: target.scope,
        authorize: async (paths: readonly string[]) => {
          const fs = agent.ctx.get('fs');
          if (!fs || serviceIdentity(fs) !== serviceIdentity(filesystem) || agent.session.header.cwd !== cwd) throw new Error('Installation session changed');
          const policy = agent.ctx.get('sandboxPolicy')?.resolve({ session: agent.session });
          const current = await resolveInstallationPath({ scope: target.scope, cwd, fs, policy, signal: operationSignal });
          if (current.root !== target.root || current.dshHome !== target.dshHome) throw new Error('Installation root changed');
          await assertInstallationMutation(target.root, paths, policy);
        },
        validateStaged: (directory: string) => validateDshSkill(agent.ctx, directory, operationSignal),
      },
    };
  }

  /** Stable tool identity is checked in the journal before resolving ephemeral detail/target references. */
  async installConversation(input: unknown, context: ConversationSearchContext, signal: AbortSignal): Promise<NativeOperation | undefined> {
    const request = conversationInstallRequest.parse(input);
    const runtime = this.localRuntime;
    const agent = this.ctx.get('agents')?.get(SessionId(context.sessionId));
    const hostInstanceId = runtime?.getStatus().hostInstanceId;
    if (!runtime || !hostInstanceId || !agent || agent.session.header.cwd !== context.cwd) {
      throw new RemoteError('skillhub/context-stale', 'Installation context unavailable.', { retryable: false });
    }
    const requestKey = conversationInstallKey({ hostInstanceId, sessionId: context.sessionId, callId: context.callId });
    return runtime.submitInstall({ ownerId: context.sessionId, hostInstanceId, requestKey, mode: 'new',
      parameters: { ...request, cwd: context.cwd, initiatedFrom: 'conversation' } }, async () => {
      signal.throwIfAborted();
      const scope = this.selectionScope(agent);
      let detailToken: string;
      if ('selectionRef' in request) {
        detailToken = this.resolveConversationSelection(agent, request.selectionRef).detailToken;
      } else {
        const resolved = await this.readSource(scope.sourceRevision, signal,
          (registry, signal) => registry.resolve(request.namespace, request.slug, undefined, signal));
        const detail = await this.getSkillDetail(scope.sourceRevision, request.namespace, request.slug, resolved.version, signal);
        detailToken = detail.detailToken;
      }
      if (agent.session.header.cwd !== context.cwd || this.ctx.get('agents')?.get(SessionId(context.sessionId)) !== agent) {
        throw new RemoteError('skillhub/context-stale', 'Conversation changed.', { retryable: false });
      }
      this.assertSource(scope.sourceRevision);
      const available = await this.installationTargetsFor(context.sessionId, detailToken, signal);
      const target = available.targets.find(target => target.scope === request.scope);
      if (!target) throw new RemoteError('skillhub/target-unavailable', 'Requested installation scope unavailable.', { retryable: false });
      return this.prepareInstallation(context.sessionId, detailToken, target.targetToken, 'conversation', signal);
    });
  }

  /** Internal owner-scoped query shared with the pending UI adapter. */
  getInstallationOperation(agentId: string, operationId: string): Promise<NativeOperation | undefined> {
    const runtime = this.localRuntime;
    if (!runtime) return Promise.reject(new Error('Local installation service is unavailable'));
    return runtime.getOperation(operationId, agentId);
  }

  /** Internal recovery for an existing session; historical sources are never queried or reinstalled. */
  recoverInstallationOperation(agentId: string, operationId: string): Promise<NativeRecoveryResult | undefined> {
    const runtime = this.localRuntime;
    const agent = this.ctx.get('agents')?.get(SessionId(agentId));
    const cwd = agent?.session.header.cwd;
    const filesystem = agent?.ctx.get('fs');
    if (!runtime || !agent || !cwd || !filesystem) return Promise.reject(new Error('Recovery context unavailable'));
    const signal = new AbortController().signal;
    return runtime.recoverOperation(operationId, agent.session.id, async operation => {
      if (!['install', 'upgrade', 'uninstall', 'remove-stale-record'].includes(operation.kind)
        || (operation.kind === 'upgrade' ? operation.targetPaths.length < 1 || operation.targetPaths.length > 2 : operation.targetPaths.length !== 1)
        || new Set(operation.targetPaths).size !== operation.targetPaths.length
        || !/^[a-zA-Z0-9][a-zA-Z0-9._-]*$/.test(operation.slug)) throw new Error('Unsupported recovery target set');
      const resolve = async (scope: 'user' | 'project'): Promise<ResolvedInstallationTarget> => {
        const fs = agent.ctx.get('fs');
        if (!fs || serviceIdentity(fs) !== serviceIdentity(filesystem) || agent.session.header.cwd !== cwd
          || this.ctx.get('agents')?.get(SessionId(agentId)) !== agent) throw new Error('Recovery session changed');
        const policy = agent.ctx.get('sandboxPolicy')?.resolve({ session: agent.session });
        const target = await resolveInstallationPath({ scope, cwd, fs, policy, signal });
        if (serviceIdentity(agent.ctx.get('fs')) !== serviceIdentity(fs) || agent.session.header.cwd !== cwd
          || this.ctx.get('agents')?.get(SessionId(agentId)) !== agent) throw new Error('Recovery session changed');
        return target;
      };
      const targets: ResolvedInstallationTarget[] = [];
      for (const scope of ['user', 'project'] as const) {
        try {
          const candidate = await resolve(scope);
          if (operation.targetPaths.includes(join(candidate.root, operation.slug))
            && !targets.some(target => target.root === candidate.root)) targets.push(candidate);
        } catch { /* A denied/unavailable root cannot authorize historical mutations. */ }
      }
      if (targets.length !== operation.targetPaths.length) throw new Error('Historical installation target is unavailable in this session');
      return async paths => {
        // Revalidate the complete accepted set even when a recovery step mutates only one target.
        for (const accepted of targets) {
          const current = await resolve(accepted.scope);
          if (current.root !== accepted.root || current.dshHome !== accepted.dshHome) throw new Error('Recovery root changed');
        }
        const policy = agent.ctx.get('sandboxPolicy')?.resolve({ session: agent.session });
        if (filesystem.sandboxMode !== undefined && !policy) throw new Error('Recovery policy unavailable');
        if (operation.kind === 'install' || operation.kind === 'upgrade') {
          for (const path of paths) {
            const accepted = targets.find(target => {
              const offset = relative(target.root, path);
              return !isAbsolute(offset) && offset !== '..' && !offset.startsWith(`..${sep}`);
            });
            if (!accepted) throw new Error('Recovery mutation escaped the accepted roots');
            await assertInstallationMutation(accepted.root, [path], policy);
          }
        } else await assertRemovalMutation(targets[0]!.root, operation.slug, operation.operationId, paths, policy);
      };
    });
  }

  /** Internal only: page and tool execution share this final target-reference check. */
  async resolveInstallationTarget(agentId: string, detailToken: string, targetToken: string, signal: AbortSignal): Promise<{
    target: ResolvedInstallationTarget; snapshot: SelectionSnapshot;
  }> {
    const agent = this.ctx.get('agents')?.get(SessionId(agentId));
    if (!agent) throw new RemoteError('skillhub/context-stale', 'Conversation unavailable.', { retryable: false });
    const context = { ...this.selectionScope(agent), detailToken };
    const detail = this.details.get(detailToken);
    const runtime = this.localRuntime;
    if (!detail || detail.sourceRevision !== context.sourceRevision || !detail.fingerprint
      || this.ctx.get('agents')?.get(SessionId(context.sessionId)) !== agent) {
      throw new RemoteError('skillhub/context-stale', 'Installation context changed.', { retryable: false });
    }
    if (!runtime || runtime.getStatus().state !== 'ready') {
      throw new RemoteError('skillhub/installation-unavailable', 'Local installation service is unavailable.', { retryable: true });
    }
    const fs = agent.ctx.get('fs');
    if (!fs) throw new RemoteError('skillhub/installation-unavailable', 'Filesystem unavailable.', { retryable: true });
    const combined = AbortSignal.any([signal, this.lifetime.signal]);
    const policy = agent.ctx.get('sandboxPolicy')?.resolve({ session: agent.session });
    const target = await this.installationTargets.resolve(targetToken, context, scope =>
      resolveInstallationPath({ scope, cwd: context.cwd, fs, policy, signal: combined }));
    combined.throwIfAborted();
    const current = this.selectionScope(agent);
    const currentPolicy = agent.ctx.get('sandboxPolicy')?.resolve({ session: agent.session });
    if (current.cwd !== context.cwd || current.sourceRevision !== context.sourceRevision
      || this.ctx.get('agents')?.get(SessionId(context.sessionId)) !== agent
      || serviceIdentity(agent.ctx.get('fs')) !== serviceIdentity(fs)
      || currentPolicy?.mode !== policy?.mode || currentPolicy?.workspaceRoot !== policy?.workspaceRoot
      || this.localRuntime !== runtime || runtime.getStatus().state !== 'ready' || this.details.get(detailToken) !== detail) {
      throw new RemoteError('skillhub/context-stale', 'Installation context changed.', { retryable: false });
    }
    return { target, snapshot: { ...detail } };
  }

  private conversationSelection(agent: { session: { id: string; header: { cwd?: string } } }, reference: string) {
    return this.selections.read(reference, this.selectionScope(agent));
  }

  resolveConversationSelection(agent: { session: { id: string; header: { cwd?: string } } }, reference: string): {
    selectionRef: string; detailToken: string; snapshot: SelectionSnapshot;
  } {
    const resolved = this.selections.resolve(reference, this.selectionScope(agent));
    if (!this.details.has(resolved.detailToken)) throw new RemoteError('skillhub/context-stale', 'Detail expired.', { retryable: false });
    return resolved;
  }

  async searchConversation(query: string, context: ConversationSearchContext, signal: AbortSignal): Promise<ConversationSearchResult> {
    const source = this.getContext();
    const result = await this.listSkills(source.sourceRevision, query, signal);
    this.assertSource(source.sourceRevision);
    const items = result.items.map(item => {
      const selectionRef = this.selections.add({ ...context, sourceRevision: source.sourceRevision },
        { namespace: item.namespace, slug: item.slug, version: item.latestVersion });
      return { ...item, selectionRef };
    });
    return { ...result, items, schemaVersion: 1, kind: 'skillhub-search', query,
      sourceRevision: source.sourceRevision, sourceLabel: source.registryUrl, createdAt: new Date().toISOString() };
  }

  private readDetail(token: string, module: 'versions' | 'reviews') {
    this.refreshSource();
    const detail = this.details.get(token);
    if (!detail || this.configuration.get().detailTabs[module] === 'hide') {
      throw new RemoteError('skillhub/context-stale', 'Reopen the skill detail.', { retryable: false });
    }
    this.assertSource(detail.sourceRevision);
    return detail;
  }

  private assertSource(sourceRevision: string): void {
    this.refreshSource();
    if (sourceRevision !== this.sourceRevision || this.lifetime.signal.aborted) {
      throw new RemoteError('skillhub/context-stale', 'SkillHub source context changed.', { retryable: false });
    }
  }

  private async readSource<T>(
    sourceRevision: string, signal: AbortSignal,
    read: (registry: RegistryAdapter, signal: AbortSignal) => Promise<T>,
  ): Promise<T> {
    this.assertSource(sourceRevision);
    if (!this.registry) {
      throw new RemoteError('skillhub/source-not-configured', 'Configure a SkillHub source first.', { retryable: false });
    }
    try {
      const result = await read(this.registry, AbortSignal.any([signal, this.lifetime.signal]));
      this.assertSource(sourceRevision);
      return result;
    } catch (error) {
      this.assertSource(sourceRevision);
      if (error instanceof RegistryError) {
        throw new RemoteError('skillhub/registry-error', 'SkillHub request failed.', {
          reason: error.code, retryable: error.retryable,
          ...(error.status === undefined ? {} : { status: error.status }),
          ...(error.retryAfterSeconds === undefined ? {} : { retryAfterSeconds: error.retryAfterSeconds }),
        });
      }
      throw error;
    }
  }
}

// Cordis wraps each service lookup in a fresh caller-context proxy.
function serviceIdentity(value: object | undefined): unknown {
  return value ? Reflect.get(value, symbols.original) ?? value : undefined;
}

export default SkillHubService;
