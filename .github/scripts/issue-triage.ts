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
const issueNumberValue = readFlag("issue-number");
const dryRun = hasFlag("dry-run");
const token = Deno.env.get("GH_TOKEN") ?? Deno.env.get("GITHUB_TOKEN");

if (!owner || !repo || !issueNumberValue || !token) {
  throw new Error(
    "Usage: deno run issue-triage.ts --owner <owner> --repo <repo> --issue-number <number> with GH_TOKEN set.",
  );
}

const issueNumber = Number.parseInt(issueNumberValue, 10);

if (Number.isNaN(issueNumber)) {
  throw new Error(`Invalid issue number: ${issueNumberValue}`);
}

const client = new GitHubClient(token, owner, repo);
const issue = await client.getIssue(issueNumber);

if (issue.pull_request) {
  console.log(`Skipping #${issue.number} because it is a pull request conversation.`);
  Deno.exit(0);
}

if (issue.labels.some((label) => label.name === TRIAGE_MANUAL_OVERRIDE_LABEL)) {
  console.log(`Skipping #${issue.number} because ${TRIAGE_MANUAL_OVERRIDE_LABEL} is set.`);
  Deno.exit(0);
}

const comments = await client.listIssueComments(issueNumber);
const ruleResult = analyzeIssue(issue, comments);
const existingComment = findTriageComment(comments);
const previousState = existingComment
  ? parseTriageMachineState(existingComment.body)
  : null;
const llmConfig = readIssueLlmConfig();
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
  console.log(
    JSON.stringify(
      {
        dryRun: true,
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
      },
      null,
      2,
    ),
  );
  Deno.exit(0);
}

await ensureManagedLabels(client);
await syncManagedLabels(client, issue, result);
await upsertTriageComment(client, issueNumber, result, comments);

console.log(
  JSON.stringify(
    {
      issue: issue.number,
      route: result.route,
      priority: result.priority,
      effort: result.effort,
      confidence: result.confidence,
      riskLevel: result.riskLevel,
      labels: buildManagedLabels(issue, result),
    },
    null,
    2,
  ),
);
