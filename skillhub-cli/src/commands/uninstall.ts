import { Command } from "commander";
import { existsSync, readdirSync, statSync, unlinkSync, rmdirSync, lstatSync } from "node:fs";
import { join } from "node:path";
import { homedir } from "node:os";
import { getAllAgents, isUniversalForScope, type AgentInfo } from "../core/agent-detector.js";
import { success, info, dim } from "../utils/logger.js";
import { removeFromLock } from "../core/skill-lock.js";
import { discoverInstalledSkills as discoverSkillsWithStatus, type DiscoveredSkill } from "../core/skill-status.js";
import { searchMultiselect, cancelSymbol } from "../utils/search-multiselect.js";
import * as p from "@clack/prompts";
import pc from "picocolors";

function removeDir(path: string) {
  try {
    const stat = lstatSync(path);
    if (stat.isSymbolicLink()) {
      unlinkSync(path);
    } else if (stat.isDirectory()) {
      for (const entry of readdirSync(path)) {
        removeDir(join(path, entry));
      }
      rmdirSync(path);
    } else {
      unlinkSync(path);
    }
  } catch {}
}

async function uninstallSkill(
  name: string,
  agent: AgentInfo,
  scope: "local" | "global",
  yes: boolean
): Promise<boolean> {
  const home = homedir();
  let baseDir: string;

  if (scope === "global") {
    if (isUniversalForScope(agent, true)) {
      baseDir = join(home, ".agents/skills");
    } else {
      baseDir = join(home, agent.globalSkillsDir || agent.skillsDir);
    }
  } else {
    baseDir = join(process.cwd(), agent.skillsDir);
  }

  const skillPath = join(baseDir, name);

  if (!existsSync(skillPath)) return false;
  if (!statSync(skillPath).isDirectory()) return false;

  if (!yes) {
    const confirmed = await p.confirm({
      message: `Uninstall ${name} from ${agent.name}?`,
      initialValue: false,
    });
    if (!confirmed) return false;
  }

  removeDir(skillPath);
  return true;
}

function getSkillPath(skillName: string, agent: AgentInfo, scope: "global" | "local"): string | null {
  const home = homedir();
  let baseDir: string;

  if (scope === "global") {
    if (isUniversalForScope(agent, true)) {
      baseDir = join(home, ".agents/skills");
    } else {
      baseDir = join(home, agent.globalSkillsDir || agent.skillsDir);
    }
  } else {
    baseDir = join(process.cwd(), agent.skillsDir);
  }

  const skillPath = join(baseDir, skillName);
  if (existsSync(skillPath) && statSync(skillPath).isDirectory()) {
    return skillPath;
  }
  return null;
}

function findAgentsWithSkill(skillName: string, scope: "global" | "local", agents: AgentInfo[]): AgentInfo[] {
  return agents.filter((a) => getSkillPath(skillName, a, scope) !== null);
}

