# Content Safety Policy

Last updated: August 18, 2026

## Purpose and scope

SkillHub accepts, stores, reviews, and distributes agent skill packages. Packages
can contain instructions, scripts, documentation, examples, images, and other files
that influence an AI agent or execute on a user's computer. Profiles, namespace
descriptions, reviews, reports, ratings, and release notes also contain user-supplied
content.

This document describes the project's content-safety expectations, available
technical and governance controls, and the responsibilities of people who publish,
review, operate, and install skills.

The SkillHub maintainers do not operate or moderate every independently hosted
instance. Each operator must assess its users, jurisdiction, deployment model, and
risk; publish enforceable rules and a reporting channel; configure appropriate
controls; and staff its own review, appeal, and emergency processes.

## Baseline rules

SkillHub instances should not knowingly publish or distribute content or packages
that:

- violate applicable law or another person's intellectual-property, privacy, or
  other rights;
- sexually exploit or endanger children, including child sexual abuse material;
- credibly threaten, harass, or promote violence or hateful abuse against people;
- expose personal, confidential, or authentication data without authorization;
- contain malware, credential theft, destructive payloads, unauthorized access,
  persistence, evasion, or instructions intended to defeat security controls;
- impersonate people or organizations, facilitate fraud, or intentionally present
  deceptive or materially misleading claims;
- secretly collect, transmit, or use data beyond the skill's documented purpose;
- conceal important external services, downloads, commands, permissions, or side
  effects from reviewers and users; or
- bypass an instance's review, scanning, namespace, visibility, or access-control
  rules.

Context matters. Legitimate security research, education, documentation, and
defensive automation can discuss or test risky behavior without promoting harm.
Reviewers should consider purpose, provenance, permissions, likely impact, and
applicable law instead of relying on keywords alone.

## Content and package risks

A skill may instruct an agent to read or modify files, run commands, call external
services, install dependencies, browse websites, or handle sensitive inputs.
Documentation and examples may be inaccurate or omit important consequences.
Images and archives may contain hidden payloads. Ratings or social signals do not
prove that a package is safe, lawful, accurate, or suitable for a particular use.

Publishers must accurately describe required permissions, external recipients,
dependencies, expected side effects, supported environments, and known limitations.
Installers must review package contents and apply least privilege before execution.

## Available technical and governance controls

SkillHub includes controls that an operator can combine according to its risk:

- package limits and file-type validation, including size, file-count, extension,
  and selected file-signature checks;
- a configurable security scanner that can inspect uploaded packages and produce
  findings for reviewers;
- namespace and platform review workflows with approve, reject, withdraw, and
  promotion decisions;
- user reports and administrator actions such as hiding, archiving, rejecting, or
  yanking content and versions;
- platform and namespace RBAC for publishing and governance actions;
- public, namespace-only, and private visibility; and
- audit records and notifications for relevant governance activity.

The implementation and operating guidance are documented in the
[scanner guide](security-scanning.md),
[review guide](skillhub/en/guide/review.md), and
[security architecture](../document/docs/04-developer/architecture/security.md).

## Important limitations

These controls reduce risk but do not certify a package as safe or compliant:

- scanner operation is configurable, and an operator can run SkillHub without an
  enabled scanner;
- optional LLM-backed analysis depends on the service selected by the operator and
  can create additional privacy and reliability risks;
- static, behavioral, metadata, and model-based analysis can produce false
  positives and false negatives;
- a successfully scanned package can still be misleading, vulnerable, or
  malicious in context, or unsuitable for a specific environment;
- privileged publication paths require operator governance and periodic review;
  and
- independently hosted instances can configure different review, visibility, and
  enforcement practices.

Operators must disclose which controls are active. A package that fails scanning
or review should remain unavailable for ordinary installation until the failure
is resolved through a documented process. Scanner failure must not be treated as
proof that a package is safe.

## Publisher responsibilities

Before submitting a skill, a publisher should:

- inspect every included file and remove secrets, personal data, build artifacts,
  and unrelated binaries;
- document commands, network destinations, external downloads, required
  permissions, and persistent changes;
- pin or constrain dependencies where practical and preserve their license notices;
- provide evidence for security, compliance, or standards claims instead of relying
  on labels alone;
- test failure and rollback behavior in an isolated environment;
- avoid manipulative instructions designed to override system, user, or operator
  safety controls; and
