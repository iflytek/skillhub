# Upstream notice

- Upstream project: `manavmishra/ZeroSlop`
- Repository: <https://github.com/manavmishra/ZeroSlop>
- Source: <https://github.com/manavmishra/ZeroSlop/tree/f936fbaf7f162073299ed5f9bc1c536a2ba29caa>
- Fixed revision: `f936fbaf7f162073299ed5f9bc1c536a2ba29caa`
- Original Skill version: `2.10.2`
- License: MIT; see `LICENSE.txt`

## SkillHub modifications

SkillHub adaptation version: `2.10.2`.

- Reduced the upstream multi-surface distribution to one offline Skill workflow.
- Retained the standard-library scorer, reviewed pattern data, deterministic fidelity check, and
  the references needed for tell interpretation, genre handling, and over-correction avoidance.
- Removed hosted MCP/REST, npm CLI, update checking, calibration, automatic learning, and
  maintainer-only release tooling from the package.
- Removed the interactive GitHub-star note and its local run-counter write.
- Disabled automatic loading of the private learned-pattern overlay; a named voice profile is read
  only when explicitly selected.
- Shortened the instructions around inspect, rewrite, and embedded-gate modes while preserving
  fidelity, non-authorship, disclosure, untrusted-input, and format-preservation boundaries.

Zero Slop and its contributors do not endorse this modified distribution.
