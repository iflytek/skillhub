# Third-Party Notice

Skill: `packing-checklist` 1.0.0

- upstream.repository: https://github.com/iflytek/skillhub
- upstream.commit: 0000000000000000000000000000000000000000
- upstream.path: builtin-skills/skills/packing-checklist
- copyright: Copyright 2026 SkillHub contributors
- license: Apache-2.0

This package is maintained inside the SkillHub repository. There is no separate third-party
upstream project: the text, references, examples and validation script were written for this
repository, and the copyright holder is the same project.

`upstream.commit` records the commit that introduced this package's current content (see the
pinned coordinate in `builtin-skills/catalog.json`). Contributor submissions must replace the
placeholder above with the real 40-character commit SHA before opening a pull request.

## SkillHub modifications

- Added `references/weather-to-layers.md` (temperature bands to layer arithmetic),
  `references/activity-gear.md` (activity to footwear/gear with volume trades),
  `references/quantity-rules.md` (quantity formulas and the one-spare rule) and
  `references/regulations.md` (what to verify and with whom, with no numeric limits quoted).
- Added `examples/sample-list.md`, a complete carry-on list used as the shape reference.
- Added `scripts/check-packing-list.py`, a structure linter that checks quantity formulas, weight
  vocabulary and conditional triggers, and fails closed on unparseable input.
- Deliberately excluded shopping, brand recommendation, purchase links, and any statement of a
  liquid, battery, customs or medical limit as current fact.

## License

Licensed under the Apache License, Version 2.0. A copy is included as `LICENSE.txt`.
