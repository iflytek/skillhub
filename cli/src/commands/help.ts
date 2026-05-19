import { printResult } from '../shared/output'

export const commands = {
  help: {
    summary: 'Show available commands',
    usage: 'skillhub help [command] [--json]',
    examples: ['skillhub help', 'skillhub help install', 'skillhub help --json']
  },
  version: {
    summary: 'Show installed CLI version',
    usage: 'skillhub version [--json]',
    examples: ['skillhub version', 'skillhub version --json']
  },
  login: {
    summary: 'Save registry and token',
    usage: 'skillhub login [--token <token>] [--registry <url>] [--json]',
    examples: ['skillhub login --token sk_xxx', 'skillhub login --registry https://skillhub.example.com']
  },
  logout: {
    summary: 'Remove local token',
    usage: 'skillhub logout [--registry <url>] [--json]',
    examples: ['skillhub logout']
  },
  whoami: {
    summary: 'Verify current token',
    usage: 'skillhub whoami [--token <token>] [--registry <url>] [--json]',
    examples: ['skillhub whoami', 'skillhub whoami --json']
  },
  search: {
    summary: 'Search published skills',
    usage: 'skillhub search [query] [--limit <n>] [--registry <url>] [--json]',
    examples: ['skillhub search', 'skillhub search pdf']
  },
  install: {
    summary: 'Install a skill locally',
    usage: 'skillhub install <slug> [--namespace <slug>] [--version <v>] [--agent <profile>] [--dir <path>] [--force] [--json]',
    examples: ['skillhub install pdf-parser', 'skillhub install pdf-parser --agent codex']
  },
  list: {
    summary: 'List local installs',
    usage: 'skillhub list [--agent <profile>] [--dir <path>] [--registry <url>] [--json]',
    examples: ['skillhub list', 'skillhub list --agent codex']
  },
  remove: {
    summary: 'Remove local or remote skill',
    usage: 'skillhub remove <slug> [--agent <profile>] [--all] [--remote] [--hard] [--namespace <slug>] [--json]',
    examples: ['skillhub remove pdf-parser', 'skillhub remove pdf-parser --remote --hard']
  },
  doctor: {
    summary: 'Scan project and merge into local inventory (preserves entries outside scan scope)',
    usage: 'skillhub doctor [--json]',
    examples: ['skillhub doctor', 'skillhub doctor --json']
  },
  publish: {
    summary: 'Publish a local skill package',
    usage: 'skillhub publish <path> [--namespace <slug>] [--visibility <public|namespace-only|private>] [--registry <url>] [--json]',
    examples: ['skillhub publish ./my-skill', 'skillhub publish ./my-skill --namespace myspace']
  },
  update: {
    summary: 'Check or update CLI itself',
    usage: 'skillhub update [--check] [--json]',
    examples: ['skillhub update --check', 'skillhub update']
  }
} as const

export function formatCommandList(): string {
  return Object.entries(commands).map(([name, detail]) => `${name.padEnd(10)} ${detail.summary}`).join('\n')
}

export async function helpCommand(args: string[]): Promise<string> {
  const json = args.includes('--json')
  const topic = args.find(arg => !arg.startsWith('--'))
  if (json) {
    if (topic) {
      // TODO: unknown topic returns undefined and crashes on detail.usage; see help-command.test.ts
      const detail = commands[topic as keyof typeof commands]
      return printResult({ ok: true, command: topic, ...detail }, true)
    }
    return printResult({
      ok: true,
      commands: Object.entries(commands).map(([name, detail]) => ({ name, description: detail.summary }))
    }, true)
  }
  if (topic) {
    const detail = commands[topic as keyof typeof commands]
    return [
      `${topic} - ${detail.summary}`,
      `Usage: ${detail.usage}`,
      'Examples:',
      ...detail.examples.map(example => `  ${example}`)
    ].join('\n')
  }
  return formatCommandList()
}
