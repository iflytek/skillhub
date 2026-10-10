# SkillHub Weekly Mirror

This directory is the reviewed mirror of the public
[`XiaoSeS/skillhub-weekly`](https://github.com/XiaoSeS/skillhub-weekly) site.
The standalone repository remains the authoritative content source.

The SkillHub documentation workflow builds this directory and places the result
under the existing VitePress Pages artifact:

- Latest report: `https://iflytek.github.io/skillhub/weekly/`
- Archive: `https://iflytek.github.io/skillhub/weekly/archive.html`
- Report: `https://iflytek.github.io/skillhub/weekly/reports/<week>/`

## Local validation

```bash
cd weekly
python3 scripts/sync_report_theme.py site/reports/*/index.html
python3 scripts/build_site.py
python3 scripts/validate_site.py _site
```

Do not edit `_site/`; it is ignored and rebuilt by CI. Update reports in the
standalone repository first, then copy `site/`, `assets/`, and `scripts/`
byte-for-byte into this directory so both published sites keep the same report
HTML, Notion-light theme, charts, and Tab behavior.
