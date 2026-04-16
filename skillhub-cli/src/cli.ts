#!/usr/bin/env node
import { Command } from "commander";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { dirname } from "node:path";

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

// ANSI helpers (zero-dependency)
const bold = (s: string) => `\x1b[1m${s}\x1b[0m`;
const dim = (s: string) => `\x1b[2m${s}\x1b[0m`;
const cyan = (s: string) => `\x1b[36m${s}\x1b[0m`;

function getPackageVersion(): string {
  try {
    const pkgPath = resolve(__dirname, "../package.json");
    const pkg = JSON.parse(readFileSync(pkgPath, "utf-8"));
    return pkg.version;
  } catch {
    return "0.1.0";
  }
}

interface HelpEntry {
  cmd: string;
  desc: string;
  alias?: string;
}

function formatSection(header: string, entries: HelpEntry[]): string {
  const displayWidth = (e: HelpEntry) => e.cmd.length + (e.alias ? e.alias.length + 3 : 0);
  const maxW = entries.reduce((max, e) => Math.max(max, displayWidth(e)), 0);
  const col = Math.max(maxW + 4, 28);
  const lines = [bold(header)];
  for (const e of entries) {
    const aliasPart = e.alias ? dim(` (${e.alias})`) : "";
    const pad = " ".repeat(Math.max(col - displayWidth(e), 2));
    lines.push(`  ${cyan(e.cmd)}${aliasPart}${pad}${e.desc}`);
  }
  return lines.join("\n");
}

function buildTopLevelHelp(version: string): string {
  const sections: string[] = [];

  sections.push(`${bold("skillhub")} ${dim(`v${version}`)}`);
  sections.push(dim("CLI for SkillHub — publish, search, and manage agent skills"));
  sections.push("");

  sections.push(formatSection("Auth", [
    { cmd: "login", desc: "Authenticate with SkillHub registry" },
    { cmd: "logout", desc: "Remove stored authentication token" },
    { cmd: "whoami", desc: "Show current authenticated user" },
  ]));
  sections.push("");

  sections.push(formatSection("Discover", [
    { cmd: "explore", desc: "Browse or search skills from the registry", alias: "find, find-skills, search" },
    { cmd: "search <query>", desc: dim("[Deprecated: use 'explore' instead]") },
  ]));
  sections.push("");

  sections.push(formatSection("Install & Manage", [
    { cmd: "install <source>", desc: "Install from registry, git, or local path", alias: "i" },
    { cmd: "download <slug>", desc: "Download a skill package to local directory" },
    { cmd: "update [slug]", desc: "Update installed skills from their source", alias: "up" },
    { cmd: "uninstall [name]", desc: "Uninstall a skill from local agent", alias: "un" },
    { cmd: "list", desc: "List installed skills", alias: "ls" },
    { cmd: "check", desc: "Check installed skills against lock file" },
  ]));
  sections.push("");

  sections.push(formatSection("Publish", [
    { cmd: "init [name]", desc: "Create a new SKILL.md template" },
    { cmd: "publish [path]", desc: "Publish a skill to SkillHub registry" },
    { cmd: "sync [path]", desc: "Scan and publish all skills from a directory" },
    { cmd: "delete <slug>", desc: "Delete a skill you own", alias: "del, unpublish" },
    { cmd: "archive <slug>", desc: "Archive a skill you own" },
    { cmd: "versions <slug>", desc: "List skill versions" },
  ]));
  sections.push("");

  sections.push(formatSection("Info & Review", [
    { cmd: "inspect <slug>", desc: "View skill metadata without installing", alias: "info, view" },
    { cmd: "resolve <slug>", desc: "Resolve the latest version of a skill" },
    { cmd: "rating <slug>", desc: "View your rating for a skill" },
    { cmd: "rate <slug> <score>", desc: "Rate a skill (1-5)" },
    { cmd: "star <slug>", desc: "Star a skill" },
    { cmd: "report <slug>", desc: "Report a skill for review" },
  ]));
  sections.push("");

  sections.push(formatSection("Account", [
    { cmd: "me skills", desc: "List your published skills", alias: "me ls" },
    { cmd: "me stars", desc: "List your starred skills" },
    { cmd: "namespaces", desc: "List namespaces you have access to" },
    { cmd: "notifications", desc: "Manage notifications", alias: "notif" },
    { cmd: "reviews my", desc: "List your review submissions", alias: "reviews submissions" },
  ]));
  sections.push("");

  sections.push(formatSection("Admin", [
    { cmd: "hide <slug>", desc: "Hide a skill (admin only)" },
    { cmd: "unhide <slug>", desc: "Unhide a skill (admin only)" },
    { cmd: "transfer <ns> <user>", desc: "Transfer namespace ownership" },
  ]));
  sections.push("");

  sections.push(bold("Examples"));
  sections.push(dim("  skillhub install vision2group/fork-workflow   Install a skill"));
  sections.push(dim("  skillhub explore                               Browse available skills"));
  sections.push(dim("  skillhub publish                               Publish current directory"));
  sections.push(dim("  skillhub me skills                             List your published skills"));
  sections.push(dim("  skillhub update --global                       Update all global skills"));
  sections.push("");

  sections.push(bold("Global Options"));
  sections.push(`  ${cyan("--registry <url>")}   Registry API base URL`);
  sections.push(`  ${cyan("--json")}              Output results as JSON`);
  sections.push(`  ${cyan("--help")}              Show help for a command`);
  sections.push(`  ${cyan("--version")}           Show version number`);

  return sections.join("\n");
}