- update or withdraw a package when a material risk is discovered.

## Operator safeguards

Before opening an instance to publishers or installers, an operator should:

- define permitted and prohibited content, reviewers, escalation owners, and
  emergency contacts;
- enable scanning and human review appropriate to the instance's exposure and
  package risk;
- restrict direct-publish and governance roles, log their use, and review them
  regularly;
- isolate scanning and package inspection from production secrets and sensitive
  networks;
- rate-limit uploads, downloads, reports, and automated activity;
- preserve only the evidence needed for review and protect reporter identities;
- provide a visible reporting channel and an impartial appeal route; and
- train reviewers to handle malware, privacy, child-safety, fraud, and
  intellectual-property reports safely.

## Reporting

For a skill or profile visible in a SkillHub instance, use that instance's report
feature or contact the operator identified in its published policies. Include the
package coordinate and version, the reason for concern, the time observed, and the
minimum context needed to investigate. Do not execute a suspected malicious package
or resend illegal, exploitative, personal, or confidential material through an
unprotected channel.

For abusive or harassing conduct on SkillHub project-managed community surfaces,
report privately to
[ifly_opensource@iflytek.com](mailto:ifly_opensource@iflytek.com) under the
[Code of Conduct](../CODE_OF_CONDUCT.md). Report upstream security vulnerabilities
privately to [security@iflytek.com](mailto:security@iflytek.com) under the
[iFLYTEK organization security policy](https://github.com/iflytek/.github/blob/main/SECURITY.md).
Do not disclose vulnerabilities or personal data in a public issue.

## Review, action, and notice

An operator's documented process should:

1. triage imminent danger, child-safety concerns, credible malware, exposed
   credentials, and active security incidents for urgent specialist handling;
2. preserve only the evidence needed for a proportionate review;
3. assess the package, context, applicable rule, law, provenance, permissions, and
   likely user impact;
4. take proportionate action, such as rejecting a version, hiding or yanking a
   package, restricting an account, revoking a token, or escalating to an authorized
   specialist;
5. record the rule, evidence, and rationale and notify affected people when lawful
   and safe; and
6. provide an appeal route and use confirmed incidents to improve controls.

SkillHub's review guide recommends completing routine package reviews within 24
hours to avoid blocking publishers. That recommendation is not a historical average
for safety reports and is not an emergency-response guarantee. The upstream project
does not yet have enough comparable safety reports to publish a meaningful average
assessment or action time. Each operator must publish targets appropriate to its
risk, staffing, and legal obligations, with an urgent path for imminent harm and
child safety.

## Appeals

A publisher, reporter, account holder, or other person materially affected by a
governance decision should be able to request review through the instance
operator's private channel. The request should identify the original decision and
give a reason for review, such as significant new evidence, a material procedural
error, a conflict of interest, or a clearly disproportionate action.

Appeals should be handled by a person who did not make the original decision and
has no conflict of interest. The reviewer may uphold, modify, or reverse the action,
or require a new investigation. Temporary protective measures may remain in place
while needed to protect people, systems, evidence, or legal obligations.

For Code of Conduct decisions on project-managed community surfaces, send an appeal
to [ifly_opensource@iflytek.com](mailto:ifly_opensource@iflytek.com) with the
subject `SkillHub Code of Conduct appeal`. Include the original case reference,
the outcome being challenged, and the reason for review. Appeal information must
be limited to people who need it. Retaliation for a good-faith report or appeal
is prohibited.

## Children and young people

SkillHub is a general-purpose developer and enterprise collaboration tool, not a
child-directed service. An operator that permits use by children or processes their
data must perform an age-appropriate risk assessment, use any legally required
parental or guardian consent, minimize collection and profiling, restrict contact
and high-risk package capabilities, provide child-accessible notices and reporting,
and route serious concerns to trained personnel and appropriate authorities.

If those protections cannot be provided, the instance should not be offered to
children. The project Code of Conduct separately protects community participation
from harassment regardless of age.

## Privacy and policy review

Package inspection, reports, audit logs, and investigations can expose sensitive
information. They must follow the
[Privacy and Data Governance Policy](PRIVACY_AND_DATA_GOVERNANCE.md) and the
instance's own privacy notice and retention schedule.

Material changes to this policy are made through the repository's public review
process. Operators should periodically test their controls, review incident trends,
and update their policy when the product, threat model, law, or operating context
changes.