export function registerUninstall(program: Command) {
  program
    .command("uninstall [skill]")
    .alias("un")
    .description("Uninstall a skill or all skills from local agent")
    .option("-g, --global", "Uninstall from global scope")
    .option("-a, --agent <agents...>", "Uninstall from specific agents")
    .option("-y, --yes", "Skip confirmation")
    .option("--all", "Uninstall all installed skills")
    .action(async (name: string | undefined, opts: { global?: boolean; agent?: string[]; yes?: boolean; all?: boolean }) => {
      let scope: "global" | "local" = opts.global ? "global" : "local";
      let scopeAll = false;

      if (!opts.global && !opts.agent) {
        const scopeSelection = await p.select({
          message: "Which scope to uninstall from?",
          options: [
            { value: "all", label: "All (global + project)" },
            { value: "global", label: "Global only" },
            { value: "project", label: "Project only" },
          ],
        });

        if (p.isCancel(scopeSelection)) {
          console.log("Cancelled.");
          return;
        }

        if (scopeSelection === "global") {
          scope = "global";
        } else if (scopeSelection === "project") {
          scope = "local";
        } else if (scopeSelection === "all") {
          scopeAll = true;
        }
      }

      const allAgents = getAllAgents();
      const isGlobal = scope === "global";
      const searchScopes = scopeAll ? ["local", "global"] : [scope];

      const discoveredSkills = await discoverSkillsWithStatus(searchScopes);
      const installedSkills = discoveredSkills.filter(
        (s) => s.status === "managed" || s.status === "orphaned"
      );

      if (opts.all) {
        if (installedSkills.length === 0) {
          dim("No skills installed.");
          return;
        }

        const selected = await searchMultiselect({
          message: "Select skills to uninstall",
          items: installedSkills.map((s) => ({
            value: s.name,
            label: s.status === "orphaned" ? `${s.name} [orphaned]` : s.name,
          })),
          required: true,
        });

        if (selected === cancelSymbol) {
          console.log("Cancelled.");
          return;
        }

        const selectedSkills = selected as string[];
        const results: { skill: string; agent: string; path: string; ok: boolean }[] = [];

        for (const skillName of selectedSkills) {
          for (const searchScope of searchScopes) {
            const agentsWithSkill = findAgentsWithSkill(skillName, searchScope as "global" | "local", allAgents);
            for (const agent of agentsWithSkill) {
              const ok = await uninstallSkill(skillName, agent, searchScope as "global" | "local", true);
              const skillPath = getSkillPath(skillName, agent, searchScope as "global" | "local");
              results.push({ skill: skillName, agent: agent.name, path: skillPath || "", ok });
            }
          }
          await removeFromLock(skillName);
        }

        printUninstallResults(results);
        return;
      }

      if (!name) {
        if (installedSkills.length === 0) {
          dim("No skills installed.");
          return;
        }

        const selected = await searchMultiselect({
          message: "Select skills to uninstall",
          items: installedSkills.map((s) => ({
            value: s.name,
            label: s.status === "orphaned" ? `${s.name} [orphaned]` : s.name,
          })),
          required: true,
        });

        if (selected === cancelSymbol) {
          console.log("Cancelled.");
          return;
        }

        const selectedSkills = selected as string[];
        const results: { skill: string; agent: string; path: string; ok: boolean }[] = [];

        for (const skillName of selectedSkills) {
          for (const searchScope of searchScopes) {
            const agentsWithSkill = findAgentsWithSkill(skillName, searchScope as "global" | "local", allAgents);
            for (const agent of agentsWithSkill) {
              const ok = await uninstallSkill(skillName, agent, searchScope as "global" | "local", !!opts.yes);
              const skillPath = getSkillPath(skillName, agent, searchScope as "global" | "local");
              results.push({ skill: skillName, agent: agent.name, path: skillPath || "", ok });
            }
          }
          await removeFromLock(skillName);
        }

        printUninstallResults(results);
        return;
      }

      let agentsWithSkill = findAgentsWithSkill(name, scope, allAgents);

      if (agentsWithSkill.length === 0 && !scopeAll) {
        agentsWithSkill = findAgentsWithSkill(name, scope === "global" ? "local" : "global", allAgents);
        if (agentsWithSkill.length > 0) {
          const otherScope = scope === "global" ? "project" : "global";
          dim(`Skill "${name}" not found in ${scope}, but found in ${otherScope}.`);
        } else {
          info(`Skill "${name}" not found.`);
          return;
        }
      }

      if (agentsWithSkill.length === 0 && scopeAll) {
        agentsWithSkill = [
          ...findAgentsWithSkill(name, "global", allAgents),
          ...findAgentsWithSkill(name, "local", allAgents),
        ];
        if (agentsWithSkill.length === 0) {
          info(`Skill "${name}" not found.`);
          return;
        }
      }

      // Dynamic universal grouping based on scope
      const universalAgents = allAgents
        .filter((a) => isUniversalForScope(a, isGlobal) && a.showInUniversalList !== false)
        .sort((a, b) => a.name.localeCompare(b.name));
      const nonUniversalAgents = allAgents
        .filter((a) => !isUniversalForScope(a, isGlobal))
        .sort((a, b) => a.name.localeCompare(b.name));

      const canonicalLabel = isGlobal ? "Universal (~/.agents/skills)" : "Universal (.agents/skills)";
      const universalSection = {
        title: canonicalLabel,
        items: universalAgents
          .filter((a) => agentsWithSkill.some((w) => w.key === a.key))
          .map((a) => ({
            value: a.key,
            label: a.name,
          })),
      };

      const selectableItems = nonUniversalAgents
        .filter((a) => agentsWithSkill.some((w) => w.key === a.key))
        .map((a) => ({
          value: a.key,
          label: a.name,
        }));

      if (selectableItems.length === 0 && universalSection.items.length === 0) {
        info(`Skill "${name}" not found.`);
        return;
      }

      const selected = await searchMultiselect({
        message: `Uninstall ${name} from which agents?`,
        items: selectableItems,
        lockedSection: universalSection.items.length > 0 ? universalSection : undefined,
      });

      if (selected === cancelSymbol) {
        console.log("Cancelled.");
        return;
      }

      const selectedAgentKeys = selected as string[];
      const pathToAgents = new Map<string, string[]>();

      for (const agentKey of selectedAgentKeys) {
        const agent = allAgents.find((a) => a.key === agentKey);
        if (agent) {
          if (scopeAll) {
            const okGlobal = await uninstallSkill(name, agent, "global", !!opts.yes);
            const okLocal = await uninstallSkill(name, agent, "local", !!opts.yes);
            if (okGlobal) {
              const skillPath = getSkillPath(name, agent, "global");
              if (skillPath) {
                const agents = pathToAgents.get(skillPath) || [];
                agents.push(agent.name);
                pathToAgents.set(skillPath, agents);
              }
            }
            if (okLocal) {
              const skillPath = getSkillPath(name, agent, "local");
              if (skillPath) {
                const agents = pathToAgents.get(skillPath) || [];
                agents.push(agent.name);
                pathToAgents.set(skillPath, agents);
              }
            }
          } else {
            const ok = await uninstallSkill(name, agent, scope, !!opts.yes);
            if (ok) {
              const skillPath = getSkillPath(name, agent, scope);
              if (skillPath) {
                const agents = pathToAgents.get(skillPath) || [];
                agents.push(agent.name);
                pathToAgents.set(skillPath, agents);
              }
            }
          }
        }
      }

      if (pathToAgents.size > 0) {
        const lines: string[] = [];
        const sortedEntries = [...pathToAgents.entries()].sort((a, b) => a[0].localeCompare(b[0]));
        for (const [path, agents] of sortedEntries) {
          const sorted = agents.sort((a, b) => a.localeCompare(b));
          const label = sorted.length <= 5
            ? sorted.join(", ")
            : sorted.slice(0, 5).join(", ") + ` ${pc.dim(`+${sorted.length - 5}`)}`;
          lines.push(`  ${pc.dim("→")} ${label}: ${pc.dim(path)}`);
        }
        success(`Uninstalled ${name} from ${selectedAgentKeys.length} agent(s):`);
        console.log(lines.join("\n"));
        await removeFromLock(name);
      } else {
        info(`Skill "${name}" not found.`);
      }
    });
}

