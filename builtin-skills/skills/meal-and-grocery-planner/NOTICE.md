# Third-Party Notice

Skill: `meal-and-grocery-planner` 1.0.0

- upstream.repository: https://github.com/iflytek/skillhub
- upstream.commit: 0000000000000000000000000000000000000000
- upstream.path: builtin-skills/skills/meal-and-grocery-planner
- copyright: Copyright 2026 SkillHub contributors
- license: Apache-2.0

This package is maintained inside the SkillHub repository. There is no separate third-party
upstream project: the text, references, examples and validation script were written for this
repository, and the copyright holder is the same project.

`upstream.commit` records the commit that introduced this package's current content (see the
pinned coordinate in `builtin-skills/catalog.json`). Contributor submissions must replace the
placeholder above with the real 40-character commit SHA before opening a pull request.

## SkillHub modifications

- Added `references/menu-structures.md` (rotations by equipment and time budget, plus equipment
  conflict rules), `references/grocery-aggregation.md` (unit normalisation, summing, store order),
  `references/substitutions.md` (swaps for unstated limits and what each swap costs) and
  `references/prep-timeline.md` (backwards clock, parallel slots, holding practice).
- Added `examples/sample-week.md`, a complete five-dinner week for two people with one shop.
- Added `scripts/check-meal-plan.py`, which catches duplicate ingredients in the aggregated
  grocery list, missing units, equipment gaps and nutrition/medical claims, and fails closed on
  unparseable input.
- Deliberately excluded ordering and delivery, calorie or macro targets, any medical claim, any
  assurance that a dish is "safe for" someone with an allergy, and any quoted food-safety
  temperature or storage duration (those are `[verify]` with the local authority).

## License

Licensed under the Apache License, Version 2.0. A copy is included as `LICENSE.txt`.
