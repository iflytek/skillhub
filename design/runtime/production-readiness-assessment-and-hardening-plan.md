# Production Readiness Assessment And Hardening Plan

## Scope

This document captures the current production-readiness assessment for SkillHub and turns the findings into a concrete hardening plan.

It is intentionally focused on engineering readiness rather than feature ideation.

As of 2026-05-04:

- the project is no longer at prototype stage
- the main runtime path is already usable
- but the repository still falls short of a fully hardened production-grade delivery standard

The goal of this document is to answer three questions:

1. what still blocks a strong production-grade claim
2. what must be completed before broader production rollout
3. what can be deferred until after initial controlled deployment

## Current Assessment

### Summary Judgment

Current status is best described as:

- suitable for controlled internal production trial
- not yet suitable for being treated as a fully hardened, low-touch production platform

That means:

- core product capability is already strong
- the runtime path is mostly coherent
- the main remaining gaps are engineering consistency, verification depth, and operational guardrails

### Approximate Readiness Score

| Dimension | Score | Notes |
|---|---:|---|
| Architecture | 7.5 / 10 | Main module split and runtime direction are broadly sound |
| Code quality | 7 / 10 | Maintainable overall, but some capability-model mismatches remain |
| Runtime consistency | 6 / 10 | Main path exists, but startup/documentation/entrypoint consistency is not fully closed |
| Security and authorization | 7 / 10 | RBAC and sensitive-path protection are meaningful, but still need operational tightening |
| Tests and regression guardrails | 6 / 10 | Many tests exist, but current verify gates do not fully reflect main runtime risk |
| Operations and delivery | 5.5 / 10 | The weakest area; reproducible official startup and release behavior still need consolidation |
| Documentation trustworthiness | 6 / 10 | Improving, but not yet a single fully reliable source of operational truth |

Overall assessment:

- approximately `6.4 / 10`

## What Has Improved

The project is in meaningfully better shape than a typical evolving internal platform because several structural concerns have already been addressed.

### 1. Standard runtime direction has converged on the current MySQL path

The current default path is already aligned around:

- `MySQL 8`
- `local-file-index`
- runtime state selectable through one provider axis

This is a major improvement over a repository that still claims one runtime while operating another.

### 2. Search provider boundaries are clearer

The provider model now has a stronger shape:

- query
- index
- rebuild

These responsibilities are more explicitly grouped under the selected provider instead of being only partially switched.

### 3. Local search startup synchronization has been improved

The repository now has a startup synchronization mechanism for `local-file-index`:

- missing index can trigger rebuild
- uninitialized index can trigger rebuild
- corrupted index can trigger rebuild
- `rebuild-on-startup=true` can force rebuild

This closes a real correctness gap where authoritative MySQL data existed while local Lucene search still returned empty results.

### 4. UASS local verification path is now clearer and more repeatable

The local mock browser flow can now be described concretely:

- local login page on `3000`
- mock third-party page on `3001`
- callback back into the main app

That is a stronger local verification story than an undocumented or half-manual auth path.

## What Still Remains Problematic

### 1. Official startup entrypoint is still not fully consolidated

This is currently the highest-value issue to fix.

The repository documentation still references commands such as:

- `make dev-all`
- `make dev-server-restart`

But the current checkout does not actually contain the documented top-level `Makefile`.

Consequences:

- new developers cannot trust the docs as-is
- operational reproducibility depends on tribal knowledge
- "correct runtime" may still be achieved by ad hoc shell command composition rather than a stable repository-owned entrypoint

### 2. Verification gates do not yet match the current standard runtime risk surface

There are many tests, but the current build verification remains overly shaped by earlier feature-scope coverage logic.

This means the repository can still give a false sense of safety:

- passing `verify` does not necessarily imply confidence in the current `MySQL + local-file-index` main path
- coverage and runtime correctness are not aligned tightly enough

### 3. Runtime truth is still split across multiple sources

There is progress, but not full closure.

At the moment, runtime truth is spread across:

- `application.yml`
- profile YAML files
- ad hoc environment variable combinations
- README guidance
- development workflow guidance
- design/runtime notes

If these are not fully aligned, operational drift returns quickly.

### 4. Authorization model and management model are not perfectly aligned

RBAC itself is meaningful and richer than a single-role toy system.

But some management operations still behave like a single-role administration model, even though the domain and platform semantics are closer to multi-role reality.

This is not necessarily a pre-production blocker, but it is a governance maturity gap.

### 5. Repository operational maturity is still too dependent on active maintainers

A production-grade platform should be reasonably operable by someone who did not participate in the recent migration steps.

SkillHub is not fully there yet.

The project still relies too much on:

