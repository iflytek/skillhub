/** A monotonic read context prevents A → B → A from reviving earlier scans. */
export function installedContext(read: () => { scope: unknown; cwd: string | undefined; active: boolean }, subscribe: (listener: () => void) => () => void) {
  let previous = read();
  let generation = 0;
  const getSnapshot = () => {
    const next = read();
    if (next.scope !== previous.scope || next.cwd !== previous.cwd || next.active !== previous.active) {
      previous = next; generation++;
    }
    return generation;
  };
  return { getSnapshot, subscribe: (listener: () => void) => subscribe(() => { getSnapshot(); listener(); }) };
}
