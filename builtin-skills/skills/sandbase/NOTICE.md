# Upstream notice

- Upstream project: `sandbaseai/cli`
- Repository: <https://github.com/sandbaseai/cli>
- Source:
  <https://github.com/sandbaseai/cli/tree/99a2f8102ce67f82080f67862d8ea81b87b37203/skills/sandbase>
- Fixed revision: `99a2f8102ce67f82080f67862d8ea81b87b37203`
- Original Skill version: `0.1.17`
- License: Apache-2.0; see `LICENSE.txt`

## SkillHub modifications

SkillHub adaptation version: `0.1.17`.

- Added explicit SPDX license metadata while retaining the upstream name and version.
- Removed host-specific invocation metadata and kept the Skill scoped to orchestration of the
  six SandBase MCP tools.
- Made installer download, local MCP configuration, and browser authentication require prior
  user approval.
- Replaced example prices with a requirement to inspect current schema and pricing.
- Added explicit confirmation, privacy-minimization, untrusted-content, charged-retry, and
  asynchronous polling boundaries.
- Removed promotional examples and generic catalog lists that did not change agent decisions.

SandBase and its contributors do not endorse this modified distribution.
