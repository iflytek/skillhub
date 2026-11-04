// Minimal test-harness surface; production code never imports Node APIs.
// The project intentionally does not depend on the full Node type package.
declare module 'node:fs' {
  export function readFileSync(path: string, encoding: 'utf8'): string
}
