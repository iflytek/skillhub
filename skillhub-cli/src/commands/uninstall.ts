import { Command } from "commander";
import { existsSync, readdirSync, statSync, unlinkSync, rmdirSync, lstatSync } from "node:fs";
import { join } from "node:path";
import { homedir } from "node:os";
import { getAllAgents, isUniversalAgent, getUniversalAgents, getNonUniversalAgents, type AgentInfo } from "../core/agent-detector.js";
import { success, info, dim } from "../utils/logger.js";
import { removeFromLock } from "../core/skill-lock.js";
import { searchMultiselect, cancelSymbol } from "../utils/search-multiselect.js";
import * as p from "@clack/prompts";

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
    if (isUniversalAgent(agent)) {
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
    if (isUniversalAgent(agent)) {
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

function discoverInstalledSkills(scope: "local" | "global", agent?: AgentInfo): string[] {
  const skills: string[] = [];
  const agents = agent ? [agent] : getAllAgents();

  for (const a of agents) {
    const skillPath = getSkillPath("*", a, scope);
    if (!skillPath) continue;

    const baseDir = skillPath.replace(/\/[^/]+$/, "");
    try {
      for (const entry of readdirSync(baseDir)) {
        const fullPath = join(baseDir, entry);
        if (statSync(fullPath).isDirectory() && existsSync(join(fullPath, "SKILL.md"))) {
          skills.push(entry);
        }
      }
    } catch {}
  }

  return [...new Set(skills)];
}

function findAgentsWithSkill(skillName: string, scope: "global" | "local", agents: AgentInfo[]): AgentInfo[] {
  return agents.filter((a) => getSkillPath(skillName, a, scope) !== null);
}

export function registerUninstall(program: Command) {
  program
    .command("uninstall [name]")
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

      if (opts.all) {
        const skills = discoverInstalledSkills(scope);

        if (skills.length === 0) {
          dim("No skills installed.");
          return;
        }

        const selected = await searchMultiselect({
          message: "Select skills to uninstall",
          items: skills.map((s) => ({ value: s, label: s })),
          required: true,
        });

        if (selected === cancelSymbol) {
          console.log("Cancelled.");
          return;
        }

        const selectedSkills = selected as string[];
        let uninstalled = 0;

        for (const skill of selectedSkills) {
          const agentsWithSkill = findAgentsWithSkill(skill, scope, allAgents);
          for (const agent of agentsWithSkill) {
            const ok = await uninstallSkill(skill, agent, scope, true);
            if (ok) uninstalled++;
          }
          await removeFromLock(skill);
        }

        success(`Uninstalled ${uninstalled} skill(s).`);
        return;
      }

      if (!name) {
        const skills = discoverInstalledSkills(scope);

        if (skills.length === 0) {
          dim("No skills installed.");
          return;
        }

        const selected = await searchMultiselect({
          message: "Select skills to uninstall",
          items: skills.map((s) => ({ value: s, label: s })),
          required: true,
        });

        if (selected === cancelSymbol) {
          console.log("Cancelled.");
          return;
        }

        const selectedSkills = selected as string[];
        let uninstalled = 0;

        for (const skill of selectedSkills) {
          const agentsWithSkill = findAgentsWithSkill(skill, scope, allAgents);
          for (const agent of agentsWithSkill) {
            const ok = await uninstallSkill(skill, agent, scope, !!opts.yes);
            if (ok) uninstalled++;
          }
          await removeFromLock(skill);
        }

        success(`Uninstalled ${uninstalled} skill(s).`);
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

      const universalAgents = getUniversalAgents();
      const nonUniversalAgents = getNonUniversalAgents();

      const universalSection = {
        title: "Universal (.agents/skills)",
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
        for (const [path, agents] of pathToAgents) {
          if (agents.length > 1) {
            lines.push(`  ${agents.join(", ")} (${path})`);
          } else {
            lines.push(`  ${agents[0]} (${path})`);
          }
        }
        success(`Uninstalled ${name} from ${selectedAgentKeys.length} agent(s):`);
        console.log(lines.join("\n"));
        await removeFromLock(name);
      } else {
        info(`Skill "${name}" not found.`);
      }
    });
}
