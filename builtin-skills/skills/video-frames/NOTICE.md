# Upstream notice

- Upstream project: `openclaw/openclaw`
- Source:
  <https://github.com/openclaw/openclaw/tree/62cbbcc800214f05cdc4b97debdf7339bfa7c5f4/skills/video-frames>
- Fixed revision: `62cbbcc800214f05cdc4b97debdf7339bfa7c5f4`
- Upstream copyright: Copyright (c) 2026 OpenClaw Foundation
- Original skill version: not declared in the upstream `SKILL.md`
- License: MIT; see `LICENSE.txt`

## SkillHub modifications

SkillHub adaptation version: `1.0.0`.

- Added explicit version and SPDX license metadata.
- Removed OpenClaw-specific host and installation metadata and replaced `{baseDir}` examples with portable relative paths.
- Added validation that `--index` is a non-negative integer and rejected simultaneous `--index` and `--time`.
- Added missing-value and FFmpeg availability checks.
- Replaced unconditional overwrite behavior with no-clobber checks and FFmpeg's `-n` option.

OpenClaw and its contributors do not endorse this modified distribution.
