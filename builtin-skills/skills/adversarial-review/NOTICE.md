# Upstream notice

- Upstream project: `RevolutionLA/adversarial-review`
- Source: <https://github.com/RevolutionLA/adversarial-review/tree/0d007a223a5b0f3a1e09c12d891614683c90956c/skills/adversarial-review>
- Fixed revision: `0d007a223a5b0f3a1e09c12d891614683c90956c`
- License: MIT; see `LICENSE.txt`

## SkillHub modifications

SkillHub adaptation version: `2.5.1`.

- Added the SkillHub package-contract top-level `version` field (upstream carries it as `metadata.version`).
- Declared the Node.js requirement for the report checker in `compatibility`.
- Removed the authoring-only reference `references/skill-spec.md` and its pointer; it is not needed at runtime.
- Removed the upstream install scripts (`install.sh`, `install.ps1`, `verify-install.sh`); curated packages are installed through SkillHub, so global-install commands are outside this package's scope.
- Kept the runtime machine-checker `scripts/check-report.mjs` and both example reviews, which the workflow hard-requires.

The upstream author (RevolutionLA) contributed and adapted this package personally; provenance and terms permit redistribution under MIT.
