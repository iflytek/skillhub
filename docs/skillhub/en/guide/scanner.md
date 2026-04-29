# Security Scanning

SkillHub can automatically trigger security scanning after a skill package is published, helping teams detect risks before packages are widely adopted.

## What Users See

- A package may enter a scanning stage immediately after publishing
- Scan results appear on the skill detail page
- Reviewers can use scan results as part of their decision process

## Typical Uses

- Detecting malicious code or suspicious behavior
- Flagging sensitive data leakage
- Helping reviewers focus on higher-risk packages first

## Facts Worth Knowing

- Scanning is typically asynchronous
- Whether scanning is enabled depends on the deployment
- Different environments may enable different scan engines
- Scan results support governance, but do not replace human judgment

## Advice for Publishers

- Remove irrelevant files and test leftovers before publishing
- Avoid packaging credentials, secrets, or debug artifacts
- If a scan fails or flags a high risk, inspect the package contents before retrying

## Advice for Reviewers

- Look at high-severity findings first
- Combine scan output with file browsing and review context
- Separate false positives from real issues and keep that knowledge reusable

## Continue Reading

- [Skill Publishing & Versioning](/en/guide/skill-publish)
- [Review & Governance](/en/guide/review)