- recent context
- migration knowledge
- manual runtime composition
- nuanced provider understanding

That is acceptable for a controlled pilot, but not ideal for broader operational handoff.

## Pre-Production Must-Fix Items

These items should be completed before claiming strong production-grade readiness.

### 1. Establish one official repository-owned startup entrypoint

Required outcome:

- a single supported local and staging startup path exists in-repo
- commands in docs map directly to files/targets that actually exist

Minimum expectations:

- start
- stop
- status
- logs

Recommended supported combinations:

- `MySQL + Redis + local-file-index`
- `MySQL + memory + local-file-index`
- `MySQL + memory + mysql-like`

### 2. Make documentation and runtime behavior fully consistent

The following must agree:

- main README
- developer workflow doc
- local quickstart
- runtime configuration reference
- actual repository scripts or entrypoints

The repository should not ask operators to guess which source is the current truth.

### 3. Upgrade regression gates to reflect the standard runtime

The main verification path must prove the current core runtime, not only a historical feature subset.

Minimum required signals:

- MySQL runtime boot
- local-file-index search correctness
- one protected auth flow
- one governance/review flow
- one management/admin flow

### 4. Freeze and publish the standard production runtime combination

A production system should have one canonical runtime combination.

Recommended standard:

- database: `MySQL 8`
- runtime state: `Redis`
- search provider: `local-file-index`

Everything else should be clearly labeled as:

- fallback
- local-only
- troubleshooting-only

### 5. Define a real release validation checklist

Before broader production rollout, the project should maintain a fixed checklist covering at least:

- startup and health
- login
- publish
- search
- review or promotion
- authorization enforcement
- session/logout behavior

This checklist must be executable and repeatable.

## Post-Production Early Iteration Items

These should still be improved, but they do not have to block an initial controlled rollout.

### 1. Multi-role administration ergonomics

If the product truly intends to support users holding multiple platform roles in normal operation, the management UI and service semantics should align with that.

### 2. Stronger search observability

Future improvements can include:

- rebuild duration metrics
- rebuild counters
- provider switch audit events
- index health markers

### 3. Further runtime automation

Examples:

- clearer start scripts
- environment profiles for common local/staging combinations
- less manual environment assembly

### 4. Continued documentation convergence

The repository has already improved here, but continued cleanup is still beneficial to reduce stale guidance and historical noise.

## Acceptable To Keep

These items can remain as long as they are clearly documented.

### 1. `mysql-like`

This remains valuable as an explicit fallback and debugging provider, provided it is not misrepresented as the standard production path.

### 2. Historical design materials

Historical documents may remain as long as they are clearly marked and do not compete with current operational guidance.

## Two-Week Hardening Plan

If the team wants to move from current readiness to a stronger "production-capable internal platform" posture within roughly two weeks, this is the recommended order.

### Week 1

1. restore or replace the documented startup entrypoint with repository-owned scripts or targets
2. align README, workflow docs, and runtime docs with actual startup behavior
3. freeze the official standard runtime combination and label all fallback modes clearly
4. ensure search startup synchronization and provider behavior are observable in logs and docs

### Week 2

1. tighten build verification around the actual standard runtime
2. formalize release validation checklist
3. verify one end-to-end governance path against the standard runtime
4. review whether role management semantics need product clarification before wider rollout

## Operational Rules If Deployed Now

If the system must be deployed before all hardening work is complete, the following rules are required.

### 1. Only use one official production combination

Recommended:

- `MySQL + Redis + local-file-index`

Do not mix fallback or local-only runtime modes into production operation.

### 2. Treat fallback providers as troubleshooting tools, not standard operation

In particular:

- `mysql-like`
- memory runtime state
- mock UASS

must not become long-lived production defaults.

### 3. Always validate search after startup

Do not stop at `actuator/health`.

At minimum verify:

- one known published skill exists in database
- the same skill is searchable from the active provider path

### 4. Treat runtime configuration as code

No manual shell drift should be tolerated for real production-like environments.

Configuration should come from:

- checked-in deployment descriptors
- stable env files
- or the deployment platform

### 5. Keep production docs synchronized with release behavior

Operational docs are part of the release artifact.

If the real runtime behavior changes, documentation must be updated in the same change window.

## Final Recommendation

SkillHub should not be described today as a fully hardened, mature production platform with complete operational guardrails.

It is better described as:

- a feature-rich internal platform
- with a viable standard runtime path
- that still needs one focused engineering-hardening cycle before broader production confidence is justified

That hardening cycle is realistic and relatively bounded.

The main remaining work is not a large architectural rewrite.
It is consistency work:

- startup entrypoints
- verification gates
- runtime truth alignment
- operational discipline
