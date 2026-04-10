import * as p from "@clack/prompts";
import pc from "picocolors";
import { homedir } from "node:os";
import { sep } from "node:path";

export function riskLabel(risk: string): string {
  switch (risk) {
    case "critical":
      return pc.red(pc.bold("Critical Risk"));
    case "high":
      return pc.red("High Risk");
    case "medium":
      return pc.yellow("Med Risk");
    case "low":
      return pc.green("Low Risk");
    case "safe":
      return pc.green("Safe");
    default:
      return pc.dim("--");
  }
}

export function socketLabel(audit: { alerts?: number } | undefined): string {
  if (!audit) return pc.dim("--");
  const count = audit.alerts ?? 0;
  return count > 0 ? pc.red(`${count} alert${count !== 1 ? "s" : ""}`) : pc.green("0 alerts");
}

export function padEnd(str: string, width: number): string {
  const visible = str.replace(/\x1b\[[0-9;]*m/g, "");
  const pad = Math.max(0, width - visible.length);
  return str + " ".repeat(pad);
}

export interface AuditSkill {
  slug: string;
  displayName: string;
}

export interface AuditData {
  ath?: { risk: string };
  socket?: { alerts?: number };
  snyk?: { risk: string };
}

export type AuditResponse = Record<string, Record<string, AuditData>>;

export function buildSecurityLines(
  auditData: AuditResponse | null,
  skills: AuditSkill[],
  _source: string
): string[] {
  if (!auditData) return [];

  const hasAny = skills.some((s) => {
    const data = auditData[s.slug];
    return data && Object.keys(data).length > 0;
  });
  if (!hasAny) return [];

  const nameWidth = Math.min(Math.max(...skills.map((s) => s.displayName.length)), 36);

  const lines: string[] = [];
  const header =
    padEnd("", nameWidth + 2) +
    padEnd(pc.dim("Gen"), 18) +
    padEnd(pc.dim("Socket"), 18) +
    pc.dim("Snyk");
  lines.push(header);

  for (const skill of skills) {
    const data = auditData[skill.slug];
    const name =
      skill.displayName.length > nameWidth
        ? skill.displayName.slice(0, nameWidth - 1) + "\u2026"
        : skill.displayName;

    const ath = data?.ath ? riskLabel(data.ath.risk) : pc.dim("--");
    const socket = data?.socket ? socketLabel(data.socket) : pc.dim("--");
    const snyk = data?.snyk ? riskLabel(data.snyk.risk) : pc.dim("--");

    lines.push(padEnd(pc.cyan(name), nameWidth + 2) + padEnd(ath, 18) + padEnd(socket, 18) + snyk);
  }

  lines.push("");
  lines.push(`${pc.dim("Details:")} ${pc.dim(`https://skills.sh/${_source}`)}`);

  return lines;
}

export function shortenPath(fullPath: string, cwd: string): string {
  const home = homedir();
  if (fullPath === home || fullPath.startsWith(home + sep)) {
    return "~" + fullPath.slice(home.length);
  }
  if (fullPath === cwd || fullPath.startsWith(cwd + sep)) {
    return "." + fullPath.slice(cwd.length);
  }
  return fullPath;
}

export function formatList(items: string[], maxShow: number = 5): string {
  if (items.length <= maxShow) {
    return items.join(", ");
  }
  const shown = items.slice(0, maxShow);
  const remaining = items.length - maxShow;
  return `${shown.join(", ")} +${remaining} more`;
}

export interface AgentInfo {
  key: string;
  name: string;
  skillsDir: string;
  globalSkillsDir?: string;
}

export function splitAgentsByType(
  agentTypes: string[],
  agents: Record<string, AgentInfo>
): { universal: string[]; symlinked: string[] } {
  const universal: string[] = [];
  const symlinked: string[] = [];

  for (const a of agentTypes) {
    const agent = agents[a];
    if (agent) {
      if (agent.skillsDir === ".agents/skills") {
        universal.push(agent.name);
      } else {
        symlinked.push(agent.name);
      }
    }
  }

  return { universal, symlinked };
}

export function buildAgentSummaryLines(
  targetAgents: string[],
  installMode: string,
  agents: Record<string, AgentInfo>
): string[] {
  const lines: string[] = [];
  const { universal, symlinked } = splitAgentsByType(targetAgents, agents);

  if (installMode === "symlink") {
    if (universal.length > 0) {
      lines.push(`  ${pc.green("universal:")} ${formatList(universal)}`);
    }
    if (symlinked.length > 0) {
      lines.push(`  ${pc.dim("symlink →")} ${formatList(symlinked)}`);
    }
  } else {
    const allNames = targetAgents.map((a) => agents[a]?.name || a);
    lines.push(`  ${pc.dim("copy →")} ${formatList(allNames)}`);
  }

  return lines;
}

export function ensureUniversalAgents(
  targetAgents: string[],
  getUniversalAgentsFn: () => string[]
): string[] {
  const universalAgents = getUniversalAgentsFn();
  const result = [...targetAgents];

  for (const ua of universalAgents) {
    if (!result.includes(ua)) {
      result.push(ua);
    }
  }

  return result;
}

export interface InstallResult {
  agent: string;
  symlinkFailed?: boolean;
}

export function buildResultLines(
  results: InstallResult[],
  targetAgents: string[],
  agents: Record<string, AgentInfo>
): string[] {
  const lines: string[] = [];
  const { universal, symlinked } = splitAgentsByType(targetAgents, agents);

  const successfulSymlinks = results
    .filter((r) => !r.symlinkFailed && !universal.includes(r.agent))
    .map((r) => r.agent);
  const failedSymlinks = results.filter((r) => r.symlinkFailed).map((r) => r.agent);

  if (universal.length > 0) {
    lines.push(`  ${pc.green("universal:")} ${formatList(universal)}`);
  }
  if (successfulSymlinks.length > 0) {
    lines.push(`  ${pc.dim("symlinked:")} ${formatList(successfulSymlinks)}`);
  }
  if (failedSymlinks.length > 0) {
    lines.push(`  ${pc.yellow("copied:")} ${formatList(failedSymlinks)}`);
  }

  return lines;
}

export function isCancelled(value: unknown): value is symbol {
  return typeof value === "symbol";
}

export async function interactiveSelect<T>(opts: {
  message: string;
  options: Array<{ value: T; label: string; hint?: string }>;
}): Promise<T | symbol> {
  const selected = await p.select({
    message: opts.message,
    options: opts.options as p.Option<T>[],
  });
  return selected as T | symbol;
}

export async function interactiveConfirm(message: string): Promise<boolean | symbol> {
  const confirmed = await p.confirm({ message });
  return confirmed as boolean | symbol;
}

export async function interactiveMultiSelect<T>(opts: {
  message: string;
  options: Array<{ value: T; label: string; hint?: string }>;
  initialValues?: T[];
  required?: boolean;
}): Promise<T[] | symbol> {
  return p.multiselect({
    message: `${opts.message} ${pc.dim("(space to toggle)")}`,
    options: opts.options as p.Option<T>[],
    initialValues: opts.initialValues as T[],
    required: opts.required,
  }) as Promise<T[] | symbol>;
}

export function getCanonicalPath(skillName: string, isGlobal: boolean, agents: Record<string, AgentInfo>): string {
  const universalAgents = Object.values(agents).filter((a) => a.skillsDir === ".agents/skills");
  if (universalAgents.length > 0) {
    return `~/.agents/skills/${skillName}`;
  }
  if (isGlobal) {
    const home = homedir();
    return `${home}/.agents/skills/${skillName}`;
  }
  return `.agents/skills/${skillName}`;
}