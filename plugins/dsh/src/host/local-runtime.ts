import { operationDiagnostic, type OperationDiagnostic } from './operation-diagnostic.js';
import { canonicalizeWatchPath, resolveDshHome } from '@deepseek-ai/dsh-home-paths';
import { assertHostDependencies } from './compatibility.js';
import type { UpdateIdentity, UpdateAssessment, InstalledProjection, InstalledProjectionTarget } from './generated/native-contract.js';
import { homedir } from 'node:os';
import type { UpgradeSelection, UpgradeBaseline } from './generated/native-contract.js';
import type { NativeUpgradeExecution } from './generated/native-contract.js';
import type { RemovalBaseline, NativeRemovalExecution } from './generated/native-contract.js';
import { join } from 'node:path';
import type { NativeCore, NativeRuntime, NativeInstallExecution, NativeInstallAcceptance, NativeOperationRequest,
  NativeOperation, NativeRecoveryResult, NativePendingOperation } from './generated/native-contract.js';

export interface LocalRuntimeStatus {
  state: 'starting' | 'ready' | 'failed' | 'closing' | 'disposed';
  hostInstanceId: string | null;
  pendingOperations: number;
}

export interface PreparedNativeInstall {
  acceptance: NativeInstallAcceptance;
  execution: Omit<NativeInstallExecution, 'operationId' | 'ownerId'>;
}

export interface PreparedNativeUpgrade {
  acceptance: NativeInstallAcceptance;
  execution: Omit<NativeUpgradeExecution, 'operationId' | 'ownerId'>;
}

export interface PreparedNativeRemoval {
  acceptance: NativeInstallAcceptance & { kind: 'uninstall' | 'remove-stale-record' };
  execution: Omit<NativeRemovalExecution, 'operationId' | 'ownerId'>;
}

/** Host-only initialization; no Client path input and no automatic replay of historical writes. */
export class LocalRuntime {
  private disposed = false;
  private runtime?: NativeRuntime;
  private starting?: Promise<void>;
  private closing?: Promise<void>;
  private readonly pending = new Set<Promise<unknown>>();
  private readonly startupPending = new Set<string>();
  private status: LocalRuntimeStatus = { state: 'starting', hostInstanceId: null, pendingOperations: 0 };

  constructor(private readonly open: () => Promise<NativeRuntime> = openLocalRuntime,
    private readonly assertWritableHost: () => void = assertHostDependencies,
    private readonly reportFailure: (diagnostic: OperationDiagnostic) => void = diagnostic => {
      console.warn('SkillHub accepted operation failed', diagnostic);
    }) {}

  start(): Promise<void> {
    if (this.disposed) return Promise.resolve();
    return this.starting ??= this.track(this.initialize());
  }

  private async initialize(): Promise<void> {
    try {
      const runtime = await this.open();
      if (this.disposed) return;
      const pending = await runtime.listPending();
      if (!this.disposed) {
        for (const operation of pending) this.startupPending.add(operation.operationId);
        this.runtime = runtime;
        this.status = { state: 'ready', hostInstanceId: runtime.hostInstanceId, pendingOperations: pending.length };
      }
    } catch {
      if (!this.disposed) this.status = { state: 'failed', hostInstanceId: null, pendingOperations: 0 };
    }
  }

  getStatus(): LocalRuntimeStatus { return { ...this.status }; }

  acceptInstall(request: NativeOperationRequest, validateNew: () => Promise<NativeInstallAcceptance>): Promise<NativeOperation | undefined> {
    return this.call(runtime => runtime.acceptInstall(request, validateNew));
  }

  getOperation(operationId: string, ownerId: string): Promise<NativeOperation | undefined> {
    return this.call(async runtime => {
      const operation = await runtime.getOperation(operationId, ownerId);
      // A prior process's unfinished journal is not evidence that work is still running.
      // Project uncertainty only: recovery retains its execution lease and all Host checks.
      if (operation && this.startupPending.has(operationId)
        && ['accepted', 'staging', 'revalidating', 'committing', 'recovering'].includes(operation.phase)) {
        return { ...operation, phase: 'unknown', files: 'unknown', recovery: 'unknown' };
      }
      return operation;
    });
  }

