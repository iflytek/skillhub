#!/usr/bin/env node
import { cac } from 'cac'
import { doctorCommand } from './commands/doctor'
import { commands, formatCommandList, helpCommand } from './commands/help'
import { installCommand, type InstallCommandOptions } from './commands/install'
import { listCommand, type ListCommandOptions } from './commands/list'
import { loginCommand } from './commands/login'
import { logoutCommand } from './commands/logout'
import { publishCommand, type PublishCommandOptions } from './commands/publish'
import { removeCommand, type RemoveCommandOptions } from './commands/remove'
import { searchCommand } from './commands/search'
import { updateCommand } from './commands/update'
import { versionCommand } from './commands/version'
import { whoamiCommand } from './commands/whoami'
import { CliError } from './shared/errors'
import { renderError } from './shared/output'

const cli = cac('skillhub')

/** Normalize cac's repeatable option: string | string[] | undefined -> string[] | undefined */
function toArray(val: string | string[] | undefined): string[] | undefined {
  if (val === undefined) return undefined
  return Array.isArray(val) ? val : [val]
}

async function runCommand(action: () => Promise<string>, json = false): Promise<void> {
  try {
    const output = await action()
    if (output) {
      process.stdout.write(`${output}\n`)
    }
  } catch (error) {
    const exitCode = error instanceof CliError ? error.exitCode : 1
    process.stderr.write(`${renderError(error, json)}\n`)
    process.exit(exitCode)
  }
}

const KNOWN_COMMANDS = Object.keys(commands)

function levenshteinDistance(left: string, right: string): number {
  const rows = left.length + 1
  const cols = right.length + 1
  const matrix = Array.from({ length: rows }, () => Array<number>(cols).fill(0))

  for (let row = 0; row < rows; row += 1) matrix[row]![0] = row
  for (let col = 0; col < cols; col += 1) matrix[0]![col] = col

  for (let row = 1; row < rows; row += 1) {
    for (let col = 1; col < cols; col += 1) {
      const cost = left[row - 1] === right[col - 1] ? 0 : 1
      matrix[row]![col] = Math.min(
        matrix[row - 1]![col]! + 1,
        matrix[row]![col - 1]! + 1,
        matrix[row - 1]![col - 1]! + cost
      )
    }
  }

  return matrix[left.length]![right.length]!
}

function findCommandSuggestions(input: string): string[] {
  return KNOWN_COMMANDS
    .map(command => ({
      command,
      score: command.startsWith(input)
        ? 0
        : command.includes(input)
          ? 1
          : levenshteinDistance(input, command)
    }))
    .filter(({ command, score }) =>
      command.startsWith(input) ||
      (input.length > 2 && command.includes(input)) ||
      score <= Math.max(2, Math.floor(command.length / 3))
    )
    .sort((left, right) => left.score - right.score || left.command.localeCompare(right.command))
    .map(({ command }) => command)
    .slice(0, 3)
}

function renderCommandDirectory(): string {
  return ['Available commands:', formatCommandList()].join('\n')
}

function exitWithOutput(output: string, exitCode: number): never {
  process.stderr.write(`${output}\n`)
  process.exit(exitCode)
}

function exitWithCliError(error: CliError, json: boolean, humanOutput?: string): never {
  return exitWithOutput(json ? renderError(error, true) : (humanOutput ?? renderError(error, false)), error.exitCode)
}

function exitUnknownCommand(command: string, json: boolean): never {
  const suggestions = findCommandSuggestions(command)
  const lines = [`unknown command "${command}" for "skillhub"`, '']

  if (suggestions.length > 0) {
    lines.push(`Did you mean ${suggestions.length === 1 ? 'this' : 'one of these'}?`)
    lines.push(...suggestions.map(suggestion => `    ${suggestion}`))
    lines.push('')
  }

  lines.push('Usage:  skillhub <command> [flags]', '')
  lines.push(renderCommandDirectory(), '')
  lines.push('Run "skillhub help" for more information.')
  return exitWithCliError(new CliError(`unknown command "${command}" for "skillhub"`, 5), json, lines.join('\n'))
}

function exitUnknownFlag(flag: string, json: boolean): never {
  return exitWithCliError(new CliError(`unknown flag: ${flag}`, 5), json, [
    `unknown flag: ${flag}`,
    '',
    'Usage:  skillhub <command> [flags]',
    '',
    renderCommandDirectory(),
    '',
    'Run "skillhub help" for more information.'
  ].join('\n'))
}

function handleCliParseError(error: unknown, json: boolean): never {
  if (!(error instanceof Error)) {
    return exitWithCliError(new CliError('unexpected failure', 1), json, 'Unexpected error')
  }

  if (error.name === 'CACError') {
    const message = error.message

    if (/unknown option/i.test(message)) {
      const match = message.match(/unknown option ["`]?([^"`]+)["`]?/i)
      return exitUnknownFlag(match?.[1] ?? 'unknown', json)
    }

    if (message.includes('missing required args')) {
      const match = message.match(/command `([^`]+)`/)
      const cmdName = match?.[1] ?? 'command'
      const firstWord = cmdName.split(' ')[0] ?? 'command'

      return exitWithCliError(new CliError('missing required argument', 5), json, [
        'Error: missing required argument',
        '',
        `Usage:  skillhub ${cmdName}`,
        '',
        `Run "skillhub help ${firstWord}" for more information.`
      ].join('\n'))
    }

    const cleanMessage = message.replace(/`/g, '"')
    return exitWithCliError(new CliError(cleanMessage, 5), json)
  }

  return exitWithCliError(new CliError('unexpected failure', 1), json, `Unexpected error: ${error.message}`)
}

