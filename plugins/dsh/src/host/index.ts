import { Context, symbols } from '@deepseek-ai/cordis';
import { Remote, RemoteError, RemoteScope, TypertRemoteService } from '@deepseek-ai/dsh-typert-protocol';
import type {} from '@deepseek-ai/dsh-agent';
import type {} from '@deepseek-ai/dsh-user-questions';
import type {} from '@deepseek-ai/dsh-session-query';
import { historicalSearchCandidate } from './conversation-history.js';
import type {} from '@deepseek-ai/dsh-sandbox-policy';
import { SessionId } from '@deepseek-ai/dsh-session/types';
import { createHmac, randomBytes, randomUUID } from 'node:crypto';
import { dirname, isAbsolute, join, relative, sep } from 'node:path';
import { isDeepStrictEqual } from 'node:util';
import { Config } from './config.js';
import { ConversationSelections } from './conversation-selections.js';
import type { SelectionSnapshot } from './conversation-selections.js';
import { registerManagementTools, managementRequests } from './management-tool.js';
import { registerManagementContext } from './management-context.js';
import type { ManagementAction, ManagementRequest } from './management-tool.js';
import type { ConversationManagementResult } from './types.js';
import { registerSearchTool } from './search-tool.js';
import { summarizeInstallation } from './installation-summary.js';
import { registerInstallTool } from './install-tool.js';
import type { ConversationSearchContext, ConversationSearchResult } from './search-tool.js';
import type { RegistryConnectionTest, CatalogContext, CatalogLabels, CatalogPageResult, CatalogSort, ModuleAvailability, ReviewPage, ConversationDetail, SkillDetail, SkillList, VersionPage } from './types.js';
import { RegistryAdapter, RegistryError } from './registry-adapter.js';
import { LocalRuntime } from './local-runtime.js';
import { assertRuntimeCompatibility } from './runtime-compatibility.js';
import type { HostCompatibility } from './types.js';
import { InstallationTargets } from './installation-targets.js';
import { UpgradePlans, RemovalPlans, TogglePlans } from './upgrade-plans.js';
import type { UpgradePlanView, UninstallPlanView, TogglePlanView, HostExecutePlanRequest, HostRecoveryRequest } from './types.js';
import type { ResolvedInstallationTarget } from './installation-targets.js';
import { assertInstallationMutation, assertRemovalMutation, resolveInstallationPath, resolveInstalledPath } from './installation-paths.js';
import { inspectDshSkill, observeDshInstallation, sameDshSkill } from './skill-discovery.js';
import { inspectSkillAvailability, inspectDisabledSkillAvailability } from './skill-availability.js';
import { selectUsableInstallation } from './skill-use-selection.js';
import type { SkillAvailability } from './types.js';
import { validateDshSkill } from './skill-format.js';
import { installRequest, operationQuery, executePlanRequest } from './install-request.js';
import { conversationInstallKey, conversationInstallRequest } from './conversation-install-request.js';
import type { PendingConversationInstall, CancelledConversationInstall } from './conversation-install-request.js';
import type { PreparedNativeInstall } from './local-runtime.js';
import type { NativeOperation, HostInstallRequest } from './types.js';
import type { NativeRecoveryResult } from './generated/native-contract.js';
import type { InstallationTargetList } from './types.js';
import type { LocalRuntimeStatus } from './local-runtime.js';
import type { InstalledSkillList, InstalledSkillDetail, InstalledUpdateCheck, SkillUsePreparation, SkillUseSelection } from './types.js';
import { readInstalledPresentations } from './installed-presentation.js';
import { installationFailureCode } from './installation-failure.js';
import type { InstalledProjectionItem } from './generated/native-contract.js';

declare module '@deepseek-ai/cordis' {
  interface Context { skillhub: SkillHubService }
}

