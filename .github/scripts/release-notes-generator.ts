import { GitHubClient } from "./github.ts";
import { IssueLlmConfig } from "./issue-llm-types.ts";
import { requestOpenAiCompatibleMarkdown } from "./issue-llm-provider.ts";

interface RawCommit {
  sha: string;
  message: string;
  author: string;
  prNumber?: number;
  prTitle?: string;
  excluded: boolean;
}

interface ChangeEntry {
  type: string;
  scope: string;
  description: string;
  prNumber?: number;
  authors: string[];
  commits: string[];
}

export async function generateReleaseNotes(
  owner: string,
  repo: string,
  tag: string,
  prevTag: string,
  llmConfig: IssueLlmConfig | null,
  dryRun: boolean,
): Promise<string> {
  const github = new GitHubClient(Deno.env.get("GH_TOKEN") ?? "", owner, repo);

  console.log(`Collecting changes from ${prevTag} to ${tag}...`);
  const changes = await collectChanges(github, prevTag, tag);
  console.log(`Found ${changes.length} changes after deduplication`);

  if (llmConfig) {
    console.log("Generating release notes with LLM...");
    try {
      const markdown = await generateMarkdownWithLLM(
        llmConfig,
        changes,
        tag,
        prevTag,
        owner,
        repo,
      );
      return markdown;
    } catch (error) {
      console.error("LLM generation failed:", error);
      console.log("Falling back to conventional commit grouping...");
      return generateFallback(changes, tag, prevTag, owner, repo);
    }
  } else {
    console.log("Using fallback mode (conventional commit grouping)...");
    return generateFallback(changes, tag, prevTag, owner, repo);
  }
}

async function collectChanges(
  github: GitHubClient,
  prevTag: string,
  tag: string,
): Promise<ChangeEntry[]> {
  const gitLogCmd = new Deno.Command("git", {
    args: ["log", `${prevTag}..${tag}`, "--format=%H|%s|%an"],
    stdout: "piped",
  });
  const gitLogOutput = await gitLogCmd.output();
  const gitLogText = new TextDecoder().decode(gitLogOutput.stdout);

  const rawCommits: RawCommit[] = [];
  for (const line of gitLogText.trim().split("\n")) {
    if (!line) continue;
    const [sha, message, author] = line.split("|");
    rawCommits.push({
      sha,
      message,
      author,
      excluded: false,
    });
  }

  for (const commit of rawCommits) {
    if (/^Revert "(.+)"$/.test(commit.message)) {
      commit.excluded = true;
      const revertedMsg = commit.message.match(/^Revert "(.+)"$/)?.[1];
      if (revertedMsg) {
        const reverted = rawCommits.find((c) => c.message === revertedMsg);
        if (reverted) reverted.excluded = true;
      }
    }

    if (/^Merge (pull request|branch|remote-tracking)/.test(commit.message)) {
      commit.excluded = true;
    }
  }

  for (const commit of rawCommits.filter((c) => !c.excluded)) {
    try {
      const pulls = await github.listCommitPulls(commit.sha);
      if (pulls.length > 0) {
        commit.prNumber = pulls[0].number;
        commit.prTitle = pulls[0].title;
      }
    } catch (error) {
      console.warn(`Failed to fetch PR for commit ${commit.sha}:`, error);
    }
  }

  const prMap = new Map<number, ChangeEntry>();
  const standaloneCommits: ChangeEntry[] = [];

  for (const commit of rawCommits.filter((c) => !c.excluded)) {
    const parsed = parseConventionalCommit(commit.message);
    const description = commit.prTitle || parsed.description;

    if (commit.prNumber) {
      if (!prMap.has(commit.prNumber)) {
        prMap.set(commit.prNumber, {
          type: parsed.type,
          scope: parsed.scope,
          description,
          prNumber: commit.prNumber,
          authors: [commit.author],
          commits: [commit.sha],
        });
      } else {
        const entry = prMap.get(commit.prNumber)!;
        if (!entry.authors.includes(commit.author)) {
          entry.authors.push(commit.author);
        }
        entry.commits.push(commit.sha);
      }
    } else {
      standaloneCommits.push({
        type: parsed.type,
        scope: parsed.scope,
        description,
        authors: [commit.author],
        commits: [commit.sha],
      });
    }
  }

  return [...prMap.values(), ...standaloneCommits];
}

