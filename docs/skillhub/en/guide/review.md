# Review & Governance

SkillHub review workflows ensure that published skill packages meet quality, permission, and governance expectations.

## Two Review Layers

- Namespace review: handled by team administrators inside a namespace
- Platform governance review: handled by platform-level administrators for promotions, reports, and broader governance actions

## Common States

| State | Meaning |
|-------|---------|
| `PENDING` | Waiting for review |
| `APPROVED` | Accepted |
| `REJECTED` | Rejected |
| `WITHDRAWN` | Withdrawn by the submitter |

## Typical Flow

1. A developer publishes a skill package
2. The system triggers scanning and review
3. A reviewer checks package details, files, and governance context
4. The reviewer approves or rejects it
5. The submitter either continues distribution or fixes issues and resubmits

## What Reviewers Should Check

- Whether metadata is complete
- Whether the package content matches the description
- Whether there are obvious security or privilege issues
- Whether the package fits namespace policy
- Whether the submitter needs actionable feedback

## Advice for Submitters

- Perform basic local verification before publishing
- If rejected, address the review feedback before resubmitting
- If you notice a problem yourself, withdraw early instead of waiting for a reviewer

## Continue Reading

- [Skill Publishing & Versioning](/en/guide/skill-publish)
- [Security Scanning](/en/guide/scanner)
- [Namespace & Team Management](/en/guide/namespace)
