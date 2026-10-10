import { GitHubClient } from "./github.ts";
import { readIssueLlmConfig, shouldUseLlm } from "./issue-llm-config.ts";
import { evaluateIssueWithLlm } from "./issue-llm-evaluator.ts";
import { TRIAGE_MANUAL_OVERRIDE_LABEL } from "./issue-triage-config.ts";
import {
  analyzeIssue,
  buildManagedLabels,
  ensureManagedLabels,
  findTriageComment,
  parseTriageMachineState,
  previewTriageMutation,
  syncManagedLabels,
  upsertTriageComment,
} from "./issue-triage-lib.ts";
import { mergeRuleAndLlm } from "./issue-triage-merge.ts";

function readFlag(name: string) {
  const index = Deno.args.indexOf(`--${name}`);
  return index >= 0 ? Deno.args[index + 1] : undefined;
}

function hasFlag(name: string) {
  return Deno.args.includes(`--${name}`);
}

const owner = readFlag("owner");
const repo = readFlag("repo");
const limitValue = readFlag("limit") ?? "0";
const dryRun = hasFlag("dry-run");
const token = Deno.env.get("GH_TOKEN") ?? Deno.env.get("GITHUB_TOKEN");

if (!owner || !repo || !token) {
  throw new Error(
    "Usage: deno run issue-backlog-rescore.ts --owner <owner> --repo <repo> [--limit 0 for all] with GH_TOKEN set.",
  );
}

const limit = Number.parseInt(limitValue, 10);

if (Number.isNaN(limit) || limit < 0) {
  throw new Error(`Invalid limit: ${limitValue}`);
}

const client = new GitHubClient(token, owner, repo);
if (!dryRun) {
  await ensureManagedLabels(client);
}
const llmConfig = readIssueLlmConfig();

const issues = await client.listOpenIssuesByLabel("triage/deferred", limit);
const dryRunResults: Array<Record<string, unknown>> = [];

for (const issue of issues) {
  if (
    issue.labels.some((label) => label.name === TRIAGE_MANUAL_OVERRIDE_LABEL)
  ) {
    console.log(
      `Skipping #${issue.number} because ${TRIAGE_MANUAL_OVERRIDE_LABEL} is set.`,
    );
    continue;
  }

  const comments = await client.listIssueComments(issue.number);
  const ruleResult = analyzeIssue(issue, comments);
  const existingComment = findTriageComment(comments);
  const previousState = existingComment
    ? parseTriageMachineState(existingComment.body)
    : null;
  let result = ruleResult;

  if (llmConfig) {
    const llmDecision = shouldUseLlm(issue, ruleResult);

    if (llmDecision.use) {
      const { inputHash, assessment } = await evaluateIssueWithLlm(
        llmConfig,
        issue,
        comments,
        ruleResult,
        previousState,
      );

      result = mergeRuleAndLlm({
        ...ruleResult,
        inputHash,
        llm: assessment,
        mode: assessment.mode === "assist" ? "llm-assist" : "llm-shadow",
      });
    }
  }

  if (dryRun) {
    const preview = previewTriageMutation(result, comments);
    dryRunResults.push({
      issue: issue.number,
      mode: result.mode,
      route: result.route,
      priority: result.priority,
      effort: result.effort,
      confidence: result.confidence,
      riskLevel: result.riskLevel,
      labels: preview.labels,
      commentAction: preview.existingComment ? "update" : "create",
      commentBody: preview.commentBody,
    });
    continue;
  }

  await syncManagedLabels(client, issue, result);
  await upsertTriageComment(client, issue.number, result, comments);

  console.log(
    JSON.stringify(
      {
        issue: issue.number,
        route: result.route,
        priority: result.priority,
        labels: buildManagedLabels(issue, result),
      },
      null,
      2,
    ),
  );
}

if (dryRun) {
  console.log(JSON.stringify({ dryRun: true, issues: dryRunResults }, null, 2));
}