function parseConventionalCommit(message: string): {
  type: string;
  scope: string;
  description: string;
} {
  const match = message.match(/^(\w+)(?:\(([^)]+)\))?: (.+)$/);
  if (match) {
    return {
      type: match[1],
      scope: match[2] || "",
      description: match[3],
    };
  }
  return {
    type: "other",
    scope: "",
    description: message,
  };
}

async function generateMarkdownWithLLM(
  config: IssueLlmConfig,
  changes: ChangeEntry[],
  tag: string,
  prevTag: string,
  owner: string,
  repo: string,
): Promise<string> {
  const template = await Deno.readTextFile(".github/release-template.md");

  const systemPrompt = `You are a senior product manager and technical documentation expert. Rewrite the following technical change list into user-friendly Release Notes.

Requirements:
1. Strictly follow the Markdown structure and heading levels of the template below
2. Remove any section entirely (including its heading) if there are no items for it
3. Rewrite technical jargon into language that end-users can understand
4. For Breaking Changes, add an upgrade / migration guide
5. Highlights must contain 2-4 items, distilled from the most important changes
6. PR number format: #123
7. Contributor format: @username
8. Replace {{version}}, {{prev_tag}}, {{tag}} with actual values
9. Output ONLY the Markdown content — no extra commentary, no code fences

Template:
---
${template}
---`;

  const changesList = changes.map((c) => {
    const pr = c.prNumber ? ` (#${c.prNumber})` : "";
    const authors = c.authors.map((a) => `@${a}`).join(", ");
    return `- ${c.type}(${c.scope}): ${c.description}${pr} by ${authors}`;
  }).join("\n");

  const userPrompt = `Version: ${tag}
Previous version: ${prevTag}
Repository: ${owner}/${repo}

Change list:
${changesList}`;

  const markdown = await requestOpenAiCompatibleMarkdown(
    config,
    systemPrompt,
    userPrompt,
  );

  return markdown
    .replace(/\{\{version\}\}/g, tag)
    .replace(/\{\{prev_tag\}\}/g, prevTag)
    .replace(/\{\{tag\}\}/g, tag);
}

function generateFallback(
  changes: ChangeEntry[],
  tag: string,
  prevTag: string,
  owner: string,
  repo: string,
): string {
  const grouped = new Map<string, ChangeEntry[]>();
  for (const change of changes) {
    const type = change.type;
    if (!grouped.has(type)) {
      grouped.set(type, []);
    }
    grouped.get(type)!.push(change);
  }

  const typeLabels: Record<string, string> = {
    feat: "## ✨ Features",
    fix: "## 🐛 Bug Fixes",
    docs: "## 📚 Documentation",
    perf: "## ⚡ Performance",
    refactor: "## 🔧 Improvements",
    test: "## 🧪 Tests",
    chore: "## 🔧 Chore",
  };

  let md = `# SkillHub ${tag}\n\n`;
  md += `> [Auto-generated - LLM unavailable]\n\n`;

  for (const [type, items] of grouped.entries()) {
    const label = typeLabels[type] || `## ${type}`;
    md += `${label}\n\n`;
    for (const item of items) {
      const pr = item.prNumber ? ` in #${item.prNumber}` : "";
      const authors = item.authors.map((a) => `@${a}`).join(", ");
      md += `- ${item.description}${pr} by ${authors}\n`;
    }
    md += "\n";
  }

  const contributors = [
    ...new Set(changes.flatMap((c) => c.authors)),
  ];
  md += `## 👥 Contributors\n\n`;
  md += contributors.map((a) => `@${a}`).join(", ") + "\n\n";

  md += `**Full Changelog**: https://github.com/${owner}/${repo}/compare/${prevTag}...${tag}\n`;

  return md;
}
