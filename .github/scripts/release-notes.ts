import { GitHubClient } from "./github.ts";
import { readReleaseNotesLlmConfig } from "./release-notes-config.ts";
import { generateReleaseNotes } from "./release-notes-generator.ts";

function readFlag(name: string): string | null {
  const index = Deno.args.indexOf(name);
  if (index === -1 || index === Deno.args.length - 1) {
    return null;
  }
  return Deno.args[index + 1];
}

function hasFlag(name: string): boolean {
  return Deno.args.includes(name);
}

async function detectPrevTag(tag: string): Promise<string> {
  const cmd = new Deno.Command("git", {
    args: ["tag", "--sort=-v:refname"],
    stdout: "piped",
  });
  const output = await cmd.output();
  const tags = new TextDecoder().decode(output.stdout).trim().split("\n");

  const currentIndex = tags.indexOf(tag);
  if (currentIndex === -1 || currentIndex === tags.length - 1) {
    throw new Error(`Cannot find previous tag for ${tag}`);
  }

  return tags[currentIndex + 1];
}

async function main() {
  const owner = readFlag("--owner");
  const repo = readFlag("--repo");
  const tag = readFlag("--tag");
  const prevTagArg = readFlag("--prev-tag");
  const dryRun = hasFlag("--dry-run");
  const skipLlm = hasFlag("--skip-llm");

  if (!owner || !repo || !tag) {
    console.error("Usage: release-notes.ts --owner <owner> --repo <repo> --tag <tag> [--prev-tag <prev-tag>] [--dry-run] [--skip-llm]");
    Deno.exit(1);
  }

  const prevTag = prevTagArg || await detectPrevTag(tag);
  console.log(`Generating release notes for ${tag} (previous: ${prevTag})`);

  const llmConfig = skipLlm ? null : readReleaseNotesLlmConfig();

  const markdown = await generateReleaseNotes(
    owner,
    repo,
    tag,
    prevTag,
    llmConfig,
    dryRun,
  );

  if (dryRun) {
    console.log("\n=== DRY RUN MODE ===\n");
    console.log(markdown);
    console.log("\n=== END DRY RUN ===");
    return;
  }

  console.log("Creating draft release...");
  const github = new GitHubClient(Deno.env.get("GH_TOKEN") ?? "", owner, repo);
  const release = await github.createDraftRelease(tag, tag, markdown);
  console.log(`Draft release created: ${release.id}`);

  console.log(`\nDraft release created successfully!`);
  console.log(`View at: https://github.com/${owner}/${repo}/releases/tag/${tag}`);
}

main().catch((error) => {
  console.error("Error:", error);
  Deno.exit(1);
});
