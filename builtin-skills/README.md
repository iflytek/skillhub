# Built-in Skills

This directory contains the reviewed source used to build SkillHub's official starter Skill
packages. Each child of `skills/` is a complete package; generated ZIP files are release artifacts
and are not committed.

The reviewed source collection contains general-purpose Skills covering study, office work,
personal productivity, content creation, weather, media, external tools, and frontend design.
Every package includes:

- a `SKILL.md` adapted for SkillHub;
- `LICENSE.txt` and `NOTICE.md` with pinned upstream provenance;
- only the scripts and references required at runtime.

Build and verify the packages with:

```bash
make build-builtin-skills
make test-builtin-skills
```

The build writes deterministic, uncompressed ZIPs and `artifacts.json` to
`builtin-skills/dist/`. The artifact index records each ZIP's SHA-256 for the release step; runtime
manifest integration is maintained separately from the reviewed source collection. A package is
added to the runtime manifest only after its immutable CDN URL is available; the manifest records
the matching SHA-256 so the backend can reject changed or incorrectly uploaded bytes before
extraction.

Published packages are pinned in the runtime manifest. A clean deployment initializes those
packages alongside the existing built-in Skills in the public `@global` namespace. Newly
reviewed source packages remain outside the runtime manifest until their immutable CDN artifact
and matching SHA-256 are available.

## Share a Skill with the Community

A Skill shared with the community may be considered for the curated starter collection.
To protect contributors and users, it should:

- solve a clear, recurring task and add useful coverage to the starter collection;
- identify its author, source, and terms that permit redistribution;
- declare required tools, network access, credentials, and supported environments;
- avoid hidden downloads, embedded secrets, and unconfirmed destructive or external actions;
- pass package validation, security review, and at least one realistic usage test.

You can start by
[opening an issue](https://github.com/iflytek/skillhub/issues/new/choose) with the source
URL and the problem the Skill solves. A complete pull request should:

1. add the reviewed package under `builtin-skills/skills/<slug>/`, including `SKILL.md`,
   `LICENSE.txt`, and `NOTICE.md`;
2. record the pinned upstream commit and provenance in `catalog.json`;
3. add a realistic regression case to `evals.json`;
4. run `make test-builtin-skills`.

Do not copy an upstream Skill into this directory without reviewing every bundled file and
confirming that its license permits redistribution.
