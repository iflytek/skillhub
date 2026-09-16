import { printResult } from '../shared/output'
import { EXIT } from '../shared/constants'
import { CliError } from '../shared/errors'

export const commands = {
  help: {
    summary: 'Show available commands',
    usage: 'skillhub help [command] [--json]',
    examples: ['skillhub help', 'skillhub help install', 'skillhub help --json']
  },
  version: {
    summary: 'Show installed CLI version',
    usage: 'skillhub version [--json]',
    examples: ['skillhub version', 'skillhub version --json', 'skillhub --version', 'skillhub -v']
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
    usage: 'skillhub search [query] [--limit <n>] [--registry <url>] [--token <token>] [--json]',
    examples: ['skillhub search', 'skillhub search pdf', 'skillhub search pdf --token sk_xxx']
  },
  install: {
    summary: 'Install a skill locally',
    usage: 'skillhub install <coordinate> [--scope <user|project>] [--namespace <slug>] [--version <v>] [--agent <profile>] [--dir <path>] [--force] [--registry <url>] [--token <token>] [--json]',
    examples: [
      'skillhub install pdf-parser',
      'skillhub install team/my-skill',
      'skillhub install @team/my-skill',
      'skillhub install team--my-skill',
      'skillhub install pdf-parser --scope user',
      'skillhub install pdf-parser --scope project --agent codex'
    ]
  },
  suite: {
    summary: 'Manage Skill Suites on compatible registries',
    usage: 'skillhub suite <install|check|upgrade|remove> <coordinate> [options]',
    examples: [
      'skillhub suite install @global/marketing --scope user',
      'skillhub suite check @global/marketing',
      'skillhub suite upgrade @global/marketing --check',
      'skillhub suite remove @global/marketing'
    ]
  },
  upgrade: {
    summary: 'Upgrade explicitly selected installed skills',
    usage: 'skillhub upgrade <coordinate...> [--namespace <slug>] [--agent <profile>] [--dir <path>] [--registry <url>] [--token <token>] [--check] [--force] [--json]',
    examples: [
      'skillhub upgrade @global/skillhub-cli',
      'skillhub upgrade @team/code-review @team/java-guide --check --json',
      'skillhub upgrade code-review --namespace team --agent codex'
    ]
  },
  sync: {
    summary: 'Synchronize and maintain namespace workspaces',
    usage: 'skillhub sync pull --namespace <slug> [--skill <slug>] [options] | skillhub sync <status|diff|push> --namespace <slug> [options]',
    examples: [
      'skillhub sync pull --namespace team-a --skill code-review',
      'skillhub sync status --namespace team-a --json',
      'skillhub sync push --all --namespace team-a --submit-review'
    ]
  },
  list: {
    summary: 'List local installs',
    usage: 'skillhub list [--agent <profile>] [--dir <path>] [--registry <url>] [--json]',
    examples: ['skillhub list', 'skillhub list --agent codex']
  },
  remove: {
    summary: 'Remove local or remote skill',
    usage: 'skillhub remove <coordinate> [--agent <profile>] [--all] [--remote] [--hard] [--namespace <slug>] [--registry <url>] [--token <token>] [--json]',
    examples: [
      'skillhub remove pdf-parser',
      'skillhub remove team/my-skill',
      'skillhub remove my-skill --namespace team',
      'skillhub remove pdf-parser --remote --hard'
    ]
  },
  doctor: {
    summary: 'Scan project and merge into local inventory (preserves entries outside scan scope)',
    usage: 'skillhub doctor [--json]',
    examples: ['skillhub doctor', 'skillhub doctor --json']
  },
  publish: {
    summary: 'Publish a local skill package',
    usage: 'skillhub publish <path> [--namespace <slug>] [--visibility <public|namespace-only|private>] [--dry-run] [--registry <url>] [--token <token>] [--json]',
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
  const detail = topic ? commands[topic as keyof typeof commands] : undefined
  if (topic && !detail) {
    throw new CliError(`unknown help topic: ${topic}`, EXIT.usage, {
      topic,
      next: 'run `skillhub help` to list available commands'
    })
  }
  if (json) {
    if (topic && detail) {
      return printResult({ ok: true, command: topic, ...detail }, true)
    }
    return printResult({
      ok: true,
      commands: Object.entries(commands).map(([name, detail]) => ({ name, description: detail.summary }))
    }, true)
  }
  if (topic && detail) {
    return [
      `${topic} - ${detail.summary}`,
      `Usage: ${detail.usage}`,
      'Examples:',
      ...detail.examples.map(example => `  ${example}`)
    ].join('\n')
  }
  return formatCommandList()
}
