import { Command } from "commander";
import { mkdtemp, rm, readFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { createWriteStream, createReadStream, existsSync, mkdirSync } from "node:fs";
import { ApiClient } from "../core/api-client.js";
import { loadConfig } from "../core/config.js";
import { readToken } from "../core/auth-token.js";
import { discoverSkills } from "../core/skill-discovery.js";
import { installSkill } from "../core/installer.js";
import { getAllAgents, detectInstalledAgents, isUniversalForScope, getAgentTargetDir, type AgentInfo } from "../core/agent-detector.js";
import { parseSource, getCloneUrl } from "../core/source-parser.js";
import { addToLock } from "../core/skill-lock.js";
import { success, error, info, dim } from "../utils/logger.js";
import unzipper from "unzipper";
import { multiSelect, sectionMultiSelect } from "../utils/prompts.js";
import { searchMultiselect, cancelSymbol } from "../utils/search-multiselect.js";
import { runInteractiveSearch, searchSkills } from "../core/interactive-search.js";
import type { SkillVersionItem } from "./versions.js";

interface SkillTag {
  id: number;
  tagName: string;
  versionId: number;
  createdAt: string;
}
import * as p from "@clack/prompts";
import pc from "picocolors";
import ora from "ora";
import { execSync } from "node:child_process";
import { finished } from "node:stream/promises";

export type SourceType = "auto" | "registry" | "git" | "local";

function detectSourceType(arg: string): SourceType {
  if (arg.startsWith(".") || arg.startsWith("/") || arg.startsWith("~")) {
    return "local";
  }
  if (arg.includes("github.com") || arg.includes("gitlab.com") || arg.includes("://") || arg.endsWith(".git")) {
    return "git";
  }
  if (/^[\w-]+\/[\w-]+$/.test(arg)) {
    return "registry";
  }
  return "registry";
}

function getInstallSpinner(sourceType: SourceType, arg: string): string {
  if (sourceType === "registry") {
    return `Searching registry for ${arg}`;
  }
  return `Resolving ${arg}`;
}

async function selectAgentsInteractive(isGlobal: boolean): Promise<string[] | null> {
  const allAgents = getAllAgents();

  // Use scope-aware universal check: an agent is "universal" when its target
  // install dir equals the canonical .agents/skills directory for the given scope.
  const universalAgents = allAgents.filter((a) => isUniversalForScope(a, isGlobal));
  const nonUniversalAgents = allAgents.filter((a) => !isUniversalForScope(a, isGlobal));

  const canonicalLabel = isGlobal ? "Universal (~/.agents/skills)" : "Universal (.agents/skills)";
  const lockedSection = {
    title: canonicalLabel,
    items: universalAgents.map((a) => ({
      value: a.key,
      label: a.name,
    })),
  };

  const selectableItems = nonUniversalAgents.map((a) => ({
    value: a.key,
    label: a.name,
    hint: isGlobal ? (a.globalSkillsDir || a.skillsDir) : a.skillsDir,
  }));

  const result = await searchMultiselect({
    message: "Which agents do you want to install to?",
    items: selectableItems,
    lockedSection,
  });

  if (result === cancelSymbol) {
    return null;
  }

  return result as string[];
}

async function selectInstallMode(): Promise<"symlink" | "copy" | null> {
  const result = await p.select({
    message: "Installation method?",
    options: [
      {
        value: "symlink",
        label: "Symlink (Recommended)",
        hint: "single source of truth",
      },
      {
        value: "copy",
        label: "Copy to all agents",
        hint: "independent copies",
      },
    ],
  });

  if (p.isCancel(result)) {
    return null;
  }

  return result as "symlink" | "copy";
}

function buildAgentSummary(targetAgents: AgentInfo[], mode: "symlink" | "copy", isGlobal: boolean): string[] {
  const lines: string[] = [];
  const universal = targetAgents.filter((a) => isUniversalForScope(a, isGlobal));
  const symlinked = targetAgents.filter((a) => !isUniversalForScope(a, isGlobal));

  if (mode === "symlink") {
    if (universal.length > 0) {
      lines.push(`  universal: ${universal.map((a) => a.name).join(", ")}`);
    }
    if (symlinked.length > 0) {
      lines.push(`  symlink → ${symlinked.map((a) => a.name).join(", ")}`);
    }
  } else {
    lines.push(`  copy → ${targetAgents.map((a) => a.name).join(", ")}`);
  }

  return lines;
}

export function registerInstall(program: Command) {
  program
    .command("install <source>")
    .alias("i")
    .description("Install skills from registry, git repositories, or local paths")
    .option("-a, --add <source>", "Install from GitHub or local path (alias for --from)")
    .option("--from <source>", "Install from GitHub or local path (alias for -a)")
    .option("--agent <agents...>", "Target specific agents")
    .option("-g, --global", "Install to global scope")
    .option("-y, --yes", "Skip all prompts")
    .option("--copy", "Copy instead of symlink")
    .option("--list", "List available skills without installing")
    .option("-v, --skill-version <ver>", "Install specific version (non-interactive)")
    .option("--tag <tag>", "Install specific tag (non-interactive, resolves to version)")
    .action(async (source: string, opts: Record<string, string | string[] | boolean>) => {
      const fromSource = (opts.from || opts.add) as string | undefined;

      let effectiveSource: SourceType;
      let installSource = source;

      if (fromSource) {
        effectiveSource = detectSourceType(fromSource);
        installSource = fromSource;
      } else {
        effectiveSource = detectSourceType(source);
      }

      const spinner = ora(getInstallSpinner(effectiveSource, installSource)).start();

      try {
        if (effectiveSource === "registry") {
          await installFromRegistry(source, opts, spinner);
        } else {
          await installFromGit(source, installSource, effectiveSource, opts, spinner);
        }
      } catch (e: any) {
        spinner.fail(e.message);
        process.exit(1);
      }
    });
}

async function installFromRegistry(slug: string, opts: Record<string, string | string[] | boolean>, spinner: any) {
  let ns = "global";
  let actualSlug = slug;
  let userSpecifiedNamespace = false;

  if (slug.includes("/") && !slug.startsWith("/")) {
    const parts = slug.split("/");
    if (parts.length === 2) {
      ns = parts[0];
      actualSlug = parts[1];
      userSpecifiedNamespace = true;
    }
  }

  const config = loadConfig();
  const token = await readToken();
  const client = new ApiClient({ baseUrl: config.registry, token: token || undefined });

  if (!userSpecifiedNamespace) {
    const results = await searchSkills(client, actualSlug, 50);

    // Deduplicate by namespace/name
    const seen = new Set<string>();
    const uniqueResults = results.filter(r => {
      const key = `${r.namespace}/${r.name}`;
      if (!seen.has(key)) {
        seen.add(key);
        return true;
      }
      return false;
    });

    if (uniqueResults.length === 0) {
      spinner.fail(`Skill not found: ${actualSlug}`);
      process.exit(1);
    }

    if (uniqueResults.length === 1) {
      ns = uniqueResults[0].namespace;
      actualSlug = uniqueResults[0].name;
    } else {
      spinner.stop();
      const selected = await runInteractiveSearch(client, actualSlug);
      if (!selected) {
        console.log("Cancelled.");
        return;
      }
      spinner.start(`Fetching ${selected}`);
      const [selectedNs, selectedName] = selected.split("/", 2);
      ns = selectedNs;
      actualSlug = selectedName;
    }
  }

  spinner.text = `Fetching versions for ${ns}/${actualSlug}`;
  const [versionsResp, tagsResp] = await Promise.all([
    client.get<{ items: SkillVersionItem[] }>(`/api/v1/skills/${ns}/${actualSlug}/versions`),
    client.get<SkillTag[]>(`/api/v1/skills/${ns}/${actualSlug}/tags`).catch(() => [] as SkillTag[]),
  ]);

  const versions = versionsResp.items || [];

  // Map tags to versions by versionId
  const versionTagsMap = new Map<number, string[]>();
  for (const tag of tagsResp || []) {
    if (!versionTagsMap.has(tag.versionId)) {
      versionTagsMap.set(tag.versionId, []);
    }
    versionTagsMap.get(tag.versionId)!.push(tag.tagName);
  }

  // Present version selection
  let selectedVersion: string = "latest";
  if (opts.yes && opts.skillVersion) {
    selectedVersion = String(opts.skillVersion);
  } else if (opts.yes && opts.tag) {
    // Non-interactive: resolve tag to version
    for (const [vid, tags] of versionTagsMap) {
      if (tags.includes(opts.tag as string)) {
        const v = versions.find((ver) => ver.id === vid);
        if (v) {
          selectedVersion = v.version;
          break;
        }
      }
    }
    if (!selectedVersion) {
      // Fallback: use latest if tag not found
      selectedVersion = versions[0]?.version || "latest";
    }
  } else {
    // Interactive: show version selection
    spinner.stop();

    const picked = await p.select({
      message: "Select version",
      options: versions.map((v) => ({
        value: v.version,
        label: `v${v.version}`,
        hint: versionTagsMap.get(v.id)?.join(", ") || "",
      })),
    });

    if (p.isCancel(picked)) {
      console.log("Cancelled.");
      return;
    }

    selectedVersion = picked as string;
    spinner.start(`Downloading ${ns}/${actualSlug}@${selectedVersion}`);
  }

  const baseUrl = config.registry.replace(/\/$/, "");
  const downloadUrl = `${baseUrl}/api/v1/skills/${ns}/${actualSlug}/versions/${selectedVersion}/download`;
  const tmpDir = await mkdtemp(join(tmpdir(), "skillhub-install-"));
  const zipPath = join(tmpDir, `${actualSlug}.zip`);

  spinner.text = `Downloading ${ns}/${actualSlug}@${selectedVersion}`;

  const { request } = await import("undici");
  let response = await request(downloadUrl, {
    method: "GET",
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  // undici 不自动跟随 redirect，手动处理 302/307/308
  if (response.statusCode === 302 || response.statusCode === 307 || response.statusCode === 308) {
    const location = response.headers.location;
    if (!location) {
      spinner.fail(`Redirect response has no Location header`);
      await rm(tmpDir, { recursive: true, force: true });
      process.exit(1);
    }
          response = await request(location as string, { method: "GET" });
  }
  const { statusCode, body } = response;

  if (statusCode >= 400) {
    spinner.fail(`Skill not found: ${ns}/${actualSlug}`);
    await rm(tmpDir, { recursive: true, force: true });
    process.exit(1);
  }

  const fileStream = createWriteStream(zipPath);
  await finished(body.pipe(fileStream));

  spinner.text = `Extracting ${actualSlug}`;
  const extractDir = join(tmpDir, "extracted");
  mkdirSync(extractDir, { recursive: true });
  await createReadStream(zipPath)
    .pipe(unzipper.Extract({ path: extractDir }))
    .promise();

  const skills = discoverSkills(extractDir);
  if (skills.length === 0) {
    spinner.fail("No SKILL.md found in package");
    process.exit(1);
  }

  spinner.succeed(`Found ${skills.length} skill(s) in ${ns}/${actualSlug}`);

  if (opts.list) {
    for (const s of skills) {
      info(`${s.name}`);
      dim(`  ${s.description}`);
    }
    return;
  }

  let selectedSkills = skills;
  if (!opts.yes && skills.length > 1) {
    const selected = await searchMultiselect({
      message: "Select skills to install",
      items: skills.map((s) => ({ value: s.name, label: s.name, hint: s.description })),
    });
    if (selected === cancelSymbol) {
      console.log("Cancelled.");
      return;
    }
    selectedSkills = skills.filter((s) => (selected as string[]).includes(s.name));
  }

  let isGlobal = !!opts.global;

  // Determine scope first, so that agent selection can use the correct
  // universal/non-universal grouping based on the actual scope.
  const allAgents = getAllAgents();
  const supportsGlobal = allAgents.some((a) => a.globalSkillsDir);

  if (opts.global === undefined && !opts.yes && supportsGlobal) {
    const scope = await p.select({
      message: "Installation scope",
      options: [
        {
          value: false,
          label: "Project",
          hint: "Install in current directory (committed with your project)",
        },
        {
          value: true,
          label: "Global",
          hint: "Install in home directory (available across all projects)",
        },
      ],
    });

    if (p.isCancel(scope)) {
      console.log("Cancelled.");
      return;
    }

    isGlobal = scope as boolean;
  }

  let targetAgents = opts.agent
    ? allAgents.filter((a) => (opts.agent as string[]).includes(a.key))
    : detectInstalledAgents();

  if (targetAgents.length === 0) {
    const claude = allAgents.find((a) => a.key === "claude-code");
    if (claude) targetAgents.push(claude);
  }

  let mode: "symlink" | "copy" = opts.copy ? "copy" : "symlink";

  if (!opts.yes && !opts.agent) {
    const selected = await selectAgentsInteractive(isGlobal);
    if (!selected) {
      console.log("Cancelled.");
      return;
    }
    targetAgents = allAgents.filter((a) => selected.includes(a.key));
  }

  // Only prompt for install mode when there are multiple unique target directories.
  // When all selected agents share the same skillsDir, symlink vs copy is meaningless.
  const uniqueDirs = new Set(targetAgents.map((a) => getAgentTargetDir(a, isGlobal)));

  if (uniqueDirs.size <= 1) {
    // Single target directory — default to copy (no symlink needed)
    mode = 'copy';
  } else if (!opts.yes) {
    const selectedMode = await selectInstallMode();
    if (selectedMode === null) {
      console.log("Cancelled.");
      return;
    }
    mode = selectedMode;
  }

  const cwd = process.cwd();
  const summaryLines: string[] = [];

  for (const skill of selectedSkills) {
    if (summaryLines.length > 0) summaryLines.push("");
    const canonicalPath = isGlobal
      ? `~/.agents/skills/${skill.name}`
      : `./.agents/skills/${skill.name}`;
    summaryLines.push(`${pc.cyan(canonicalPath)}`);
    for (const line of buildAgentSummary(targetAgents, mode, isGlobal)) {
      summaryLines.push(`  ${line}`);
    }
  }
  summaryLines.push("");
  summaryLines.push(`${pc.dim("Mode:")} ${mode}`);
  summaryLines.push(`${pc.dim("Scope:")} ${isGlobal ? "global" : "project"}`);

  console.log("");
  p.note(summaryLines.join("\n"), "Installation Summary");

  if (!opts.yes) {
    const confirmed = await p.confirm({ message: "Proceed with installation?" });

    if (p.isCancel(confirmed) || !confirmed) {
      console.log("Cancelled.");
      return;
    }
  }

  spinner.start("Installing skills...");

  let installed = 0;
  let failed = 0;
  const results: { skill: string; agent: string; success: boolean; path: string; error?: string }[] = [];

  for (const skill of selectedSkills) {
    for (const agent of targetAgents) {
      const result = installSkill(
        skill.dir,
        skill.name,
        agent.key,
        getAgentTargetDir(agent, isGlobal),
        mode,
        isGlobal,
        agent,
      );
      results.push({
        skill: skill.name,
        agent: agent.name,
        success: result.success,
        path: result.path || "",
        error: result.error,
      });
      if (result.success) {
        installed++;
        await addToLock(skill.name, {
          source: `${ns}/${actualSlug}`,
          sourceType: "registry",
          sourceUrl: `${config.registry}/api/v1/skills/${ns}/${actualSlug}`,
          namespace: ns,
          slug: skill.name,
          version: "latest",
        });
      } else {
        failed++;
        error(`Failed to install ${skill.name} to ${agent.name}: ${result.error}`);
      }
    }
  }

  spinner.stop("Installation complete");

  console.log("");
  const successful = results.filter((r) => r.success);

  if (successful.length > 0) {
    const resultLines: string[] = [];
    for (const skill of selectedSkills) {
      const skillResults = results.filter((r) => r.skill === skill.name && r.success);
      if (skillResults.length > 0) {
        resultLines.push(`${pc.green("✓")} ${skill.name}`);
        for (const r of skillResults) {
          resultLines.push(`  ${pc.dim("→")} ${r.agent}: ${r.path}`);
        }
      }
    }
    p.note(resultLines.join("\n"), `Installed ${successful.length} skill(s)`);
  }

  if (failed > 0) {
    p.log.error(pc.red(`Failed to install ${failed}`));
    for (const r of results.filter((r) => !r.success)) {
      p.log.message(`${pc.red("✗")} ${r.skill} → ${r.agent}: ${pc.dim(r.error || "unknown error")}`);
    }
  }

  console.log("");
  p.outro(pc.green("Done!") + pc.dim("  Review skills before use; they run with full agent permissions."));

  await rm(tmpDir, { recursive: true, force: true });
}

async function installFromGit(skillName: string, source: string, sourceType: SourceType, opts: Record<string, string | string[] | boolean>, spinner: any) {
  let skillsDir: string;

  const parsed = parseSource(source);

  if (parsed.skillFilter) {
    opts.skill = opts.skill || [];
    if (!Array.isArray(opts.skill)) {
      opts.skill = [opts.skill as string];
    }
    if (!opts.skill.includes(parsed.skillFilter)) {
      opts.skill.push(parsed.skillFilter);
    }
  }

  // If skillName is a skill identifier (not a path), use it to filter
  if (skillName && !skillName.startsWith(".") && !skillName.startsWith("/") && !skillName.startsWith("~")) {
    opts.skill = opts.skill || [];
    if (!Array.isArray(opts.skill)) {
      opts.skill = [opts.skill as string];
    }
    if (!opts.skill.includes(skillName)) {
      opts.skill.push(skillName);
    }
  }

  if (parsed.type === "local") {
    skillsDir = parsed.localPath!;
    spinner.text = `Scanning ${parsed.localPath}`;
  } else {
    const cloneUrl = getCloneUrl(parsed);
    spinner.text = `Cloning ${cloneUrl}`;
    const tmpDir = await mkdtemp(join(tmpdir(), "skillhub-install-"));
    const refArg = parsed.ref ? `--branch ${parsed.ref}` : "";
    const depth = parsed.ref ? "" : "--depth 1";
    execSync(`git clone ${depth} ${refArg} ${cloneUrl} ${tmpDir}`, { stdio: "pipe" });
    skillsDir = tmpDir;

    process.on("exit", () => { rm(tmpDir, { recursive: true, force: true }).catch(() => {}); });
  }

  spinner.text = "Discovering skills";
  const skills = discoverSkills(skillsDir);

  if (skills.length === 0) {
    spinner.fail("No skills found. Ensure the directory contains SKILL.md files.");
    process.exit(1);
  }

  spinner.succeed(`Found ${skills.length} skill(s)`);

  if (opts.list) {
    for (const s of skills) {
      info(`${s.name}`);
      dim(`  ${s.description}`);
    }
    return;
  }

  let selectedSkills = skills;
  if (opts.skill) {
    const skillNames = opts.skill as string[];
    if (skillNames.includes("*")) {
      selectedSkills = skills;
    } else {
      selectedSkills = skills.filter((s) => skillNames.includes(s.name));
      if (selectedSkills.length === 0) {
        error(`No matching skills for: ${skillNames.join(", ")}`);
        info("Available: " + skills.map((s) => s.name).join(", "));
        process.exit(1);
      }
    }
  } else if (!opts.yes && skills.length > 1) {
    const selected = await searchMultiselect({
      message: "Select skills to install",
      items: skills.map((s) => ({ value: s.name, label: s.name, hint: s.description })),
    });

    if (selected === cancelSymbol) {
      console.log("Cancelled.");
      return;
    }

    selectedSkills = skills.filter((s) => (selected as string[]).includes(s.name));
  }

  let isGlobal = !!opts.global;

  // Determine scope first, so that agent selection can use the correct
  // universal/non-universal grouping based on the actual scope.
  const allAgents = getAllAgents();
  const supportsGlobal = allAgents.some((a) => a.globalSkillsDir);

  if (opts.global === undefined && !opts.yes && supportsGlobal) {
    const scope = await p.select({
      message: "Installation scope",
      options: [
        {
          value: false,
          label: "Project",
          hint: "Install in current directory (committed with your project)",
        },
        {
          value: true,
          label: "Global",
          hint: "Install in home directory (available across all projects)",
        },
      ],
    });

    if (p.isCancel(scope)) {
      console.log("Cancelled.");
      return;
    }

    isGlobal = scope as boolean;
  }

  let targetAgents = opts.agent
    ? allAgents.filter((a) => (opts.agent as string[]).includes(a.key))
    : detectInstalledAgents();

  if (targetAgents.length === 0) {
    if (!opts.yes) {
      info("No agents detected. Installing to Claude Code by default.");
    }
    const claude = allAgents.find((a) => a.key === "claude-code");
    if (claude) targetAgents.push(claude);
    else targetAgents.push(allAgents[0]);
  }

  let mode: "symlink" | "copy" = opts.copy ? "copy" : "symlink";

  if (!opts.yes && !opts.agent) {
    const selected = await selectAgentsInteractive(isGlobal);
    if (!selected) {
      console.log("Cancelled.");
      return;
    }
    targetAgents = allAgents.filter((a) => selected.includes(a.key));
  }

  // Only prompt for install mode when there are multiple unique target directories.
  // When all selected agents share the same skillsDir, symlink vs copy is meaningless.
  const uniqueDirs = new Set(targetAgents.map((a) => getAgentTargetDir(a, isGlobal)));

  if (uniqueDirs.size <= 1) {
    // Single target directory — default to copy (no symlink needed)
    mode = 'copy';
  } else if (!opts.yes) {
    const selectedMode = await selectInstallMode();
    if (selectedMode === null) {
      console.log("Cancelled.");
      return;
    }
    mode = selectedMode;
  }

  const cwd = process.cwd();
  const summaryLines: string[] = [];

  for (const skill of selectedSkills) {
    if (summaryLines.length > 0) summaryLines.push("");
    const canonicalPath = isGlobal
      ? `~/.agents/skills/${skill.name}`
      : `./.agents/skills/${skill.name}`;
    summaryLines.push(`${pc.cyan(canonicalPath)}`);
    for (const line of buildAgentSummary(targetAgents, mode, isGlobal)) {
      summaryLines.push(`  ${line}`);
    }
  }
  summaryLines.push("");
  summaryLines.push(`${pc.dim("Mode:")} ${mode}`);
  summaryLines.push(`${pc.dim("Scope:")} ${isGlobal ? "global" : "project"}`);

  console.log("");
  p.note(summaryLines.join("\n"), "Installation Summary");

  if (!opts.yes) {
    const confirmed = await p.confirm({ message: "Proceed with installation?" });

    if (p.isCancel(confirmed) || !confirmed) {
      console.log("Cancelled.");
      return;
    }
  }

  spinner.start("Installing skills...");

  let installed = 0;
  let failed = 0;
  const results: { skill: string; agent: string; success: boolean; path: string; error?: string }[] = [];

  for (const skill of selectedSkills) {
    for (const agent of targetAgents) {
      const result = installSkill(
        skill.dir,
        skill.name,
        agent.key,
        getAgentTargetDir(agent, isGlobal),
        mode,
        isGlobal,
        agent,
      );
      results.push({
        skill: skill.name,
        agent: agent.name,
        success: result.success,
        path: result.path || "",
        error: result.error,
      });
      if (result.success) {
        installed++;
        const sourceUrl = parsed.type === "local"
          ? (parsed.localPath as string)
          : getCloneUrl(parsed);
        await addToLock(skill.name, {
          source: source,
          sourceType: parsed.type === "local" ? "local" : "git",
          sourceUrl: sourceUrl,
          ref: parsed.ref,
          namespace: "global",
          slug: skill.name,
          version: parsed.ref || "main",
        });
      } else {
        failed++;
        error(`Failed to install ${skill.name} to ${agent.name}: ${result.error}`);
      }
    }
  }

  spinner.stop("Installation complete");

  console.log("");
  const successful = results.filter((r) => r.success);

  if (successful.length > 0) {
    const resultLines: string[] = [];
    for (const skill of selectedSkills) {
      const skillResults = results.filter((r) => r.skill === skill.name && r.success);
      if (skillResults.length > 0) {
        resultLines.push(`${pc.green("✓")} ${skill.name}`);
        for (const r of skillResults) {
          resultLines.push(`  ${pc.dim("→")} ${r.agent}: ${r.path}`);
        }
      }
    }
    p.note(resultLines.join("\n"), `Installed ${successful.length} skill(s)`);
  }

  if (failed > 0) {
    p.log.error(pc.red(`Failed to install ${failed}`));
    for (const r of results.filter((r) => !r.success)) {
      p.log.message(`${pc.red("✗")} ${r.skill} → ${r.agent}: ${pc.dim(r.error || "unknown error")}`);
    }
  }

  console.log("");
  p.outro(pc.green("Done!") + pc.dim("  Review skills before use; they run with full agent permissions."));
}
