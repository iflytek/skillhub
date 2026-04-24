import { Command } from "commander";
import { existsSync } from "node:fs";
import { getAllAgents } from "../core/agent-detector.js";
import { getSkillLockPath } from "../core/skill-lock.js";
import { discoverInstalledSkills, filterSkillsByStatus, type DiscoveredSkill } from "../core/skill-status.js";
import { success, error, info, warn, dim } from "../utils/logger.js";
import { searchMultiselect, cancelSymbol } from "../utils/search-multiselect.js";
import * as p from "@clack/prompts";
import pc from "picocolors";

interface ListOptions {
  scope?: string;
  agent?: string[];
  status?: string[];
  json?: boolean;
}

export async function listAction(opts: ListOptions) {
  let scopes: ("local" | "global")[] = [];

  if (opts.scope) {
    const scopeValue = opts.scope.toLowerCase();
    if (scopeValue === "all") {
      scopes = ["local", "global"];
    } else if (scopeValue === "global") {
      scopes = ["global"];
    } else if (scopeValue === "project" || scopeValue === "local") {
      scopes = ["local"];
    }
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

    if (scopeSelection === "all") {
      scopes = ["local", "global"];
    } else if (scopeSelection === "global") {
      scopes = ["global"];
    } else {
      scopes = ["local"];
    }
  }

  let targetAgents = opts.agent
    ? getAllAgents().filter((a) => opts.agent!.includes(a.key))
    : undefined;

  if (!opts.agent) {
    const allAgents = getAllAgents();
    const agentItems = allAgents
      .map((a) => ({ value: a.key, label: a.name }))
      .sort((a, b) => a.label.localeCompare(b.label));

    const selected = await searchMultiselect({
      message: "Which agents to list from?",
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

  let showManaged = false;
  let showOrphaned = false;
  let showMissing = false;

  if (opts.status && opts.status.length > 0) {
    const statusSet = new Set(opts.status.map((s) => s.toLowerCase()));
    if (statusSet.has("all")) {
      showManaged = true;
      showOrphaned = true;
      showMissing = true;
    } else {
      showManaged = statusSet.has("managed");
      showOrphaned = statusSet.has("orphaned");
      showMissing = statusSet.has("missing");
    }
  } else {
    const statusSelection = await p.multiselect({
      message: "Which statuses to show?",
      options: [
        { value: "managed", label: "managed", hint: "installed and in lock file" },
        { value: "orphaned", label: "orphaned", hint: "installed but not in lock file" },
        { value: "missing", label: "missing", hint: "in lock file but not installed" },
      ],
      required: false,
      initialValues: ["managed", "orphaned"],
    });

    if (p.isCancel(statusSelection)) {
      console.log("Cancelled.");
      return;
    }

    const selected = statusSelection as string[];
    showManaged = selected.includes("managed");
    showOrphaned = selected.includes("orphaned");
    showMissing = selected.includes("missing");
  }

  const allSkills = await discoverInstalledSkills(scopes, targetAgents);
  const filteredSkills = filterSkillsByStatus(allSkills, {
    managed: showManaged,
    orphaned: showOrphaned,
    missing: showMissing,
  });

  if (opts.json) {
    console.log(JSON.stringify(filteredSkills, null, 2));
    return;
  }

  displaySkillList(filteredSkills, scopes, targetAgents);
}

export function registerList(program: Command) {
  program
    .command("list")
    .alias("ls")
    .description("List installed skills with status")
    .option("--scope <scope>", "Scope to list (global, project, all)")
    .option("--agent <agents...>", "Filter by specific agents")
    .option("--status <status...>", "Filter by status (managed, orphaned, missing, all)")
    .option("--json", "Output as JSON")
    .action(async (opts: ListOptions) => {
      await listAction(opts);
    });
}

function displaySkillList(
  skills: DiscoveredSkill[],
  scopes: ("local" | "global")[],
  targetAgents?: import("../core/agent-detector.js").AgentInfo[]
) {
  const scopeLabel = scopes.length === 2 ? "all scopes" : `${scopes[0]} scope`;
  const agentLabel = targetAgents
    ? ` (${targetAgents.map((a) => a.name).sort((a, b) => a.localeCompare(b)).join(", ")})`
    : "";

  console.log("");
  info(`Installed Skills (${scopeLabel})${agentLabel}:`);
  console.log("");

  if (skills.length === 0) {
    dim("  No skills found.");
    console.log("");
    return;
  }

  let managed = 0, missing = 0, orphaned = 0;

  for (const skill of skills) {
    if (skill.status === "managed") {
      managed++;
      success(`  ✓ ${skill.name}`);
      if (skill.source) {
        dim(`    Source: ${skill.source}`);
      }
      for (const loc of skill.locations) {
        dim(`    → ${loc.agent}: ${loc.path}`);
      }
    } else if (skill.status === "missing") {
      missing++;
      error(`  ✗ ${skill.name}`);
      if (skill.source) {
        dim(`    Source: ${skill.source}`);
      }
      dim(`    Status: NOT INSTALLED`);
    } else if (skill.status === "orphaned") {
      orphaned++;
      warn(`  ! ${skill.name}`);
      for (const loc of skill.locations) {
        dim(`    → ${loc.agent}: ${loc.path}`);
      }
      dim(`    Status: NOT IN LOCK FILE`);
    }
  }

  console.log("");
  const lockPath = getSkillLockPath();
  if (existsSync(lockPath)) {
    dim(`Lock file: ${lockPath}`);
  }
  dim(`Summary: ${managed} managed, ${missing} missing, ${orphaned} orphaned`);
  console.log("");
}