/** Native SkillHub service with session-scoped installation and read-only catalog methods. */
export class SkillHubService extends TypertRemoteService {
  static Config: typeof Config = Config;
  private readonly configuration: ReturnType<typeof Config>;
  private sourceRevision = randomUUID();
  private registry?: RegistryAdapter;
  private sourceInput = '';
  private readonly lifetime = new AbortController();
  private localRuntime?: LocalRuntime;
  private readonly selections = new ConversationSelections();
  private readonly pendingConversationInstalls = new Map<string, { parameters: string; agent: unknown; runtime: LocalRuntime; result: Promise<PendingConversationInstall>; confirmation?: Promise<NativeOperation | PendingConversationInstall | CancelledConversationInstall | undefined> }>();
  private readonly pendingConversationChoices = new Map<string, { agent: unknown; runtime: LocalRuntime }>();
  private readonly managementRequests = new Map<string, { parameters: string; result: Promise<object> }>();
  private readonly managementConfirmations = new Map<string, { context: ConversationSearchContext; agent: unknown; runtime: LocalRuntime; sourceRevision: string; planId: string; requestKey: string; view: ConversationManagementResult; attempted?: boolean; execution?: Promise<ConversationManagementResult> }>();
  private readonly installationTargets = new InstallationTargets();
  private readonly upgradePlans = new UpgradePlans();
  private readonly removalPlans = new RemovalPlans();
  private readonly togglePlans = new TogglePlans();
  private readonly installedUpdateScans = new Map<string, {
    sourceRevision: string; agentId: string; valid(): void; close(): void;
    check(key: string, signal: AbortSignal): Promise<InstalledUpdateCheck>;
  }>();
  private readonly installedReferenceKey = randomBytes(32);
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
          this.removalPlans.clear(); this.togglePlans.clear();
          if (this.localRuntime === runtime) this.localRuntime = undefined;
          await runtime.dispose();
        };
      });
    });
    ctx.inject(['systemPrompt'], ctx => {
      ctx.effect(() => registerManagementContext(ctx, async (sessionId, cwd, signal) => {
        const source = this.getContext();
        if (!source.configured) return null;
        return this.listInstalledForAgent(sessionId, source.sourceRevision, signal, cwd);
      }));
    });
    ctx.inject(['tools'], ctx => {
      ctx.effect(() => registerManagementTools(ctx.tools, (action, request, context, signal) => this.manageConversation(action, request, context, signal)));
      ctx.effect(() => registerSearchTool(ctx.tools, (query, context, signal) => this.searchConversation(query, context, signal)));
      ctx.effect(() => registerInstallTool(ctx.tools, (request, context, signal) => this.installConversationWithChoice(request, context, signal)));
    });
    ctx.effect(() => () => { this.lifetime.abort(); for (const scan of this.installedUpdateScans.values()) scan.close(); this.pendingConversationInstalls.clear(); this.pendingConversationChoices.clear(); this.managementRequests.clear(); this.managementConfirmations.clear(); this.details.clear(); this.capabilities.clear(); this.selections.clear(); this.installationTargets.clear(); this.upgradePlans.clear(); this.removalPlans.clear(); this.togglePlans.clear(); });
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

    this.upgradePlans.clear();
    this.removalPlans.clear(); this.togglePlans.clear();
    this.capabilities.clear();
    this.registry = registry;
    this.sourceInput = source;
    this.sourceRevision = randomUUID();
  }

  @Remote
  getStatus(): string {
    return 'SkillHub: connected';
  }

  /** Probe an unsaved address independently of the active source and its selection state. */
  @Remote
  async testRegistryConnection(inputUrl: string, signal: AbortSignal): Promise<RegistryConnectionTest> {
    const started = performance.now();
    const deadline = AbortSignal.timeout(10_000);
    const combined = AbortSignal.any([signal, this.lifetime.signal, deadline]);
    try {
      const registry = new RegistryAdapter(inputUrl.trim());
      const result = await registry.catalog('', 0, 'newest', combined);
      combined.throwIfAborted();
      return { reachable: true, mode: result.mode, elapsedMs: Math.round(performance.now() - started) };
    } catch (error) {
      const cancelled = signal.aborted || this.lifetime.signal.aborted;
      const reason = cancelled ? 'CANCELLED' : deadline.aborted ? 'NETWORK_ERROR'
        : error instanceof RegistryError ? error.code : 'NETWORK_ERROR';
      throw new RemoteError('skillhub/registry-error', 'SkillHub connection test failed.', {
        reason, retryable: !cancelled && (deadline.aborted || !(error instanceof RegistryError) || error.retryable),
      });
    }
  }

  private assertRuntimeBaseline() {
    return assertRuntimeCompatibility({
      packageOf: (name, parentURL) => this.ctx.get('pluginPackages')?.packageOf(name, parentURL),
      clientPath: name => this.ctx.get('clientModules')?.clientPath(name),
    }, import.meta.url);
  }

  @Remote
  getCompatibility(): HostCompatibility {
    try {
      const candidateDshVersion = this.assertRuntimeBaseline();
      return { dependencyBaseline: 'matched', candidateDshVersion, localWritesAllowed: true };
    } catch {
      return { dependencyBaseline: 'unverified', candidateDshVersion: '0.2.0-rc.2 / 0.2.1-rc.2', localWritesAllowed: false };
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
  async listCatalog(sourceRevision: string, query: string, page: number, sort: CatalogSort, signal: AbortSignal): Promise<CatalogPageResult> {
    return this.readSource(sourceRevision, signal, (registry, signal) => registry.catalog(query, page, sort, signal));
  }

  @Remote
  async listLabels(sourceRevision: string, signal: AbortSignal): Promise<CatalogLabels> {
    return this.readSource(sourceRevision, signal, (registry, signal) => registry.labels(signal));
  }

  @Remote
  async listCatalogByLabel(
    sourceRevision: string, query: string, page: number, sort: CatalogSort, label: string, signal: AbortSignal,
  ): Promise<CatalogPageResult> {
    return this.readSource(sourceRevision, signal, (registry, signal) => registry.catalog(query, page, sort, signal, label));
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
      return { detailToken, webUrl: metadata.webUrl, title: metadata.displayName?.trim() || slug, summary: metadata.summary ?? '',
        downloadCount: metadata.downloadCount, starCount: metadata.starCount,
        ratingAvg: metadata.ratingCount === 0 ? undefined : metadata.ratingAvg, ratingCount: metadata.ratingCount,
        namespace, slug, version: resolved.version, versionId: resolved.versionId, documentation,
        tabs: { versions, reviews, securityAudit: modes.securityAudit === 'hide' ? 'hidden' : 'anonymous-denied' } };

    });
  }

  @Remote
  async listVersions(detailToken: string, page: number, signal: AbortSignal): Promise<VersionPage> {
    const detail = this.readDetail(detailToken, 'versions');
    return this.readSource(detail.sourceRevision, signal,
      (registry, signal) => registry.browseVersions(detail.namespace, detail.slug, page, 20, signal));
  }

  @Remote
  async listReviews(detailToken: string, page: number, signal: AbortSignal): Promise<ReviewPage> {
    const detail = this.readDetail(detailToken, 'reviews');
    return this.readSource(detail.sourceRevision, signal,
      (registry, signal) => registry.reviews(detail.skillId, page, 20, signal));
  }

  @RemoteScope('agent')
  async listInstalled(sourceRevision: string, signal: AbortSignal): Promise<InstalledSkillList> {
    const id = this.ctx.invocation?.request.args.agentId;
    return this.listInstalledForAgent(typeof id === 'string' ? id : '', sourceRevision, signal);
  }

  /** Tools use explicit trusted context, never a forged Remote invocation. */
  private async listInstalledForAgent(
    agentId: string, sourceRevision: string, signal: AbortSignal, expectedCwd?: string,
  ): Promise<InstalledSkillList> {
    this.assertSource(sourceRevision);
    const agent = this.ctx.get('agents')?.get(SessionId(agentId));
    const cwd = agent?.session.header.cwd;
    const fs = agent?.ctx.get('fs');
    const runtime = this.localRuntime;
    if (!agent || !cwd || (expectedCwd !== undefined && cwd !== expectedCwd)) throw new RemoteError('skillhub/context-stale', 'Session unavailable.', { retryable: false });
    if (!fs || !runtime) throw new RemoteError('skillhub/context-stale', 'Session unavailable.', { retryable: false });
    const source = this.getContext();
    if (!source.configured) throw new RemoteError('skillhub/source-not-configured', 'Configure a SkillHub source first.', { retryable: false });
    const combined = AbortSignal.any([signal, this.lifetime.signal]);
    const targets = await Promise.all((['user', 'project'] as const).map(async scope => {
      try { return { scope, target: await resolveInstalledPath({ scope, cwd, fs, signal: combined }) }; }
      catch { combined.throwIfAborted(); return { scope, target: undefined }; }
    }));
    const roots = targets.flatMap(entry => entry.target ? [entry.target] : []);
    const outcome = await runtime.listInstalled(source.registryUrl, roots).then(
      result => ({ result, error: undefined }), error => ({ result: undefined, error }),
    );
    const presentation = outcome.result ? await readInstalledPresentations(outcome.result.items, roots, combined) : [];
    combined.throwIfAborted();
    this.assertSource(sourceRevision);
    if (agent.session.header.cwd !== cwd || serviceIdentity(agent.ctx.get('fs')) !== serviceIdentity(fs)
      || this.ctx.get('agents')?.get(SessionId(agent.session.id)) !== agent || this.localRuntime !== runtime) {
      throw new RemoteError('skillhub/context-stale', 'Session changed.', { retryable: false });
    }
    for (const target of roots) {
      let current: ResolvedInstallationTarget;
      try { current = await resolveInstalledPath({ scope: target.scope, cwd, fs, signal: combined }); }
      catch { combined.throwIfAborted(); throw new RemoteError('skillhub/context-stale', 'Skill root changed.', { retryable: false }); }
      if (current.root !== target.root) throw new RemoteError('skillhub/context-stale', 'Skill root changed.', { retryable: false });
    }
    combined.throwIfAborted();
    if (agent.session.header.cwd !== cwd || serviceIdentity(agent.ctx.get('fs')) !== serviceIdentity(fs)
      || this.ctx.get('agents')?.get(SessionId(agent.session.id)) !== agent || this.localRuntime !== runtime) {
      throw new RemoteError('skillhub/context-stale', 'Session changed.', { retryable: false });
    }
    this.assertSource(sourceRevision);
    if (!outcome.result) throw outcome.error;
    const result = outcome.result;
    const unavailable = targets.filter(entry => !entry.target).map(entry => entry.scope);
    const items = result.items.map((item, index) => {
      const itemKey = this.installedItemKey(agent.session.id, cwd, sourceRevision, item);
      const locationKey = createHmac('sha256', this.installedReferenceKey).update(JSON.stringify([agent.session.id, cwd, sourceRevision, item.namespace, item.slug, item.scope, item.targetPath])).digest('base64url');
      return { ...presentation[index], itemKey, locationKey, installationState: item.installationState ?? 'enabled', namespace: item.namespace, slug: item.slug, version: item.version, scope: item.scope, state: item.state };
    });
    return { items, complete: result.complete, issues: result.issues, unavailable };
  }

  @RemoteScope('agent')
  async checkAvailability(sourceRevision: string, itemKey: string, signal: AbortSignal): Promise<SkillAvailability> {
    return this.checkAvailabilityForAgent({ sourceRevision, itemKey }, signal);
  }

  private async checkAvailabilityForAgent(request: ManagementRequest, signal: AbortSignal, agentId?: string): Promise<SkillAvailability> {
    const parsed = managementRequests.availability.parse(request);
    const combined = AbortSignal.any([signal, this.lifetime.signal]);
    const id = agentId ?? this.ctx.invocation?.request.args.agentId;
    const agent = typeof id === 'string' ? this.ctx.get('agents')?.get(SessionId(id)) : undefined;
    const cwd = agent?.session.header.cwd;
    const sessionId = agent?.session.id;
    const fs = agent?.ctx.get('fs');
    const skills = this.ctx.get('skills');
    const revision = this.getContext().sourceRevision;
    const runtime = this.localRuntime;
    const selected = 'itemKey' in parsed ? await this.readInstalledItem(parsed.sourceRevision, parsed.itemKey, combined, agentId) : undefined;
    const valid = () => {
      combined.throwIfAborted(); selected?.valid(); this.assertSource(revision);
      if (!agent || !cwd || agent.session.id !== sessionId || agent.session.header.cwd !== cwd
        || this.ctx.get('agents')?.get(SessionId(sessionId!)) !== agent || this.localRuntime !== runtime
        || serviceIdentity(agent.ctx.get('fs')) !== serviceIdentity(fs)
        || serviceIdentity(this.ctx.get('skills')) !== serviceIdentity(skills)) {
        throw new RemoteError('skillhub/context-stale', 'Session changed.', { retryable: false });
      }
    };
    valid();
    if (selected?.item.installationState === 'disabled') {
      const target = async () => {
        const resolved = await resolveInstalledPath({ scope: selected.item.scope, cwd: cwd!, fs: fs!, signal: combined });
        valid();
        if (join(resolved.root, selected.item.slug) !== selected.item.targetPath) throw new RemoteError('skillhub/context-stale', 'Skill root changed.', { retryable: false });
        return resolved;
      };
      const identity = { registry: this.getContext().registryUrl, namespace: selected.item.namespace,
        slug: selected.item.slug, version: selected.item.version };
      const options = { scope: agent!, cwd: cwd!, signal: combined };
      const diagnosis = await inspectDisabledSkillAvailability({ directory: selected.item.targetPath,
        fingerprint: selected.item.fingerprint, signal: combined, valid,
        baseline: async () => selected.runtime!.inspectRemoval(identity, await target()),
        entry: async () => selected.runtime!.readInstallationEntry(identity, await target()),
        reader: skills ? { get: (name, signal = combined) => skills.get(name, { ...options, signal }),
          ...(typeof skills.snapshot === 'function' ? { snapshot: (signal?: AbortSignal) => skills.snapshot({ ...options, signal: signal ?? combined }) } : {}) } : undefined });
      valid();
      return { ...diagnosis, scope: selected.item.scope, installation: { namespace: selected.item.namespace,
        slug: selected.item.slug, version: selected.item.version, scope: selected.item.scope } };
    }
    const options = { scope: agent!, cwd: cwd!, signal: combined };
    const diagnosis = await inspectSkillAvailability({ ctx: agent!.ctx, signal: combined, valid,
      name: 'name' in parsed ? parsed.name : undefined,
      reader: skills ? { get: name => skills.get(name, options),
        ...(typeof skills.snapshot === 'function' ? { snapshot: () => skills.snapshot(options) } : {}) } : undefined,
      managed: selected ? { directory: selected.item.targetPath, fingerprint: selected.item.fingerprint,
        baseline: async () => {
          const target = await resolveInstalledPath({ scope: selected.item.scope, cwd: cwd!, fs: fs!, signal: combined });
          valid();
          if (join(target.root, selected.item.slug) !== selected.item.targetPath) throw new RemoteError('skillhub/context-stale', 'Skill root changed.', { retryable: false });
          return selected.runtime!.inspectRemoval({ registry: this.getContext().registryUrl,
            namespace: selected.item.namespace, slug: selected.item.slug, version: selected.item.version }, target);
        } } : undefined });
    valid();
    return { ...diagnosis, ...(selected ? { installationState: selected.item.installationState ?? 'enabled' } : {}), ...(selected ? { scope: selected.item.scope, installation: { namespace: selected.item.namespace,
      slug: selected.item.slug, version: selected.item.version, scope: selected.item.scope } } : {}) };
  }

  /** Resolve coordinates through the current DSH winner; no scope/version preference or write. */
  @RemoteScope('agent')
  async selectSkillUse(sourceRevision: string, namespace: string, slug: string, signal: AbortSignal): Promise<SkillUseSelection> {
    const id = this.ctx.invocation?.request.args.agentId;
    const agent = typeof id === 'string' ? this.ctx.get('agents')?.get(SessionId(id)) : undefined;
    const sessionId = agent?.session.id;
    const cwd = agent?.session.header.cwd;
    const fs = agent?.ctx.get('fs');
    const skills = this.ctx.get('skills');
    const runtime = this.localRuntime;
    const combined = AbortSignal.any([signal, this.lifetime.signal]);
    const valid = () => {
      combined.throwIfAborted(); this.assertSource(sourceRevision);
      if (!agent || !sessionId || !cwd || agent.session.id !== sessionId || agent.session.header.cwd !== cwd
        || this.ctx.get('agents')?.get(SessionId(sessionId)) !== agent || this.localRuntime !== runtime
        || serviceIdentity(agent.ctx.get('fs')) !== serviceIdentity(fs)
        || serviceIdentity(this.ctx.get('skills')) !== serviceIdentity(skills)) {
        throw new RemoteError('skillhub/context-stale', 'Session changed.', { retryable: false });
      }
    };
    valid();
    if (!namespace || !slug || namespace.length > 256 || slug.length > 256 || !skills?.snapshot) {
      return { status: 'blocked', reason: 'selection-unverified' };
    }
    return selectUsableInstallation({ namespace, slug, signal: combined, valid,
      list: async signal => {
        const snapshot = await skills.snapshot!({ scope: agent!, cwd: cwd!, signal });
        valid(); signal.throwIfAborted();
        const listed = await this.listInstalled(sourceRevision, signal);
        return { ...listed, complete: listed.complete && snapshot.complete };
      },
      prepare: async (itemKey, signal) => {
        const result = await this.prepareSkillUse(sourceRevision, itemKey, signal);
        valid(); signal.throwIfAborted();
        if (result.status === 'ready') {
          const snapshot = await skills.snapshot!({ scope: agent!, cwd: cwd!, signal });
          valid(); signal.throwIfAborted();
          if (!snapshot.complete || !snapshot.skills.some(skill => skill.name === result.name)) {
            return { status: 'blocked', reason: 'skill-unavailable' };
          }
        }
        return result;
      } });
  }

  /** Read-only handoff: installed snapshots and market names never authorize an invocation. */
  @RemoteScope('agent')
  async prepareSkillUse(sourceRevision: string, itemKey: string, signal: AbortSignal): Promise<SkillUsePreparation> {
    const { item, valid, agent, runtime, fs } = await this.readInstalledItem(sourceRevision, itemKey, signal);
    const combined = AbortSignal.any([signal, this.lifetime.signal]);
    const skills = this.ctx.get('skills');
    const cwd = agent!.session.header.cwd!;
    const readyContext = () => {
      valid(); combined.throwIfAborted();
      if (serviceIdentity(this.ctx.get('skills')) !== serviceIdentity(skills)) {
        throw new RemoteError('skillhub/context-stale', 'Skill discovery service changed.', { retryable: false });
      }
    };
    readyContext();
    if (item.installationState === 'disabled') return { status: 'blocked', reason: 'installation-disabled' };
    if (!skills || runtime!.getStatus().state !== 'ready') return { status: 'blocked', reason: 'host-unavailable' };
    if (runtime!.getStatus().pendingOperations > 0) return { status: 'blocked', reason: 'operation-pending' };
    if (item.state !== 'present' || !item.fingerprint) return { status: 'blocked', reason: 'installation-unverified' };
    try {
      const target = await resolveInstalledPath({ scope: item.scope, cwd, fs: fs!, signal: combined });
      readyContext();
      if (join(target.root, item.slug) !== item.targetPath) {
        throw new RemoteError('skillhub/context-stale', 'Installed target changed.', { retryable: false });
      }
      const identity = { registry: this.getContext().registryUrl, namespace: item.namespace, slug: item.slug, version: item.version };
      const baseline = await runtime!.inspectRemoval(identity, target);
      readyContext();
      if (baseline.status === 'blocked') return { status: 'blocked', reason:
        baseline.reason === 'shared-target' ? 'shared-installation' : 'files-changed' };
      if (baseline.kind !== 'uninstall' || baseline.inventoryItem.targets.find(target => target.installDir === item.targetPath)?.fingerprint !== item.fingerprint
        || baseline.target.path !== item.targetPath) return { status: 'blocked', reason: 'files-changed' };
      const examples = await runtime!.readSkillExamples(item.targetPath);
      readyContext();
      if (examples.state === 'ready' && baseline.target.files['.skillhub/examples.json'] !== examples.digest) {
        return { status: 'blocked', reason: 'files-changed' };
      }
      const observed = await inspectDshSkill(agent!.ctx, item.targetPath, combined,
        name => skills.get(name, { scope: agent!, cwd, signal: combined }));
      readyContext();
      if (observed.status !== 'ready') return { status: 'blocked', reason:
        observed.status === 'shadowed' ? 'skill-shadowed' : 'skill-unavailable' };
      if (!observed.skill.invocation.userInvocable) return { status: 'blocked', reason: 'user-invocation-disabled' };
      if (!/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(observed.skill.name)) {
        return { status: 'blocked', reason: 'unsupported-name' };
      }
      // Re-read both the opaque record and every selected-target file after asynchronous discovery.
      const latest = await this.readInstalledItem(sourceRevision, itemKey, combined);
      readyContext();
      const current = await resolveInstalledPath({ scope: item.scope, cwd, fs: fs!, signal: combined });
      readyContext();
      if (current.root !== target.root || current.dshHome !== target.dshHome) {
        throw new RemoteError('skillhub/context-stale', 'Installed target changed.', { retryable: false });
      }
      const inspected = await runtime!.inspectRemoval(identity, current);
      readyContext(); latest.valid();
      if (!isDeepStrictEqual(inspected, baseline)) return { status: 'blocked', reason: 'files-changed' };
      if (runtime!.getStatus().state !== 'ready' || runtime!.getStatus().pendingOperations > 0) {
        return { status: 'blocked', reason: 'operation-pending' };
      }
      const winner = await skills.get(observed.skill.name, { scope: agent!, cwd, signal: combined });
      readyContext();
      if (!winner) return { status: 'blocked', reason: 'skill-unavailable' };
      if (!sameDshSkill(winner, observed.skill, item.targetPath)) return { status: 'blocked', reason: 'skill-shadowed' };
      const finalBaseline = await runtime!.inspectRemoval(identity, current);
      readyContext(); latest.valid();
      if (!isDeepStrictEqual(finalBaseline, baseline)) return { status: 'blocked', reason: 'files-changed' };
      if (runtime!.getStatus().state !== 'ready' || runtime!.getStatus().pendingOperations > 0) {
        return { status: 'blocked', reason: 'operation-pending' };
      }
      return { status: 'ready', name: observed.skill.name, version: item.version, scope: item.scope, examples };
    } catch (error) {
      readyContext();
      if (error instanceof RemoteError) throw error;
      return { status: 'blocked', reason: 'skill-unavailable' };
    }
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
  async beginInstalledUpdateScan(sourceRevision: string, signal: AbortSignal): Promise<{ scanId: string; itemKeys: string[] }> {
    this.assertSource(sourceRevision);
    const agentId = this.ctx.invocation?.request.args.agentId;
    const agent = typeof agentId === 'string' ? this.ctx.get('agents')?.get(SessionId(agentId)) : undefined;
    const runtime = this.localRuntime;
    const fs = agent?.ctx.get('fs');
    const cwd = agent?.session.header.cwd;
    const sessionId = agent?.session.id;
    const controller = new AbortController();
    const scanId = randomUUID();
    const combined = AbortSignal.any([controller.signal, this.lifetime.signal]);
    let timer: ReturnType<typeof setTimeout> | undefined;
    const deadline = Date.now() + 30 * 60_000;
    let expiresAt = Date.now() + 60_000;
    const touch = () => {
      clearTimeout(timer);
      expiresAt = Math.min(Date.now() + 60_000, deadline);
      timer = setTimeout(close, Math.max(0, expiresAt - Date.now())); timer.unref();
    };
    let initializing = true;
    const close = () => {
      controller.abort(); clearTimeout(timer);
      // Keep the reserved slot until uncancellable filesystem initialization settles.
      if (!initializing) this.installedUpdateScans.delete(scanId);
    };
    const valid = () => {
      if (Date.now() >= expiresAt) close();
      combined.throwIfAborted(); this.assertSource(sourceRevision);
      if (!agent || !runtime || !fs || !cwd || agent.session.id !== sessionId || agent.session.header.cwd !== cwd
        || this.ctx.get('agents')?.get(SessionId(sessionId!)) !== agent || this.localRuntime !== runtime
        || serviceIdentity(agent.ctx.get('fs')) !== serviceIdentity(fs)) throw new RemoteError('skillhub/context-stale', 'Update scan context changed.', { retryable: false });
    };
    valid(); signal.throwIfAborted();
    if (this.installedUpdateScans.size >= 4) throw new RemoteError('skillhub/context-stale', 'Too many update scans.', { retryable: false });
    // Reserve the slot before asynchronous snapshot creation; abandoned begins also expire.
    this.installedUpdateScans.set(scanId, { sourceRevision, agentId: String(agentId), valid, close,
      check: async () => { throw new Error('Update scan is initializing'); } });
    touch();
    const abort = () => close(); signal.addEventListener('abort', abort, { once: true });
    try {
      const roots: Awaited<ReturnType<typeof resolveInstalledPath>>[] = [];
      for (const scope of ['user', 'project'] as const) {
        try { roots.push(await resolveInstalledPath({ scope, cwd: cwd!, fs: fs!, signal: combined })); }
        catch { combined.throwIfAborted(); }
      }
      const snapshot = await runtime!.openInstalledSnapshot(this.getContext().registryUrl, roots);
      valid(); signal.throwIfAborted();
      const items = new Map(snapshot.projection.items.map(item => [this.installedItemKey(sessionId!, cwd!, sourceRevision, item), item]));
      const candidates = new Map<string, Promise<Awaited<ReturnType<RegistryAdapter['resolve']>>>>();
      let resolving = 0;
      const waiters = new Set<() => void>();
      const resolveCandidate = async (item: InstalledProjectionItem) => {
        while (resolving >= 3) {
          combined.throwIfAborted();
          await new Promise<void>(done => {
            const wake = () => { waiters.delete(wake); combined.removeEventListener('abort', wake); done(); };
            waiters.add(wake); combined.addEventListener('abort', wake, { once: true });
          });
        }
        combined.throwIfAborted(); valid(); resolving++;
        try { return await this.readSource(sourceRevision, combined, (registry, signal) => registry.resolve(item.namespace, item.slug, undefined, signal)); }
        finally { resolving--; for (const wake of [...waiters]) wake(); }
      };
      const read = async (key: string) => {
        valid();
        const item = items.get(key);
        if (!item) throw new Error('Installed reference is not in this scan');
        const target = await resolveInstalledPath({ scope: item.scope, cwd: cwd!, fs: fs!, signal: combined });
        if (join(target.root, item.slug) !== item.targetPath) throw new Error('Installed root changed');
        const current = await snapshot.read(item); valid(); return current;
      };
      this.installedUpdateScans.set(scanId, { sourceRevision, agentId: String(agentId), valid, close,
        check: async (key, itemSignal) => {
          try {
            itemSignal.throwIfAborted(); valid(); touch();
            const item = await read(key);
            const base = { currentVersion: item.version, candidateVersion: null, checkedAt: new Date().toISOString() };
            if (item.state !== 'present') return { ...base, state: 'blocked', reason: 'local-record-unverified' };
            const identity = JSON.stringify([item.namespace, item.slug]);
            let candidate = candidates.get(identity);
            if (!candidate) {
              candidate = resolveCandidate(item);
              candidates.set(identity, candidate);
            }
            const resolved = await candidate.then(value => ({ value }), error => ({ error }));
            await read(key); itemSignal.throwIfAborted();
            if ('error' in resolved) return { ...base, state: 'unavailable', reason: installationFailureCode(resolved.error) };
            const assessment = await runtime!.assessUpdate(item, resolved.value);
            valid(); itemSignal.throwIfAborted();
            return { ...assessment, checkedAt: new Date().toISOString() };
          } catch (error) {
            if (itemSignal.aborted) throw error;
            close();
            throw new RemoteError('skillhub/context-stale', 'Installed update scan changed or expired.', { retryable: false });
          }
        } });
      return { scanId, itemKeys: [...items.keys()] };
    } catch (error) { close(); throw error; }
    finally {
      initializing = false;
      signal.removeEventListener('abort', abort);
      if (controller.signal.aborted) this.installedUpdateScans.delete(scanId);
    }
  }

  @RemoteScope('agent')
  async checkInstalledUpdateScan(sourceRevision: string, scanId: string, itemKey: string, signal: AbortSignal): Promise<InstalledUpdateCheck> {
    const scan = this.installedUpdateScans.get(scanId);
    if (!scan || scan.sourceRevision !== sourceRevision || scan.agentId !== this.ctx.invocation?.request.args.agentId) {
      throw new RemoteError('skillhub/context-stale', 'Update scan expired.', { retryable: false });
    }
    return scan.check(itemKey, signal);
  }

  @RemoteScope('agent')
  async endInstalledUpdateScan(sourceRevision: string, scanId: string): Promise<void> {
    const scan = this.installedUpdateScans.get(scanId);
    if (scan?.sourceRevision === sourceRevision && scan.agentId === this.ctx.invocation?.request.args.agentId) scan.close();
  }

  @RemoteScope('agent')
  async checkUpdate(sourceRevision: string, itemKey: string, signal: AbortSignal): Promise<InstalledUpdateCheck> {
    return this.checkUpdateForVersion(sourceRevision, itemKey, undefined, signal);
  }

  @RemoteScope('agent')
  async checkVersionUpdate(sourceRevision: string, itemKey: string, version: string, signal: AbortSignal): Promise<InstalledUpdateCheck> {
    return this.checkUpdateForVersion(sourceRevision, itemKey, version, signal);
  }

  private async checkUpdateForVersion(sourceRevision: string, itemKey: string, version: string | undefined, signal: AbortSignal, agentId?: string): Promise<InstalledUpdateCheck> {
    const { item, valid } = await this.readInstalledItem(sourceRevision, itemKey, signal, agentId);
    const base = { currentVersion: item.version, candidateVersion: null, checkedAt: new Date().toISOString() };
    if (item.state !== 'present') return { ...base, state: 'blocked', reason: 'local-record-unverified' };
    let candidate;
    try {
      candidate = await this.readSource(sourceRevision, signal, (registry, signal) => registry.resolve(item.namespace, item.slug, version, signal));
    } catch (error) {
      signal.throwIfAborted(); valid();
      return { ...base, state: 'unavailable', reason: installationFailureCode(error) };
    }
    if (version !== undefined && candidate.version !== version) throw new RemoteError('skillhub/context-stale', 'Selected version changed.', { retryable: false });
    const after = await this.readInstalledItem(sourceRevision, itemKey, signal, agentId);
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
    return this.prepareUpgradeForVersion(sourceRevision, itemKey, undefined, signal);
  }

  @RemoteScope('agent')
  async prepareVersionUpgrade(sourceRevision: string, itemKey: string, version: string, signal: AbortSignal): Promise<UpgradePlanView> {
    return this.prepareUpgradeForVersion(sourceRevision, itemKey, version, signal);
  }

  private async prepareUpgradeForVersion(sourceRevision: string, itemKey: string, version: string | undefined, signal: AbortSignal, agentId?: string): Promise<UpgradePlanView> {
    const { item, valid, agent, runtime, fs } = await this.readInstalledItem(sourceRevision, itemKey, signal, agentId);
    const combined = AbortSignal.any([signal, this.lifetime.signal]);
    const context = this.selectionScope(agent!);
    const base: UpgradePlanView = { hostInstanceId: runtime!.getStatus().hostInstanceId!, status: 'blocked', reason: '', planId: null, expiresAt: null,
      currentVersion: item.version, candidateVersion: null, targets: [] };
    if (runtime!.getStatus().pendingOperations > 0) return { ...base, reason: 'recovery-required' };
    if (item.state !== 'present') return { ...base, reason: 'local-record-unverified' };
    const policy = agent!.ctx.get('sandboxPolicy')?.resolve({ session: agent!.session });
    const roots = [await resolveInstallationPath({ scope: item.scope, cwd: context.cwd, fs: fs!, policy, signal: combined })];
    const baseline = await runtime!.inspectUpgrade({ registry: this.getContext().registryUrl,
      namespace: item.namespace, slug: item.slug, version: item.version }, roots);
    valid(); combined.throwIfAborted();
    if (baseline.status === 'blocked') return { ...base, reason: baseline.reason };
    const candidate = await this.readSource(sourceRevision, combined,
      (registry, signal) => registry.resolve(item.namespace, item.slug, version, signal));
    if (version !== undefined && candidate.version !== version) throw new RemoteError('skillhub/context-stale', 'Selected version changed.', { retryable: false });
    const assessment = await runtime!.assessUpdate(item, candidate);
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
  async prepareToggle(sourceRevision: string, itemKey: string, desiredState: 'enabled' | 'disabled', signal: AbortSignal): Promise<TogglePlanView> {
    return this.prepareToggleForAgent(sourceRevision, itemKey, desiredState, signal);
  }

  private async prepareToggleForAgent(sourceRevision: string, itemKey: string, desiredState: 'enabled' | 'disabled', signal: AbortSignal, agentId?: string): Promise<TogglePlanView> {
    if (!['enabled', 'disabled'].includes(desiredState)) throw new RemoteError('skillhub/invalid-installation-request', 'Invalid installation state.', { retryable: false });
    const { item, valid, agent, runtime, fs } = await this.readInstalledItem(sourceRevision, itemKey, signal, agentId);
    const combined = AbortSignal.any([signal, this.lifetime.signal]);
    const context = this.selectionScope(agent!);
    const base: TogglePlanView = { hostInstanceId: runtime!.getStatus().hostInstanceId!, status: 'blocked', reason: '',
      planId: null, expiresAt: null, currentVersion: item.version, beforeState: item.installationState ?? 'enabled', afterState: desiredState, target: null };
    if (runtime!.getStatus().pendingOperations > 0) return { ...base, reason: 'recovery-required' };
    if (item.state !== 'present') return { ...base, reason: 'local-record-unverified' };
    const policy = agent!.ctx.get('sandboxPolicy')?.resolve({ session: agent!.session });
    const target = await resolveInstallationPath({ scope: item.scope, cwd: context.cwd, fs: fs!, policy, signal: combined });
    if (join(target.root, item.slug) !== item.targetPath) throw new RemoteError('skillhub/context-stale', 'Toggle target changed.', { retryable: false });
    const baseline = await runtime!.inspectToggle({ registry: this.getContext().registryUrl,
      namespace: item.namespace, slug: item.slug, version: item.version }, target, desiredState);
    valid(); combined.throwIfAborted();
    if (baseline.status === 'blocked') return { ...base, reason: baseline.reason };
    if (baseline.status === 'unchanged') return { ...base, status: 'unchanged', reason: 'already-' + desiredState };
    const currentPolicy = agent!.ctx.get('sandboxPolicy')?.resolve({ session: agent!.session });
    const current = await resolveInstallationPath({ scope: item.scope, cwd: context.cwd, fs: fs!, policy: currentPolicy, signal: combined });
    if (current.root !== target.root || current.dshHome !== target.dshHome || baseline.beforeState !== base.beforeState) {
      throw new RemoteError('skillhub/context-stale', 'Toggle target changed.', { retryable: false });
    }
    await this.readInstalledItem(sourceRevision, itemKey, combined, agentId);
    await assertInstallationMutation(target.root, [baseline.target.path, join(baseline.target.path, 'SKILL.md'), join(baseline.target.path, 'SKILL.md.disabled')], currentPolicy);
    valid(); combined.throwIfAborted();
    return { ...base, ...this.togglePlans.issue(context, baseline), status: 'ready', reason: desiredState,
      target: { scope: baseline.target.scope, path: baseline.target.path } };
  }

  @RemoteScope('agent')
  async prepareUninstall(sourceRevision: string, itemKey: string, signal: AbortSignal): Promise<UninstallPlanView> {
    return this.prepareUninstallForAgent(sourceRevision, itemKey, signal);
  }

  private async prepareUninstallForAgent(sourceRevision: string, itemKey: string, signal: AbortSignal, agentId?: string): Promise<UninstallPlanView> {
    const { item, valid, agent, runtime, fs } = await this.readInstalledItem(sourceRevision, itemKey, signal, agentId);
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

  /** Opaque references are stable within a Host and bind the full trusted record snapshot. */
  private installedItemKey(sessionId: string, cwd: string, sourceRevision: string, item: InstalledProjectionItem): string {
    return createHmac('sha256', this.installedReferenceKey).update(JSON.stringify([
      sessionId, cwd, sourceRevision, item.scope, item.namespace, item.slug, item.version,
      item.targetPath, item.fingerprint ?? null, item.state, item.recordRevision ?? null, item.installationState ?? 'enabled',
    ])).digest('base64url');
  }

  private async readInstalledItem(sourceRevision: string, itemKey: string, signal: AbortSignal, agentId?: string) {
    this.assertSource(sourceRevision);
    const id = agentId ?? this.ctx.invocation?.request.args.agentId;
    const agent = typeof id === 'string' ? this.ctx.get('agents')?.get(SessionId(id)) : undefined;
    const runtime = this.localRuntime;
    const fs = agent?.ctx.get('fs');
    const cwd = agent?.session.header.cwd;
    const sessionId = agent?.session.id;
    const valid = () => {
      this.assertSource(sourceRevision);
      if (!agent || !runtime || !fs || !cwd || sessionId !== agent.session.id || cwd !== agent.session.header.cwd
        || this.localRuntime !== runtime || this.ctx.get('agents')?.get(SessionId(agent.session.id)) !== agent
        || serviceIdentity(agent.ctx.get('fs')) !== serviceIdentity(fs)) {
        throw new RemoteError('skillhub/context-stale', 'Reopen the installed skill list.', { retryable: false });
      }
    };
    valid();
    const combined = AbortSignal.any([signal, this.lifetime.signal]);
    // Re-read trusted roots rather than retaining an evictable cache or decoding client paths.
    const roots: Awaited<ReturnType<typeof resolveInstalledPath>>[] = [];
    for (const scope of ['user', 'project'] as const) {
      try { roots.push(await resolveInstalledPath({ scope, cwd: cwd!, fs: fs!, signal: combined })); }
      catch { combined.throwIfAborted(); }
    }
    const result = await runtime!.listInstalled(this.getContext().registryUrl, roots);
    combined.throwIfAborted();
    valid();
    const item = result.items.find(item => this.installedItemKey(sessionId!, cwd!, sourceRevision, item) === itemKey);
    if (!item) throw new RemoteError('skillhub/context-stale', 'Installed record changed or unavailable.', { retryable: false });
    const target = await resolveInstalledPath({ scope: item.scope, cwd: cwd!, fs: fs!, signal: combined });
    if (join(target.root, item.slug) !== item.targetPath) {
      throw new RemoteError('skillhub/context-stale', 'Installed target changed.', { retryable: false });
    }
    combined.throwIfAborted();
    valid();
    return { item, valid, agent, runtime, fs };
  }

  @RemoteScope('agent')
  async executePlan(input: HostExecutePlanRequest, signal: AbortSignal): Promise<NativeOperation | null> {
    const parsed = executePlanRequest.safeParse(this.ctx.invocation?.request.args.input ?? input);
    if (!parsed.success) throw new RemoteError('skillhub/invalid-installation-request', 'Invalid mutation request.', { retryable: false });
    const id = this.ctx.invocation?.request.args.agentId;
    return this.executePlanForAgent(typeof id === 'string' ? id : '', parsed.data, signal, 'plaza');
  }

  private async executePlanForAgent(agentId: string, request: HostExecutePlanRequest, signal: AbortSignal, initiatedFrom: 'plaza' | 'conversation'): Promise<NativeOperation | null> {
    const agent = this.ctx.get('agents')?.get(SessionId(agentId));
    const runtime = this.localRuntime;
    const hostInstanceId = runtime?.getStatus().hostInstanceId;
    if (!agent || !runtime || !hostInstanceId) throw new RemoteError('skillhub/context-stale', 'Mutation context unavailable.', { retryable: false });
    try {
      const previous = await runtime.findPlanOperation({ hostInstanceId, ownerId: agent.session.id, requestKey: request.requestKey,
        mode: 'retry', parameters: { planId: request.planId } });
      if (previous) return previous;
      if (this.togglePlans.has(request.planId)) {
        return await runtime.submitToggle({ hostInstanceId, ownerId: agent.session.id, requestKey: request.requestKey,
          mode: 'new', parameters: { planId: request.planId } }, async () => {
          const combined = AbortSignal.any([signal, this.lifetime.signal]);
          combined.throwIfAborted();
          const context = this.selectionScope(agent);
          const baseline = this.togglePlans.read(request.planId, context);
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
          this.togglePlans.claim(request.planId, context, request.requestKey);
          const item = baseline.inventoryItem;
          const operationSignal = new AbortController().signal;
          return {
            acceptance: { kind: baseline.afterState === 'enabled' ? 'enable' : 'disable', namespace: item.namespace, slug: item.slug, version: item.targets.find(target => target.installDir === baseline.target.path)!.version,
              targetLabel: target.scope === 'user' ? '当前用户' : '当前项目', targetPaths: [baseline.target.path],
              context: { sourceIdentity: source.registryUrl, sourceLabel: source.registryUrl, sourceRevision: context.sourceRevision,
                hostLabel: 'DSH Host', initiatedFrom } },
            execution: { baseline, authorize: async (paths: readonly string[]) => {
              valid();
              const currentPolicy = agent.ctx.get('sandboxPolicy')?.resolve({ session: agent.session });
              const current = await resolveInstallationPath({ scope: target.scope, cwd: context.cwd, fs: filesystem,
                policy: currentPolicy, signal: operationSignal });
              valid();
              if (current.root !== target.root || current.dshHome !== target.dshHome) throw new Error('Removal root changed');
              await assertInstallationMutation(target.root, paths, currentPolicy);
            } },
          };
        }) ?? null;
      }
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
            acceptance: { kind: baseline.kind, namespace: item.namespace, slug: item.slug, version: item.targets.find(target => target.installDir === baseline.target.path)!.version,
              targetLabel: target.scope === 'user' ? '当前用户' : '当前项目', targetPaths: [baseline.target.path],
              context: { sourceIdentity: source.registryUrl, sourceLabel: source.registryUrl, sourceRevision: context.sourceRevision,
                hostLabel: 'DSH Host', initiatedFrom } },
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
              hostLabel: 'DSH Host', initiatedFrom } },
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
    const pendingOrigin = this.pendingConversationChoices.get(selectionRef);
    if (pendingOrigin) this.pendingConversationChoices.set(fixedRef, pendingOrigin);
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
    if (!['install', 'upgrade', 'uninstall', 'remove-stale-record', 'enable', 'disable'].includes(operation.kind)) {
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

  /** Use the Host question surface without creating an operation until a human answers. */
  async installConversationWithChoice(input: unknown, context: ConversationSearchContext, signal: AbortSignal): Promise<NativeOperation | PendingConversationInstall | CancelledConversationInstall | undefined> {
    const request = conversationInstallRequest.parse(input);
    return this.installConversation({ ...request, scope: request.scope ?? 'user' }, context, signal);
  }

  /** Stable tool identity is checked in the journal before resolving ephemeral detail/target references. */
  async installConversation(input: unknown, context: ConversationSearchContext, signal: AbortSignal): Promise<NativeOperation | PendingConversationInstall | undefined> {
    const request = conversationInstallRequest.parse(input);
    const runtime = this.localRuntime;
    const agent = this.ctx.get('agents')?.get(SessionId(context.sessionId));
    const hostInstanceId = runtime?.getStatus().hostInstanceId;
    if (!runtime || !hostInstanceId || !agent || agent.session.header.cwd !== context.cwd) {
      throw new RemoteError('skillhub/context-stale', 'Installation context unavailable.', { retryable: false });
    }
    const requestKey = conversationInstallKey({ hostInstanceId, sessionId: context.sessionId, callId: context.callId });
    const parameters = JSON.stringify({ ...request, cwd: context.cwd });
    const pending = this.pendingConversationInstalls.get(requestKey);
    if (pending) {
      if (pending.agent !== agent || pending.runtime !== runtime) throw new RemoteError('skillhub/context-stale', 'Conversation changed.', { retryable: false });
      if (pending.parameters !== parameters) throw new RemoteError('skillhub/request-conflict', 'Request parameters changed.', { retryable: false });
      const result = await pending.result;
      signal.throwIfAborted();
      this.selections.read(result.selectionRef, this.selectionScope(agent));
      if (this.localRuntime !== runtime || this.ctx.get('agents')?.get(SessionId(context.sessionId)) !== agent) {
        throw new RemoteError('skillhub/context-stale', 'Conversation changed.', { retryable: false });
      }
      return result;
    }
    if (!request.scope) {
      // No transaction, target token, download, or fingerprint is created before visible confirmation.
      const result = (async (): Promise<PendingConversationInstall> => {
        signal.throwIfAborted();
        const scope = this.selectionScope(agent);
        let selectionRef: string;
        let candidate: { namespace: string; slug: string; version: string };
        if ('selectionRef' in request) {
          const { namespace, slug, version } = this.selections.read(request.selectionRef, scope);
          candidate = { namespace, slug, version };
          selectionRef = this.selections.add({ ...context, sourceRevision: scope.sourceRevision }, candidate);
        } else {
          const resolved = await this.readSource(scope.sourceRevision, signal,
            (registry, signal) => registry.resolve(request.namespace, request.slug, undefined, signal));
          candidate = { namespace: request.namespace, slug: request.slug, version: resolved.version };
          selectionRef = this.selections.add({ ...context, sourceRevision: scope.sourceRevision }, candidate);
        }
        signal.throwIfAborted();
        this.assertSource(scope.sourceRevision);
        if (agent.session.header.cwd !== context.cwd || this.localRuntime !== runtime
          || this.ctx.get('agents')?.get(SessionId(context.sessionId)) !== agent) {
          throw new RemoteError('skillhub/context-stale', 'Conversation changed.', { retryable: false });
        }
        this.pendingConversationChoices.set(selectionRef, { agent, runtime });
        return { schemaVersion: 1, kind: 'skillhub-install', status: 'awaiting-confirmation',
          toolCallId: context.callId, selectionRef, namespace: candidate.namespace, slug: candidate.slug,
          version: candidate.version, sourceLabel: this.getContext().registryUrl,
          sourceRevision: scope.sourceRevision, createdAt: new Date().toISOString() };
      })();
      // Retain a failed choice too: retrying the call must never silently resolve a newer candidate.
      this.pendingConversationInstalls.set(requestKey, { parameters, agent, runtime, result });
      return result;
    }
    if ('selectionRef' in request && this.pendingConversationChoices.has(request.selectionRef)) {
      throw new RemoteError('skillhub/context-stale', 'Choose the scope and confirm in the installation card.', { retryable: false });
    }
    return runtime.submitInstall({ ownerId: context.sessionId, hostInstanceId, requestKey, mode: 'new',
      parameters: { ...request, cwd: context.cwd, initiatedFrom: 'conversation' } }, async () => {
      signal.throwIfAborted();
      const scope = this.selectionScope(agent);
      let detailToken: string;
      if ('selectionRef' in request) {
        const resolved = await this.selections.resolveForInstall(request.selectionRef, scope, async candidate => {
          const detail = await this.getSkillDetail(scope.sourceRevision, candidate.namespace, candidate.slug, candidate.version, signal);
          const snapshot = this.details.get(detail.detailToken);
          if (!snapshot) throw new RemoteError('skillhub/context-stale', 'Detail expired.', { retryable: false });
          return { detailToken: detail.detailToken, snapshot };
        });
        if (!this.details.has(resolved.detailToken)) throw new RemoteError('skillhub/context-stale', 'Detail expired.', { retryable: false });
        detailToken = resolved.detailToken;
      } else {
        const resolved = await this.readSource(scope.sourceRevision, signal,
          (registry, signal) => registry.resolve(request.namespace, request.slug, undefined, signal));
        const detail = await this.getSkillDetail(scope.sourceRevision, request.namespace, request.slug, resolved.version, signal);
        detailToken = detail.detailToken;
      }
      signal.throwIfAborted();
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
      if (!['install', 'upgrade', 'uninstall', 'remove-stale-record', 'enable', 'disable'].includes(operation.kind)
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
        if (operation.kind === 'install' || operation.kind === 'upgrade' || operation.kind === 'enable' || operation.kind === 'disable') {
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
    const pending = this.pendingConversationChoices.get(reference);
    if (pending && (pending.agent !== agent || pending.runtime !== this.localRuntime)) {
      throw new RemoteError('skillhub/context-stale', 'Installation choice expired. Search again.', { retryable: false });
    }
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
    const agent = this.ctx.get('agents')?.get(SessionId(context.sessionId));
    const fs = agent?.ctx.get('fs');
    const runtime = this.localRuntime;
    const combined = AbortSignal.any([signal, this.lifetime.signal]);
    const valid = () => {
      combined.throwIfAborted();
      this.assertSource(source.sourceRevision);
      if (!agent || agent.session.id !== context.sessionId || agent.session.header.cwd !== context.cwd
        || this.ctx.get('agents')?.get(SessionId(context.sessionId)) !== agent
        || serviceIdentity(agent.ctx.get('fs')) !== serviceIdentity(fs) || this.localRuntime !== runtime) {
        throw new RemoteError('skillhub/context-stale', 'Search session changed.', { retryable: false });
      }
    };
    valid();
    const result = await this.listSkills(source.sourceRevision, query, combined);
    valid();
    let installed: InstalledSkillList | undefined;
    try {
      if (fs && runtime) installed = await this.listInstalledForAgent(context.sessionId, source.sourceRevision, combined, context.cwd);
    }
    catch (error) {
      valid();
      if (error instanceof RemoteError) throw error;
      // Local read failures cannot turn successful market results into uninstalled claims.
    }
    valid();
    const checkedAt = new Date().toISOString();
    const items = result.items.map(item => {
      const selectionRef = this.selections.add({ ...context, sourceRevision: source.sourceRevision },
        { namespace: item.namespace, slug: item.slug, version: item.latestVersion });
      return { ...item, selectionRef, installation: summarizeInstallation(item, installed, checkedAt) };
    });
    return { ...result, items, schemaVersion: 1, kind: 'skillhub-search', query,
      sourceRevision: source.sourceRevision, sourceLabel: source.registryUrl, createdAt: checkedAt };
  }

  /** Model adapters always supply trusted exec context; never synthesize a Remote invocation. */
  async manageConversation(action: ManagementAction, input: ManagementRequest, context: ConversationSearchContext, signal: AbortSignal, useQuestions: boolean = true): Promise<object> {
    const request = managementRequests[action].parse(input);
    const agent = this.ctx.get('agents')?.get(SessionId(context.sessionId));
    const runtime = this.localRuntime;
    if (!agent || !runtime || agent.session.header.cwd !== context.cwd) {
      throw new RemoteError('skillhub/context-stale', 'Conversation unavailable.', { retryable: false });
    }
    signal.throwIfAborted();
    if (action === 'availability') return { schemaVersion: 1, kind: 'skillhub-availability', toolCallId: context.callId,
      diagnosis: await this.checkAvailabilityForAgent(request, signal, context.sessionId) };
    if (action === 'operation') {
      const { operationId, hostInstanceId } = managementRequests.operation.parse(request);
      if (runtime.getStatus().hostInstanceId !== hostInstanceId) throw new RemoteError('skillhub/context-stale', 'Host changed.', { retryable: false });
      const operation = await runtime.getOperation(operationId, context.sessionId);
      signal.throwIfAborted();
      if (this.localRuntime !== runtime || this.ctx.get('agents')?.get(SessionId(context.sessionId)) !== agent || agent.session.header.cwd !== context.cwd) {
        throw new RemoteError('skillhub/context-stale', 'Conversation changed.', { retryable: false });
      }
      return { schemaVersion: 1, kind: 'skillhub-operation', toolCallId: context.callId, operation: operation ?? null };
    }
    const source = this.getContext();
    if (action === 'list') return { schemaVersion: 1, kind: 'skillhub-installed', toolCallId: context.callId,
      sourceRevision: source.sourceRevision, sourceLabel: source.registryUrl, ...await this.listInstalledForAgent(context.sessionId, source.sourceRevision, signal, context.cwd) };
    const selected = ['uninstall', 'enable', 'disable'].includes(action) ? managementRequests.uninstall.parse(request) : managementRequests.check.parse(request);
    // Retain one preparation and UUIDv4 for each model call; failed calls never silently re-resolve latest.
    const key = JSON.stringify([context.sessionId, context.callId]);
    const parameters = JSON.stringify([action, request, context.cwd]);
    const previous = this.managementRequests.get(key);
    if (previous) {
      if (previous.parameters !== parameters) throw new RemoteError('skillhub/request-conflict', 'Request changed.', { retryable: false });
      try { return await previous.result; }
      catch (error) {
        const retained = [...this.managementConfirmations.entries()].find(([, entry]) =>
          entry.context.sessionId === context.sessionId && entry.context.callId === context.callId && entry.attempted);
        if (retained) return this.confirmManagementForAgent(context.sessionId, retained[0], true, signal);
        throw error;
      }
    }
    if (this.managementRequests.size >= 400) throw new RemoteError('skillhub/installation-unavailable', 'Too many retained management requests. Restart the plugin after resolving pending operations.', { retryable: false });
    const work = (async (): Promise<object> => {
      const list = await this.listInstalledForAgent(context.sessionId, selected.sourceRevision, signal, context.cwd);
      const item = list.items.find(item => item.itemKey === selected.itemKey);
      if (!item) throw new RemoteError('skillhub/context-stale', 'Installed selection changed.', { retryable: false });
      const version = 'version' in selected && typeof selected.version === 'string' ? selected.version : undefined;
      if (action === 'check') return { schemaVersion: 1, kind: 'skillhub-update-check', toolCallId: context.callId,
        sourceRevision: selected.sourceRevision, sourceLabel: source.registryUrl, item,
        check: await this.checkUpdateForVersion(selected.sourceRevision, selected.itemKey, version, signal, context.sessionId) };
      const plan = action === 'upgrade'
        ? await this.prepareUpgradeForVersion(selected.sourceRevision, selected.itemKey, version, signal, context.sessionId)
        : action === 'enable' || action === 'disable'
          ? await this.prepareToggleForAgent(selected.sourceRevision, selected.itemKey, action === 'enable' ? 'enabled' : 'disabled', signal, context.sessionId)
        : await this.prepareUninstallForAgent(selected.sourceRevision, selected.itemKey, signal, context.sessionId);
      const view: ConversationManagementResult = { schemaVersion: 1, kind: 'skillhub-management', toolCallId: context.callId,
        action: action === 'upgrade' || action === 'enable' || action === 'disable' ? action : 'uninstall', sourceRevision: selected.sourceRevision, sourceLabel: source.registryUrl, item,
        status: plan.status === 'ready' ? 'awaiting-confirmation' : 'blocked', reason: plan.reason,
        candidateVersion: 'candidateVersion' in plan ? plan.candidateVersion : null, confirmationRef: null, operation: null };
      if (plan.status !== 'ready' || !plan.planId) return view;
      const confirmationRef = randomUUID();
      view.confirmationRef = confirmationRef;
      this.managementConfirmations.set(confirmationRef, { context: { ...context }, agent, runtime,
        sourceRevision: selected.sourceRevision, planId: plan.planId, requestKey: randomUUID(), view });
      const questions = this.ctx.get('userQuestions');
      if (!questions || !useQuestions) return view;
      const label = action === 'upgrade' ? '确认更新' : action === 'enable' ? '确认启用' : action === 'disable' ? '确认停用' : '确认卸载';
      let answer;
      try {
        answer = await questions.ask({ agent, signal, questions: [{ id: 'skillhub-management', header: `${label.slice(2)}技能`,
          question: `${label} @${item.namespace}/${item.slug}？`,
          detail: `${item.scope === 'user' ? '当前用户' : '当前项目'} · ${item.version}${view.candidateVersion ? ` → ${view.candidateVersion}` : ''}${action === 'enable' || action === 'disable' ? ` · ${item.installationState === 'disabled' ? '已停用' : '已启用'}。仅改变这份安装；同名副本可能继续可用，已加载内容不会撤回，旧入口引用可能失效。` : ''}`,
          options: [{ label, description: '仅操作这份安装，其他位置保持不变。' }, { label: '取消', description: '不修改文件。' }], multiSelect: false }] });
      } catch (error) {
        if (error && typeof error === 'object' && Reflect.get(error, 'code') === 'NO_PROVIDER') return view;
        if (error && typeof error === 'object' && Reflect.get(error, 'code') === 'ASK_CANCELLED') return this.confirmManagementForAgent(context.sessionId, confirmationRef, false, signal);
        throw error;
      }
      const choice = answer.answers.find(answer => answer.id === 'skillhub-management');
      return this.confirmManagementForAgent(context.sessionId, confirmationRef,
        !choice?.custom && choice?.selected.length === 1 && choice.selected[0] === label, signal);
    })();
    this.managementRequests.set(key, { parameters, result: work });
    return work;
  }

  @RemoteScope('agent')
  async beginConversationManagement(sourceRevision: string, itemKey: string, action: 'upgrade' | 'uninstall' | 'enable' | 'disable', version: string | null, requestKey: string, signal: AbortSignal): Promise<ConversationManagementResult> {
    if (!['upgrade', 'uninstall', 'enable', 'disable'].includes(action) || !/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(requestKey)) {
      throw new RemoteError('skillhub/invalid-installation-request', 'Invalid management request.', { retryable: false });
    }
    const id = this.ctx.invocation?.request.args.agentId;
    const agent = typeof id === 'string' ? this.ctx.get('agents')?.get(SessionId(id)) : undefined;
    if (!agent?.session.header.cwd) throw new RemoteError('skillhub/context-stale', 'Conversation unavailable.', { retryable: false });
    return await this.manageConversation(action, { sourceRevision, itemKey, ...(version === null ? {} : { version }) },
      { sessionId: agent.session.id, cwd: agent.session.header.cwd, callId: `ui:${requestKey}` }, signal, false) as ConversationManagementResult;
  }

  /** Only a trusted Client event or native userQuestions answer can enter this confirmation path. */
  @RemoteScope('agent')
  async confirmConversationManagement(confirmationRef: string, confirmed: boolean, signal: AbortSignal): Promise<ConversationManagementResult> {
    const id = this.ctx.invocation?.request.args.agentId;
    if (typeof confirmed !== 'boolean') throw new RemoteError('skillhub/invalid-installation-request', 'Invalid confirmation.', { retryable: false });
    return this.confirmManagementForAgent(typeof id === 'string' ? id : '', confirmationRef, confirmed, signal);
  }

  private async confirmManagementForAgent(agentId: string, confirmationRef: string, confirmed: boolean, signal: AbortSignal): Promise<ConversationManagementResult> {
    const entry = this.managementConfirmations.get(confirmationRef);
    const agent = this.ctx.get('agents')?.get(SessionId(agentId));
    if (!entry || !agent || entry.agent !== agent || entry.runtime !== this.localRuntime
      || entry.context.sessionId !== agentId || agent.session.header.cwd !== entry.context.cwd) {
      throw new RemoteError('skillhub/context-stale', 'Confirmation expired.', { retryable: false });
    }
    // Once submitted, retries return the same durable operation, even when the old itemKey has changed.
    if (entry.execution) return entry.execution;
    if (entry.view.status === 'cancelled') return entry.view;
    if (entry.attempted) {
      const previous = await entry.runtime.findPlanOperation({ hostInstanceId: entry.runtime.getStatus().hostInstanceId!,
        ownerId: agentId, requestKey: entry.requestKey, mode: 'retry', parameters: { planId: entry.planId } });
      if (previous) { entry.view = { ...entry.view, status: 'accepted', operation: previous, reason: 'accepted' }; return entry.view; }
    }
    this.assertSource(entry.sourceRevision);
    signal.throwIfAborted();
    if (!confirmed) { entry.view = { ...entry.view, status: 'cancelled', reason: 'cancelled' }; return entry.view; }
    entry.execution = (async () => {
      await this.readInstalledItem(entry.sourceRevision, entry.view.item.itemKey, signal, agentId);
      if (this.localRuntime !== entry.runtime || this.ctx.get('agents')?.get(SessionId(agentId)) !== agent || agent.session.header.cwd !== entry.context.cwd) {
        throw new RemoteError('skillhub/context-stale', 'Conversation changed.', { retryable: false });
      }
      entry.attempted = true;
      const operation = await this.executePlanForAgent(agentId, { planId: entry.planId, requestKey: entry.requestKey }, signal, 'conversation');
      if (!operation) throw new RemoteError('skillhub/installation-unavailable', 'Acceptance unknown. Query the original request.', { retryable: true });
      entry.view = { ...entry.view, status: 'accepted', operation, reason: 'accepted' };
      return entry.view;
    })();
    try { return await entry.execution; }
    catch (error) {
      // A retry uses the same request key and queries the journal before ephemeral plan validation.
      entry.execution = undefined;
      const operation = await entry.runtime.findPlanOperation({ hostInstanceId: entry.runtime.getStatus().hostInstanceId!,
        ownerId: agentId, requestKey: entry.requestKey, mode: 'retry', parameters: { planId: entry.planId } });
      if (operation) { entry.view = { ...entry.view, status: 'accepted', operation, reason: 'accepted' }; entry.execution = Promise.resolve(entry.view); return entry.view; }
      throw error;
    }
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