  observeInstallation(operationId: string, ownerId: string, roots: readonly string[],
    observe: (paths: readonly string[]) => Promise<boolean>): Promise<boolean> {
    return this.call(runtime => runtime.observeInstallation(operationId, ownerId, roots, observe));
  }

  findPlanOperation(request: NativeOperationRequest): Promise<NativeOperation | undefined> {
    const input = { ...request, parameters: { ...request.parameters } };
    return this.call(runtime => runtime.findPlanOperation(input));
  }

  assessUpdate(installed: UpdateIdentity, candidate: UpdateIdentity): Promise<UpdateAssessment> {
    const local = { ...installed }; const remote = { ...candidate };
    return this.call(async runtime => runtime.assessUpdate(local, remote));
  }

  inspectUpgrade(selected: UpgradeSelection, targets: readonly InstalledProjectionTarget[]): Promise<UpgradeBaseline> {
    const identity = { ...selected }; const roots = targets.map(target => ({ ...target }));
    return this.call(runtime => runtime.inspectUpgrade(identity, roots));
  }

  inspectRemoval(selected: UpgradeSelection, target: InstalledProjectionTarget): Promise<RemovalBaseline> {
    const identity = { ...selected }; const root = { ...target };
    return this.call(runtime => runtime.inspectRemoval(identity, root));
  }

  listInstalled(registry: string, targets: readonly InstalledProjectionTarget[]): Promise<InstalledProjection> {
    const roots = targets.map(target => ({ ...target }));
    return this.call(runtime => runtime.listInstalled(registry, roots));
  }

  executeInstall(request: NativeInstallExecution): Promise<NativeRecoveryResult> {
    return this.call(runtime => runtime.executeInstall(request));
  }

  /** Host resolves historical targets from trusted context; neither Client nor model supplies paths. */
  recoverOperation(operationId: string, ownerId: string,
    prepare: (operation: NativePendingOperation) => Promise<Parameters<NativeRuntime['recover']>[3]>): Promise<NativeRecoveryResult | undefined> {
    return this.call(async runtime => {
      const pending = await runtime.listPending();
      if (!this.disposed) this.status = { ...this.status, pendingOperations: pending.length };
      const operation = pending.find(item => item.operationId === operationId && item.ownerId === ownerId);
      if (!operation) return undefined;
      this.assertWritableHost();
      const authorize = await prepare(structuredClone(operation));
      try {
        return await runtime.recover(operationId, ownerId, [...operation.targetPaths], authorize);
      } finally {
        try {
          const remaining = await runtime.listPending();
          if (!this.disposed) this.status = { ...this.status, pendingOperations: remaining.length };
        } catch {
          if (!this.disposed) this.status = { ...this.status, state: 'failed' };
        }
      }
    });
  }

  /** Acceptance is returned early; the complete admitted workflow remains tracked until execution settles. */
  submitInstall(request: NativeOperationRequest, prepare: () => Promise<PreparedNativeInstall>): Promise<NativeOperation | undefined> {
    const receipt = Promise.withResolvers<NativeOperation | undefined>();
    const input = { ...request, parameters: { ...request.parameters } };
    const workflow = this.call(async runtime => {
      let prepared: PreparedNativeInstall | undefined;
      const accepted = await runtime.acceptInstall(input, async () => {
        this.assertWritableHost();
        const result = await prepare();
        prepared = { acceptance: structuredClone(result.acceptance), execution: { ...result.execution } };
        return prepared.acceptance;
      });
      receipt.resolve(accepted);
      // A retry found in durable state does not replay work or reinterpret expired references.
      if (!accepted || !prepared || accepted.phase !== 'accepted') return;
      const execution = { ...prepared.execution, operationId: accepted.operationId, ownerId: input.ownerId };
      await this.executeAccepted(accepted, () => runtime.executeInstall(execution));
    });
    // Before acceptance this rejects the caller. After acceptance, the durable operation owns the outcome.
    void workflow.catch(error => receipt.reject(error));
    return receipt.promise;
  }