export async function createCli(): Promise<Command> {
  const program = new Command();
  const version = getPackageVersion();

  program
    .name("skillhub")
    .description("CLI for SkillHub — publish, search, and manage agent skills")
    .version(version)
    .option("--registry <url>", "Registry API base URL", "http://localhost:8080")
    .option("--json", "Output results as JSON");

  const customHelp = buildTopLevelHelp(version);
  const originalHelpInformation = program.helpInformation.bind(program);
  program.helpInformation = () => {
    if (program.parent) return originalHelpInformation();
    return customHelp;
  };

  const [
    { registerLogin },
    { registerLogout },
    { registerWhoami },
    { registerPublish },
    { registerNamespaces },
    { registerInstall },
    { registerDownload },
    { registerList },
    { registerStar },
    { registerInit },
    { registerMe },
    { registerReviews },
    { registerNotifications },
    { registerDelete },
    { registerVersions },
    { registerReport },
    { registerResolve },
    { registerRating, registerRate },
    { registerArchive },
    { registerUpdate },
    { registerCheck },
    { registerUninstall },
    { registerSync },
    { registerInspect },
    { registerExplore },
    { registerTransfer },
    { registerHide },
  ] = await Promise.all([
    import("./commands/login.js"),
    import("./commands/logout.js"),
    import("./commands/whoami.js"),
    import("./commands/publish.js"),
    import("./commands/namespaces.js"),
    import("./commands/install.js"),
    import("./commands/download.js"),
    import("./commands/list.js"),
    import("./commands/star.js"),
    import("./commands/init.js"),
    import("./commands/me.js"),
    import("./commands/reviews.js"),
    import("./commands/notifications.js"),
    import("./commands/delete.js"),
    import("./commands/versions.js"),
    import("./commands/report.js"),
    import("./commands/resolve.js"),
    import("./commands/rating.js"),
    import("./commands/archive.js"),
    import("./commands/update.js"),
    import("./commands/check.js"),
    import("./commands/uninstall.js"),
    import("./commands/sync.js"),
    import("./commands/inspect.js"),
    import("./commands/explore.js"),
    import("./commands/transfer.js"),
    import("./commands/hide.js"),
  ]);

  registerLogin(program);
  registerLogout(program);
  registerWhoami(program);
  registerPublish(program);
  registerNamespaces(program);
  registerInstall(program);
  registerDownload(program);
  registerList(program);
  registerStar(program);
  registerInit(program);
  registerMe(program);
  registerReviews(program);
  registerNotifications(program);
  registerDelete(program);
  registerVersions(program);
  registerReport(program);
  registerResolve(program);
  registerRating(program);
  registerRate(program);
  registerArchive(program);
  registerUpdate(program);
  registerCheck(program);
  registerUninstall(program);
  registerSync(program);
  registerInspect(program);
  registerExplore(program);
  registerTransfer(program);
  registerHide(program);

  return program;
}

export async function main() {
  const program = await createCli();
  program.parse();
}

main();
