import { Command } from "commander";
import { existsSync, readdirSync, statSync } from "node:fs";
import { join } from "node:path";
import { homedir } from "node:os";
import { getAllAgents, type AgentInfo } from "../core/agent-detector.js";
import { getAllLockedSkills, getSkillLockPath } from "../core/skill-lock.js";
import { success, error, info, warn, dim } from "../utils/logger.js";
import * as p from "@clack/prompts";
import { searchMultiselect, cancelSymbol } from "../utils/search-multiselect.js";

interface CheckResult {
  name: string;
  status: "ok" | "missing" | "orphaned";
  source?: string;
  location?: string;
}

function findInstalledSkills(
  scope: "local" | "global",
  agents?: AgentInfo[]
): Map<string, string[]> {
  const skillsMap = new Map<string, string[]>();
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
          existing.push(agent.name);
          skillsMap.set(entry, existing);
        }
      }
    } catch {}
  }

  return skillsMap;
}

export function registerCheck(program: Command) {
  program
    .command("check")
    .description("Check installed skills against lock file")
    .option("--global", "Check global scope skills")
    .option("--local", "Check local (project) scope skills")
    .option("--all", "Check both global and local scopes")
    .option("--agent <agents...>", "Filter by specific agents")
    .option("--status <status...>", "Filter by status (ok, missing, orphaned)")
    .option("--json", "Output results as JSON")
    .action(async (opts: {
      global?: boolean;
      local?: boolean;
      all?: boolean;
      agent?: string[];
      status?: string[];
      json?: boolean;
    }) => {
      // Determine scopes
      let scopes: ("local" | "global")[] = [];

      if (opts.all) {
        scopes = ["local", "global"];
      } else if (opts.global) {
        scopes = ["global"];
      } else if (opts.local) {
        scopes = ["local"];
      } else {
        // Interactive scope selection
        const scopeSelection = await p.select({
          message: "Which scope to check?",
          options: [
            { value: "all", label: "All (global + project)" },
            { value: "global", label: "Global only" },
            { value: "local", label: "Project only" },
          ],
        });

        if (p.isCancel(scopeSelection)) {
          console.log("Cancelled.");
          return;
        }

        if (scopeSelection === "all") {
          scopes = ["local", "global"];
        } else if (scopeSelection === "global") {
          scopes = ["global"];
        } else {
          scopes = ["local"];
        }
      }

      // Determine agents to check
      let targetAgents: AgentInfo[] | undefined;
      if (opts.agent && opts.agent.length > 0) {
        const allAgents = getAllAgents();
        targetAgents = allAgents.filter((a) => opts.agent!.includes(a.key));
      } else if (!opts.agent) {
        // Interactive agent selection
        const allAgents = getAllAgents();
        const agentItems = allAgents
          .map((a) => ({
            value: a.key,
            label: a.name,
          }))
          .sort((a, b) => a.label.localeCompare(b.label));

        const selected = await searchMultiselect({
          message: "Which agents to check?",
          items: agentItems,
          required: false,
        });

        if (selected === cancelSymbol) {
          console.log("Cancelled.");
          return;
        }

        if (selected && selected.length > 0) {
          targetAgents = allAgents.filter((a) => (selected as string[]).includes(a.key));
        }
      }

      // Determine which statuses to show
      let showOk = false;
      let showMissing = false;
      let showOrphaned = false;

      if (opts.status && opts.status.length > 0) {
        // Command line flags
        showOk = opts.status.includes("ok");
        showMissing = opts.status.includes("missing");
        showOrphaned = opts.status.includes("orphaned");
      } else {
        // Interactive status selection (default: ok + missing only)
        const statusSelection = await p.multiselect({
          message: "Which statuses to show?",
          options: [
            { value: "ok", label: "OK (installed and in lock file)" },
            { value: "missing", label: "Missing (in lock file but not installed)" },
            { value: "orphaned", label: "Orphaned (installed but not in lock file)" },
          ],
          required: false,
          initialValues: ["ok", "missing"],
        });

        if (p.isCancel(statusSelection)) {
          console.log("Cancelled.");
          return;
        }

        const selected = statusSelection as string[];
        showOk = selected.includes("ok");
        showMissing = selected.includes("missing");
        showOrphaned = selected.includes("orphaned");
      }

      const lockPath = getSkillLockPath();

      if (!existsSync(lockPath)) {
        if (opts.json) {
          console.log(JSON.stringify({ error: "No lock file found" }, null, 2));
        } else {
          warn("No skillhub.lock found. Have you installed any skills?");
        }
        return;
      }

      const lockedSkills = await getAllLockedSkills();
      const allResults: CheckResult[] = [];

      // Check each scope
      for (const scope of scopes) {
        const installedSkills = findInstalledSkills(scope, targetAgents);

        for (const [name, entry] of Object.entries(lockedSkills)) {
          const installedLocations = installedSkills.get(name);
          if (installedLocations && installedLocations.length > 0) {
            allResults.push({
              name,
              status: "ok",
              source: entry.source,
              location: `${scope}: ${installedLocations.sort((a, b) => a.localeCompare(b)).join(", ")}`,
            });
          }
        }

        for (const [name, locations] of installedSkills.entries()) {
          if (!lockedSkills[name]) {
            allResults.push({
              name,
              status: "orphaned",
              location: `${scope}: ${locations.sort((a, b) => a.localeCompare(b)).join(", ")}`,
            });
          }
        }
      }

      // Mark missing skills (not found in any scope)
      const checkedNames = new Set<string>();
      for (const r of allResults) {
        if (r.status !== "orphaned") {
          checkedNames.add(r.name);
        }
      }

      for (const [name, entry] of Object.entries(lockedSkills)) {
        if (!checkedNames.has(name)) {
          allResults.push({
            name,
            status: "missing",
            source: entry.source,
          });
        }
      }

      // Sort results: ok → missing → orphaned, then alphabetically by name
      allResults.sort((a, b) => {
        const order = { ok: 0, missing: 1, orphaned: 2 };
        const diff = order[a.status] - order[b.status];
        return diff !== 0 ? diff : a.name.localeCompare(b.name);
      });

      if (opts.json) {
        console.log(JSON.stringify(allResults, null, 2));
        return;
      }

      // Filter results by selected statuses
      const filteredResults = allResults.filter((r) => {
        if (r.status === "ok") return showOk;
        if (r.status === "missing") return showMissing;
        if (r.status === "orphaned") return showOrphaned;
        return false;
      });

      const scopeLabel = scopes.length === 2 ? "all scopes" : `${scopes[0]} scope`;
      const agentLabel = targetAgents
        ? ` (${targetAgents.map((a) => a.name).sort((a, b) => a.localeCompare(b)).join(", ")})`
        : "";

      console.log("");
      info(`SkillHub Lock Check (${scopeLabel})${agentLabel}:`);
      console.log("");

      if (filteredResults.length === 0) {
        dim("  No matching skills found.");
        console.log("");
        return;
      }

      let ok = 0,
        missing = 0,
        orphaned = 0;

      for (const r of filteredResults) {
        if (r.status === "ok") {
          ok++;
          success(`  ✓ ${r.name}`);
          dim(`    Source: ${r.source}`);
          dim(`    Location: ${r.location}`);
        } else if (r.status === "missing") {
          missing++;
          error(`  ✗ ${r.name}`);
          dim(`    Source: ${r.source}`);
          dim(`    Status: NOT INSTALLED`);
        } else if (r.status === "orphaned") {
          orphaned++;
          warn(`  ! ${r.name}`);
          dim(`    Location: ${r.location}`);
          dim(`    Status: NOT IN LOCK FILE`);
        }
      }

      console.log("");
      dim(`Lock file: ${lockPath}`);
      dim(`Summary: ${ok} OK, ${missing} missing, ${orphaned} orphaned`);
      console.log("");
    });
}
