# Govern an X/Twitter Workflow Skill

This recipe shows how a team can use SkillHub to distribute a reviewed
X/Twitter workflow skill while keeping execution in OpenClaw or another
compatible agent runtime.

SkillHub stores, scans, versions, and reviews the skill package. It does not
run TweetClaw actions or store Xquik credentials for the runtime.

## When to use this pattern

Use this pattern when a team wants a repeatable workflow for:

- search tweets and search tweet replies
- follower export and user lookup
- media workflow review
- tweet monitors and webhook handoff
- approval-reviewed post tweets, post tweet replies, direct messages, or
  giveaway draws

Use a private or namespace-only skill when the workflow includes internal
policy, customer context, or team-specific review steps.

## Package layout

Create a normal SkillHub skill package. Keep runtime credentials out of the
package and use placeholders in examples.

```text
tweetclaw-social-workflow/
├── SKILL.md
└── references/
    └── review-checklist.md
```

Example `SKILL.md`:

```md
---
name: tweetclaw-social-workflow
description: Review and run X/Twitter workflows with TweetClaw through OpenClaw.
---

# TweetClaw Social Workflow

Use this skill when the operator needs reviewed X/Twitter work through
TweetClaw and OpenClaw.

## Runtime requirements

- OpenClaw has the TweetClaw plugin installed:
  `openclaw plugins install @xquik/tweetclaw`
- The runtime has `XQUIK_API_KEY` configured outside the skill package.
- The operator approves every write, direct message, media upload, monitor,
  webhook, or giveaway draw before execution.

## Read-first flow

1. Start with search tweets, search tweet replies, follower export, or user
   lookup.
2. Summarize source tweet IDs, account IDs, URLs, and review notes.
3. Ask for approval before calling any TweetClaw write or account-changing
   action.
```

Example `references/review-checklist.md`:

```md
# Review Checklist

- No API keys, cookies, access tokens, or account credentials are present.
- Public examples use placeholder account IDs and tweet IDs.
- Read workflows are separated from write workflows.
- Post tweets, replies, DMs, media upload, monitors, webhooks, and giveaway
  draws require operator approval.
- The skill says that OpenClaw executes TweetClaw, not SkillHub.
```

## Publish to a namespace

Publish to a team namespace when the workflow needs a namespace administrator
to review the package before other users install it.

```bash
skillhub publish ./tweetclaw-social-workflow \
  --namespace social-ops \
  --visibility namespace-only
```

If you use the ClawHub compatibility path, keep the canonical slug aligned with
SkillHub's namespace mapping:

```bash
npx clawhub publish ./tweetclaw-social-workflow \
  --slug social-ops--tweetclaw-social-workflow \
  --version 1.0.0
```

SkillHub maps `social-ops--tweetclaw-social-workflow` to namespace
`social-ops` and skill slug `tweetclaw-social-workflow`.

## Review and scan

After publish, Skill Scanner checks the package and SkillHub creates a review
task when namespace review is enabled.

Reviewers should check:

- the Skill Scanner report
- the `SKILL.md` runtime requirements
- the review checklist
- that only placeholders appear in examples
- that write, DM, media, monitor, webhook, and giveaway workflows require
  approval

See [Security Scanning](/en/guide/scanner) and
[Review & Governance](/en/guide/review) for the full workflow.

## Install and run

Install the approved skill into an OpenClaw-compatible skill directory:

```bash
skillhub install tweetclaw-social-workflow \
  --namespace social-ops \
  --agent openclaw
```

Then configure the runtime outside SkillHub:

```bash
openclaw plugins install @xquik/tweetclaw
export XQUIK_API_KEY=REPLACE_WITH_RUNTIME_SECRET
```

The installed skill can now guide the operator through reviewed TweetClaw
workflows while SkillHub remains the governed distribution and audit layer.
