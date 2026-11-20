# Third-Party Notice

Skill: `event-planner` 1.0.0

- upstream.repository: https://github.com/iflytek/skillhub
- upstream.commit: 0000000000000000000000000000000000000000
- upstream.path: builtin-skills/skills/event-planner
- copyright: Copyright 2026 SkillHub contributors
- license: Apache-2.0

This package is maintained inside the SkillHub repository. There is no separate third-party
upstream project: the text, references, examples and validation script were written for this
repository, and the copyright holder is the same project.

`upstream.commit` records the commit that introduced this package's current content (see the
pinned coordinate in `builtin-skills/catalog.json`). Contributor submissions must replace the
placeholder above with the real 40-character commit SHA before opening a pull request.

## SkillHub modifications

- Added `references/timeline-anchors.md` (countdown anchors by event scale and the rationale that
  makes them load-bearing), `references/quantities-and-materials.md` (headcount-driven quantity
  drivers), `references/budget-and-overruns.md` (ranges, overrun classes, contract traps) and
  `references/emergency-sheet.md` (failure classes with first signal, owner and cost cap).
- Added `examples/sample-plan.md`, a complete 40-person internal event plan.
- Added `scripts/check-event-plan.py`, which enforces one owner and a definition of done per
  timeline line, a driver per quantity, budget ranges with contingency, and an emergency sheet
  with cost caps. It fails closed on unparseable input.
- Deliberately excluded invitation sending, venue booking, payment collection, and any claim about
  occupancy limits, licensing, insurance or vendor availability.

## License

Licensed under the Apache License, Version 2.0. A copy is included as `LICENSE.txt`.