  private call<T>(action: (runtime: NativeRuntime) => Promise<T>): Promise<T> {
    const runtime = this.runtime;
    if (this.disposed || !runtime || this.status.state !== 'ready') return Promise.reject(new Error('Local installation service is unavailable'));
    return this.track(Promise.resolve().then(() => action(runtime)));
  }

  submitUpgrade(request: NativeOperationRequest, prepare: () => Promise<PreparedNativeUpgrade>): Promise<NativeOperation | undefined> {
    const receipt = Promise.withResolvers<NativeOperation | undefined>();
    const input = { ...request, parameters: { ...request.parameters } };
    const workflow = this.call(async runtime => {
      let prepared: PreparedNativeUpgrade | undefined;
      const accepted = await runtime.acceptUpgrade(input, async () => {
        this.assertWritableHost();
        const result = await prepare();
        prepared = { acceptance: structuredClone(result.acceptance),
          execution: { ...result.execution, baseline: structuredClone(result.execution.baseline) } };
        return prepared.acceptance;
      });
      receipt.resolve(accepted);
      if (!accepted || !prepared || accepted.phase !== 'accepted') return;
      const execution = { ...prepared.execution, operationId: accepted.operationId, ownerId: input.ownerId };
      await this.executeAccepted(accepted, () => runtime.executeUpgrade(execution));
    });
    void workflow.catch(error => receipt.reject(error));
    return receipt.promise;
  }

  submitRemoval(request: NativeOperationRequest, prepare: () => Promise<PreparedNativeRemoval>): Promise<NativeOperation | undefined> {
    const receipt = Promise.withResolvers<NativeOperation | undefined>();
    const input = { ...request, parameters: { ...request.parameters } };
    const workflow = this.call(async runtime => {
      let prepared: PreparedNativeRemoval | undefined;
      const accepted = await runtime.acceptRemoval(input, async () => {
        this.assertWritableHost();
        const result = await prepare();
        prepared = { acceptance: structuredClone(result.acceptance),
          execution: { ...result.execution, baseline: structuredClone(result.execution.baseline) } };
        return prepared.acceptance;
      });
      receipt.resolve(accepted);
      if (!accepted || !prepared || accepted.phase !== 'accepted') return;
      const execution = { ...prepared.execution, operationId: accepted.operationId, ownerId: input.ownerId };
      await this.executeAccepted(accepted, () => runtime.executeRemoval(execution));
    });
    void workflow.catch(error => receipt.reject(error));
    return receipt.promise;
  }

  private async executeAccepted<T>(operation: NativeOperation, execute: () => Promise<T>): Promise<T> {
    try { return await execute(); }
    catch (error) {
      try { this.reportFailure(operationDiagnostic(operation, error)); }
      catch { /* A diagnostic sink must not replace the original execution failure. */ }
      throw error;
    }
  }

  private track<T>(promise: Promise<T>): Promise<T> {
    this.pending.add(promise);
    void promise.then(() => this.pending.delete(promise), () => this.pending.delete(promise));
    return promise;
  }

  dispose(): Promise<void> {
    if (this.closing) return this.closing;
    this.disposed = true;
    this.status = { ...this.status, state: 'closing' };
    return this.closing = Promise.allSettled([...this.pending]).then(() => {
      this.runtime = undefined;
      this.status = { state: 'disposed', hostInstanceId: null, pendingOperations: 0 };
    });
  }
}

async function openLocalRuntime(): Promise<NativeRuntime> {
  // Resolves beside the compiled Host entry, including when installed from a tarball.
  const core: NativeCore = await import(new URL('./local-core.js', import.meta.url).href);
  if (typeof core.openNativeRuntime !== 'function') throw new Error('Native core is missing');
  const home = await canonicalizeWatchPath(resolveDshHome());
  return core.openNativeRuntime({ stateDirectory: join(home, 'skillhub/operations'), inventoryHome: homedir() });
}
