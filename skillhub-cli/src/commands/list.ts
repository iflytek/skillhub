import { Command } from "commander";
import { existsSync, readdirSync, lstatSync } from "node:fs";
import { join } from "node:path";
import { homedir } from "node:os";
import { getAllAgents, isUniversalForScope } from "../core/agent-detector.js";
import { info, dim } from "../utils/logger.js";
import { searchMultiselect, cancelSymbol } from "../utils/search-multiselect.js";
import * as p from "@clack/prompts";
import pc from "picocolors";

interface ListOptions {
  global?: boolean;
  project?: boolean;
  agent?: string[];
  all?: boolean;
}

export function registerList(program: Command) {
  program
    .command("list")
    .alias("ls")
    .description("List installed skills")
    .option("-g, --global", "List global skills only")
    .option("-p, --project", "List project skills only")
    .option("-a, --all", "List all skills (both global and project)")
    .option("--agent <agents...>", "Filter by specific agents")
    .action(async (opts: ListOptions) => {
      let scopeGlobal: boolean | null = null;

      if (opts.global) {
        scopeGlobal = true;
      } else if (opts.project) {
        scopeGlobal = false;
      } else {
        const scopeSelection = await p.select({
          message: "Which scope to list?",
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
          scopeGlobal = true;
        } else if (scopeSelection === "project") {
          scopeGlobal = false;
        }
      }

      // Determine scope for dynamic universal grouping
      const isGlobal = scopeGlobal === true;
      const allAgents = getAllAgents();

      const universalAgents = allAgents
        .filter((a) => isUniversalForScope(a, isGlobal) && a.showInUniversalList !== false)
        .sort((a, b) => a.name.localeCompare(b.name));
      const nonUniversalAgents = allAgents
        .filter((a) => !isUniversalForScope(a, isGlobal))
        .sort((a, b) => a.name.localeCompare(b.name));

      const canonicalLabel = isGlobal ? "Universal (~/.agents/skills)" : "Universal (.agents/skills)";
      const universalSection = {
        title: canonicalLabel,
        items: universalAgents.map((a) => ({
          value: a.key,
          label: a.name,
        })),
      };

      const selectableItems = nonUniversalAgents.map((a) => ({
        value: a.key,
        label: a.name,
      }));

      const agentSelection = await searchMultiselect({
        message: "Which agents to list from?",
        items: selectableItems,
        lockedSection: universalSection,
      });

      if (agentSelection === cancelSymbol) {
        console.log("Cancelled.");
        return;
      }

      const selectedAgents = agentSelection as string[];
      const agents = allAgents.filter((a) => selectedAgents.includes(a.key));

      if (agents.length === 0) {
        console.log("No agents selected.");
        return;
      }

      console.log("");

      // Collect all skill entries grouped by (skillName, path) -> agentNames
      const skillMap = new Map<string, Map<string, string[]>>();
      const home = homedir();
      const cwd = process.cwd();

      for (const agent of agents) {
        const showProject = scopeGlobal === null || scopeGlobal === false;
        const showGlobal = scopeGlobal === null || scopeGlobal === true;

        if (showProject) {
          const projectDir = join(cwd, agent.skillsDir);
          collectSkills(skillMap, projectDir, agent.name, cwd, true);
        }

        if (showGlobal && agent.globalSkillsDir) {
          const globalDir = join(home, agent.globalSkillsDir);
          collectSkills(skillMap, globalDir, agent.name, home, false);
        }
      }

      if (skillMap.size === 0) {
        dim("No skills installed for selected agents and scope.");
      } else {
        // Output grouped by skill, then by path with agent names merged
        const sortedSkills = [...skillMap.entries()].sort((a, b) => a[0].localeCompare(b[0]));
        for (const [skillName, pathGroups] of sortedSkills) {
          info(`${skillName}`);
          const sortedPaths = [...pathGroups.entries()].sort((a, b) => a[0].localeCompare(b[0]));
          for (const [displayPath, agentNames] of sortedPaths) {
            const sorted = agentNames.sort((a, b) => a.localeCompare(b));
            const label = sorted.length <= 5
              ? sorted.join(", ")
              : sorted.slice(0, 5).join(", ") + ` ${pc.dim(`+${sorted.length - 5}`)}`;
            dim(`  ${pc.dim("→")} ${label}: ${displayPath}`);
          }
        }
      }

      console.log("");
    });
}

/**
 * Collect skills from a directory into the skillMap.
 * skillMap: skillName -> (displayPath -> agentNames[])
 */
function collectSkills(
  skillMap: Map<string, Map<string, string[]>>,
  dir: string,
  agentName: string,
  baseForRelative: string,
  isProject: boolean,
) {
  if (!existsSync(dir)) return;
  const skills = getSkillsInDir(dir);
  for (const skillName of skills) {
    const displayPath = isProject
      ? dir.replace(baseForRelative, ".")
      : dir.replace(baseForRelative, "~");
    let pathGroups = skillMap.get(skillName);
    if (!pathGroups) {
      pathGroups = new Map();
      skillMap.set(skillName, pathGroups);
    }
    const agents = pathGroups.get(displayPath) || [];
    agents.push(agentName);
    pathGroups.set(displayPath, agents);
  }
}

function getSkillsInDir(dir: string): string[] {
  if (!existsSync(dir)) return [];
  return readdirSync(dir).filter((f) => {
    const full = join(dir, f);
    try {
      const stat = lstatSync(full);
      return stat.isDirectory() && existsSync(join(full, "SKILL.md"));
    } catch {
      return false;
    }
  }).sort((a, b) => a.localeCompare(b));
}
