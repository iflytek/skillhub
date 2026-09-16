# Source notice

- Source project: `iflytek/skillhub`
- Source repository: <https://github.com/iflytek/skillhub>
- Fixed revision: `42a0e423f4ac01e5e7e0801c786735cdd4a818cf`
- Source path: `web/src/docs/skill.md`
- License: Apache-2.0; see `LICENSE.txt`

## SkillHub modifications

SkillHub adaptation version: `2.0.2`.

- Created a dedicated first-party CLI Skill instead of changing the existing ClawHub-oriented `skillhub-registry` Skill.
- Separated anonymous bootstrap guidance from the persistent Agent installation while keeping one instruction body.
- Added CLI identity checks to avoid invoking an unrelated executable with the same name.
- Added live-help verification and a reviewed operations reference for sync, publish, removal, repair, and troubleshooting.
- Added POSIX and PowerShell 7 credential-entry guidance without placing tokens in command history.
- Preserved exact registry, coordinate, version, Agent target, authentication, and integrity boundaries.
- Removed automatic public-registry fallback for exact installs and private discovery queries.
- Required read-only launcher provenance checks and exact user confirmation before package-manager removal.
