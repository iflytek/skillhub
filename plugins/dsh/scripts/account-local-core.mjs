import { openNativeRuntime as openRuntime } from '../../../cli/src/integrations/local-skills.ts';
import { registryCredentials } from './core-registry-transport.ts';
export * from '../../../cli/src/integrations/local-skills.ts';

/** Each DSH Host supplies its own account resolver without changing the shared core. */
export async function openNativeRuntime(options) {
  const runtime = await openRuntime(options);
  return new Proxy(runtime, {
    get(target, property) {
      const value = Reflect.get(target, property);
      if (typeof value !== 'function') return value;
      return (...args) => registryCredentials.run(options.getRegistryToken, () => value.apply(target, args));
    },
  });
}
