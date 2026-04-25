import { existsSync, readdirSync, statSync } from "node:fs";
import { join } from "node:path";
import { homedir } from "node:os";
import { getAllAgents, type AgentInfo } from "./agent-detector.js";
import { getAllLockedSkills } from "./skill-lock.js";

export interface SkillLocation {
  agent: string;
  path: string;
  scope: "local" | "global";
}

export interface DiscoveredSkill {
  name: string;
  status: "managed" | "orphaned" | "missing";
  source?: string;
  locations: SkillLocation[];
}

function findInstalledSkills(
  scope: "local" | "global",
  agents?: AgentInfo[]
): Map<string, SkillLocation[]> {
  const skillsMap = new Map<string, SkillLocation[]>();
  const allAgents = getAllAgents();
  const targetAgents = agents || allAgents;

  for (const agent of targetAgents) {
    const baseDir = scope === "global"
      ? join(homedir(), agent.globalSkillsDir || agent.skillsDir)
      : join(process.cwd(), agent.skillsDir);

    if (!existsSync(baseDir)) continue;

    try {
      for (const entry of readdirSync(baseDir)) {
        const skillPath = join(baseDir, entry);
        if (statSync(skillPath).isDirectory() && existsSync(join(skillPath, "SKILL.md"))) {
          const existing = skillsMap.get(entry) || [];
          const displayPath = scope === "global"
            ? baseDir.replace(homedir(), "~")
            : baseDir.replace(process.cwd(), ".");
          existing.push({
            agent: agent.name,
            path: `${displayPath}/${entry}`,
            scope,
          });
          skillsMap.set(entry, existing);
        }
      }
    } catch {}
  }

  return skillsMap;
}

export async function discoverInstalledSkills(
  scopes: ("local" | "global")[],
  agents?: AgentInfo[]
): Promise<DiscoveredSkill[]> {
  const lockedSkills = await getAllLockedSkills();
  const allInstalled = new Map<string, SkillLocation[]>();

  for (const scope of scopes) {
    const installed = findInstalledSkills(scope, agents);
    for (const [name, locations] of installed) {
      const existing = allInstalled.get(name) || [];
      existing.push(...locations);
      allInstalled.set(name, existing);
    }
  }

  const results: DiscoveredSkill[] = [];
  const checkedNames = new Set<string>();

  for (const [name, entry] of Object.entries(lockedSkills)) {
    const locations = allInstalled.get(name);
    if (locations && locations.length > 0) {
      results.push({
        name,
        status: "managed",
        source: entry.source,
        locations,
      });
    } else {
      results.push({
        name,
        status: "missing",
        source: entry.source,
        locations: [],
      });
    }
    checkedNames.add(name);
  }

  for (const [name, locations] of allInstalled) {
    if (!checkedNames.has(name)) {
      results.push({
        name,
        status: "orphaned",
        locations,
      });
    }
  }

  const order = { managed: 0, missing: 1, orphaned: 2 };
  results.sort((a, b) => {
    const diff = order[a.status] - order[b.status];
    return diff !== 0 ? diff : a.name.localeCompare(b.name);
  });

  return results;
}

export function filterSkillsByStatus(
  skills: DiscoveredSkill[],
  options: {
    managed?: boolean;
    orphaned?: boolean;
    missing?: boolean;
  }
): DiscoveredSkill[] {
  const showManaged = options.managed ?? true;
  const showOrphaned = options.orphaned ?? true;
  const showMissing = options.missing ?? false;

  return skills.filter((s) => {
    if (s.status === "managed" && showManaged) return true;
    if (s.status === "orphaned" && showOrphaned) return true;
    if (s.status === "missing" && showMissing) return true;
    return false;
  });
}