function isJsonRequested(argv: string[]): boolean {
  return argv.includes('--json')
}

function readUnknownCommand(argv: string[]): string | undefined {
  const firstArg = argv[0]
  if (!firstArg || firstArg.startsWith('-') || KNOWN_COMMANDS.includes(firstArg)) {
    return undefined
  }
  return firstArg
}

cli
  .command('', 'Show help')
  .action(() => runCommand(() => helpCommand([])))

cli
  .command('help [command]', 'Show help')
  .option('--json', 'Output JSON')
  .action((command: string | undefined, options: { json?: boolean }) => {
    // TODO: --json is not forwarded to helpCommand; see help-command.test.ts
    return runCommand(() => helpCommand(command ? [command] : []), Boolean(options.json))
  })

cli
  .command('version', 'Show CLI version')
  .option('--json', 'Output JSON')
  .action((options: { json?: boolean }) => {
    return runCommand(() => versionCommand(options.json ? ['--json'] : []), Boolean(options.json))
  })

cli
  .command('update', 'Update CLI to latest version')
  .option('--check', 'Check for updates without installing')
  .option('--json', 'Output JSON')
  .action((options: { check?: boolean; json?: boolean }) => {
    return runCommand(() => updateCommand(options), Boolean(options.json))
  })

cli
  .command('login', 'Save registry and token')
  .option('--registry <url>', 'Registry URL')
  .option('--token <token>', 'API token')
  .option('--json', 'Output JSON')
  .action((options: { registry?: string; token?: string; json?: boolean }) => {
    return runCommand(() => loginCommand(options), Boolean(options.json))
  })

cli
  .command('logout', 'Remove local token')
  .option('--registry <url>', 'Registry URL')
  .option('--json', 'Output JSON')
  .action((options: { registry?: string; json?: boolean }) => {
    return runCommand(() => logoutCommand(options), Boolean(options.json))
  })

cli
  .command('whoami', 'Verify current token')
  .option('--registry <url>', 'Registry URL')
  .option('--token <token>', 'API token')
  .option('--json', 'Output JSON')
  .action((options: { registry?: string; token?: string; json?: boolean }) => {
    return runCommand(() => whoamiCommand(options), Boolean(options.json))
  })

cli
  .command('search [query]', 'Search published skills')
  .option('--registry <url>', 'Registry URL')
  .option('--limit <n>', 'Max results', { default: 20 })
  .option('--json', 'Output JSON')
  .action((query: string | undefined, options: { registry?: string; limit?: number; json?: boolean }) => {
    return runCommand(() => searchCommand(query ?? '', options), Boolean(options.json))
  })

cli
  .command('install <slug>', 'Install a skill locally')
  .option('--namespace <slug>', 'Namespace', { default: 'global' })
  .option('--version <v>', 'Version')
  .option('--agent <profile>', 'Agent profile (repeatable)')
  .option('--dir <path>', 'Install directory')
  .option('--force', 'Overwrite existing')
  .option('--registry <url>', 'Registry URL')
  .option('--token <token>', 'API token')
  .option('--json', 'Output JSON')
  .action((slug: string, options: InstallCommandOptions & { agent?: string | string[] }) => {
    return runCommand(() => installCommand(slug, { ...options, agent: toArray(options.agent) }), Boolean(options.json))
  })

cli
  .command('list', 'List local installs')
  .option('--agent <profile>', 'Filter by agent (repeatable)')
  .option('--dir <path>', 'Filter by directory')
  .option('--registry <url>', 'Registry URL')
  .option('--json', 'Output JSON')
  .action((options: ListCommandOptions & { agent?: string | string[] }) => {
    return runCommand(() => listCommand({ ...options, agent: toArray(options.agent) }), Boolean(options.json))
  })

cli
  .command('remove <slug>', 'Remove local or remote skill')
  .option('--agent <profile>', 'Filter by agent (repeatable)')
  .option('--all', 'Remove all targets')
  .option('--remote', 'Delete remote skill')
  .option('--hard', 'Skip confirmation for remote delete')
  .option('--namespace <slug>', 'Namespace for remote delete')
  .option('--registry <url>', 'Registry URL')
  .option('--token <token>', 'API token')
  .option('--json', 'Output JSON')
  .action((slug: string, options: RemoveCommandOptions & { agent?: string | string[] }) => {
    return runCommand(() => removeCommand(slug, { ...options, agent: toArray(options.agent) }), Boolean(options.json))
  })

cli
  .command('doctor', 'Scan project and merge into local inventory')
  .option('--json', 'Output JSON')
  .action((options: { json?: boolean }) => {
    return runCommand(() => doctorCommand(options), Boolean(options.json))
  })

cli
  .command('publish <path>', 'Publish a local skill package')
  .option('--namespace <slug>', 'Namespace')
  .option('--visibility <v>', 'Visibility (public|namespace-only|private)')
  .option('--dry-run', 'Validate without publishing')
  .option('--registry <url>', 'Registry URL')
  .option('--token <token>', 'API token')
  .option('--json', 'Output JSON')
  .action((path: string, options: PublishCommandOptions) => {
    return runCommand(() => publishCommand(path, options), Boolean(options.json))
  })

cli.help()

if (import.meta.main) {
  const args = process.argv.slice(2)
  const json = isJsonRequested(args)
  const unknownCommand = readUnknownCommand(args)
  if (unknownCommand) {
    exitUnknownCommand(unknownCommand, json)
  }
  try {
    cli.parse(process.argv)
  } catch (error) {
    handleCliParseError(error, json)
  }
}
