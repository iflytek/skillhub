# Upstream notice

- Upstream project: `sensedeal/cue-skills`
- Repository: <https://github.com/sensedeal/cue-skills>
- Source: <https://github.com/sensedeal/cue-skills/tree/475c249f5d966dd9a4aba02d8af16b90e33ad1fe/cue-omni-reader>
- Fixed revision: `475c249f5d966dd9a4aba02d8af16b90e33ad1fe`
- Original Skill version: `0.5.0`
- License: MIT; see `LICENSE.txt`

## SkillHub modifications

SkillHub adaptation version: `1.0.0`.

- Retained the URL/local-source parse workflow, asynchronous operation recovery, complete artifact
  consumption, minimum-root authorization, credential, billing, and cleanup boundaries.
- Kept the audited `@cueai/omni-reader-mcp@1.8.0` Bridge pin and removed the per-session npm
  `latest` probe and upgrade path. Bridge upgrades require review and a new SkillHub package.
- Reduced upstream maintenance material to the runtime instructions needed by an Agent; omitted
  historical verification reports, synchronization scripts, and test tooling.
- Added explicit treatment of parsed content as untrusted input and prohibited public temporary
  uploads of local files.

Cue Omni Reader and its contributors do not endorse this modified distribution.