/**
 * Print uninstall results grouped by skill and path (consistent with install output format).
 */
function printUninstallResults(results: { skill: string; agent: string; path: string; ok: boolean }[]) {
  const successful = results.filter((r) => r.ok);
  if (successful.length === 0) {
    info("No skills were uninstalled.");
    return;
  }

  // Group by skill
  const skillGroups = new Map<string, { agent: string; path: string }[]>();
  for (const r of successful) {
    let group = skillGroups.get(r.skill);
    if (!group) {
      group = [];
      skillGroups.set(r.skill, group);
    }
    group.push({ agent: r.agent, path: r.path });
  }

  const lines: string[] = [];
  const sortedSkills = [...skillGroups.entries()].sort((a, b) => a[0].localeCompare(b[0]));

  for (const [skillName, entries] of sortedSkills) {
    lines.push(`${pc.green("✓")} ${skillName}`);

    // Group by path
    const pathGroups = new Map<string, string[]>();
    for (const e of entries) {
      const agents = pathGroups.get(e.path) || [];
      agents.push(e.agent);
      pathGroups.set(e.path, agents);
    }

    const sortedPaths = [...pathGroups.entries()].sort((a, b) => a[0].localeCompare(b[0]));
    for (const [path, agents] of sortedPaths) {
      const sorted = agents.sort((a, b) => a.localeCompare(b));
      const label = sorted.length <= 5
        ? sorted.join(", ")
        : sorted.slice(0, 5).join(", ") + ` ${pc.dim(`+${sorted.length - 5}`)}`;
      lines.push(`  ${pc.dim("→")} ${label}: ${pc.dim(path)}`);
    }
  }

  p.note(lines.join("\n"), `Uninstalled ${successful.length} skill(s)`);
}
