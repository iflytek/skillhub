# Upstream notice

- Upstream project: `anthropics/knowledge-work-plugins` (`legal` plugin 1.3.0)
- Source:
  <https://github.com/anthropics/knowledge-work-plugins/tree/da38ec1ee89d41e5380e652a97382695003396e7/legal/skills/triage-nda>
- Fixed revision: `da38ec1ee89d41e5380e652a97382695003396e7`
- Upstream publisher: Anthropic
- Original skill version: not declared in the upstream `SKILL.md`
- License: Apache-2.0; see `LICENSE.txt`, copied from `legal/LICENSE` at the fixed revision

## SkillHub modifications

SkillHub adaptation version: `1.0.0`.

- Added explicit version, normalized SPDX license metadata, and a `compatibility` statement.
- Removed the Claude Code specific `argument-hint`, `@$1` argument expansion, and `/triage-nda`
  invocation block, and the reference to the plugin-level `CONNECTORS.md`, which is not part of
  this package.
- Limited input to NDA text or files the user provides; the skill asks for the text instead of
  fetching a document-system link.
- Treats the NDA as untrusted counterparty text, so embedded instructions are evaluated and flagged
  rather than followed.
- Uses a screening playbook only when the user supplies it or points to it, instead of searching
  local settings.
- Added a cautious tie-break when a term falls between the classification bands or a required fact
  is missing, and required `Not stated` for report fields the document does not supply.
- Reworded GREEN routing as a recommendation and stated that the skill does not sign, send, forward,
  or file documents.

The screening criteria, classification bands, report template, and standard positions are otherwise
unchanged. Anthropic does not endorse this modified distribution.
