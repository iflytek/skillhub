# SkillHub documentation site

## Webpack version override

`package.json` pins `overrides.webpack` to `5.94.0` so the Docusaurus 3.9.x build stays compatible with webpack’s `ProgressPlugin` schema expectations. Revisit this pin when upgrading Docusaurus or webpack, then remove or bump the override if the toolchain no longer needs it.
