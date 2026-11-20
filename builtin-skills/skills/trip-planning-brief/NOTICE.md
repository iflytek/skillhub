# Third-Party Notice

Skill: `trip-planning-brief` 1.0.0

- upstream.repository: https://github.com/iflytek/skillhub
- upstream.commit: 0000000000000000000000000000000000000000
- upstream.path: builtin-skills/skills/trip-planning-brief
- copyright: Copyright 2026 SkillHub contributors
- license: Apache-2.0

This package is maintained inside the SkillHub repository. There is no separate third-party
upstream project: the text, references, examples and validation script were written for this
repository, and the copyright holder is the same project.

`upstream.commit` records the commit that introduced this package's current content (see the
pinned coordinate in `builtin-skills/catalog.json`). Contributor submissions must replace the
placeholder above with the real 40-character commit SHA before opening a pull request.

## SkillHub modifications

- Added `references/intake-checklist.md`, `references/pace-and-radius.md`,
  `references/budget-model.md` and `references/risk-and-fallbacks.md` as the operating contract
  for intake, pacing, cost ranging and failure handling.
- Added `examples/sample-brief.md`, a complete worked output used as the shape reference.
- Added `scripts/check-itinerary.py`, a structure linter (stdlib only) that fails closed:
  unparseable documents are failures, not skips.
- Deliberately excluded booking, payment, calendar writes, message sending and any
  account-bound action. The skill produces a document and stops.

## License

Licensed under the Apache License, Version 2.0. A copy is included as `LICENSE.txt`.
