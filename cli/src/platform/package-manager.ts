export type InstallMode = 'npx' | 'npm-global' | 'bun-global' | 'unknown'

/**
 * Detect how the CLI was installed by inspecting process.argv and
 * environment variables.
 *
 * - npx: argv[1] contains `_npx/` or npm_execpath points to npx
 * - npm-global: resolved binary lives under a global npm prefix
 * - bun-global: resolved binary lives under ~/.bun or BUN_INSTALL
 * - unknown: fallback
 */
export function detectInstallMode(
  argv: string[] = process.argv,
  env: NodeJS.ProcessEnv = process.env
): InstallMode {
  const execPath = argv[1] ?? ''

  // npx detection: npx injects `_npx/` into the path
  if (execPath.includes('_npx/') || execPath.includes('_npx\\')) {
    return 'npx'
  }

  // npm_execpath is set when running via npm/npx
  const npmExecPath = env.npm_execpath ?? ''
  if (npmExecPath.includes('npx')) {
    return 'npx'
  }

  // bun global: BUN_INSTALL or ~/.bun in the path
  const bunInstall = env.BUN_INSTALL ?? ''
  if (bunInstall && execPath.startsWith(bunInstall)) {
    return 'bun-global'
  }
  if (execPath.includes('/.bun/') || execPath.includes('\\.bun\\')) {
    return 'bun-global'
  }

  // npm global: check if the binary is in a global npm prefix
  const npmGlobalPrefix = env.npm_config_prefix ?? ''
  if (npmGlobalPrefix && execPath.startsWith(npmGlobalPrefix)) {
    return 'npm-global'
  }
  // Common npm global paths
  if (execPath.includes('/lib/node_modules/') || execPath.includes('/node_modules/.bin/')) {
    return 'npm-global'
  }

  return 'unknown'
}
