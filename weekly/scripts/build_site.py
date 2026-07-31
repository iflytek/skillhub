#!/usr/bin/env python3
"""Build the SkillHub weekly report site from self-contained report files."""

from __future__ import annotations

import argparse
import json
import shutil
from html import escape
from pathlib import Path


def load_manifest(source: Path) -> tuple[str, list[dict[str, str]]]:
    manifest_path = source / "reports.json"
    payload = json.loads(manifest_path.read_text(encoding="utf-8"))
    latest = payload.get("latest")
    reports = payload.get("reports")
    if not isinstance(latest, str) or not latest:
        raise ValueError("reports.json must define a non-empty latest week")
    if not isinstance(reports, list) or not reports:
        raise ValueError("reports.json must contain at least one report")

    required = {"week", "title", "period", "snapshot", "path"}
    normalized: list[dict[str, str]] = []
    for index, report in enumerate(reports):
        if not isinstance(report, dict) or not required.issubset(report):
            missing = required - set(report) if isinstance(report, dict) else required
            raise ValueError(f"report #{index + 1} is missing fields: {sorted(missing)}")
        normalized.append({key: str(report[key]) for key in required})

    weeks = {report["week"] for report in normalized}
    if latest not in weeks:
        raise ValueError(f"latest week {latest!r} is not present in reports")
    return latest, sorted(normalized, key=lambda item: item["week"], reverse=True)


def render_archive(latest: str, reports: list[dict[str, str]]) -> str:
    rows = "\n".join(
        f"""        <li>
          <a class="report-link" href="./{escape(report['path'], quote=True)}">
            <span class="report-copy">
              <span class="report-kicker">
                <span>{escape(report['week'])}</span>
                {'<span class="latest">最新</span>' if report['week'] == latest else ''}
              </span>
              <strong>{escape(report['title'])}</strong>
              <span class="report-meta">{escape(report['period'])} · 快照 {escape(report['snapshot'])}</span>
            </span>
            <span class="arrow" aria-hidden="true">→</span>
          </a>
        </li>"""
        for report in reports
    )
    return f"""<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <title>SkillHub 开源周报归档</title>
  <style data-site-theme="notion-light">
    :root {{
      color-scheme: light;
      --page:#fff;
      --warm:#f6f5f4;
      --ink:#0d0d0d;
      --ink-soft:#31302e;
      --muted:#615d59;
      --faint:#76716c;
      --line:#e5e3e1;
      --blue:#0075de;
      --blue-active:#005bab;
      --green:#147a33;
      --green-soft:#e9f7ec;
      --focus:#097fe8;
    }}
    * {{ box-sizing: border-box; }}
    body {{
      margin:0;
      background:var(--page);
      color:var(--ink);
      font:15px/1.65 Inter,-apple-system,BlinkMacSystemFont,"Segoe UI","PingFang SC","Hiragino Sans GB","Microsoft YaHei",sans-serif;
      -webkit-font-smoothing:antialiased;
    }}
    a {{ color:var(--blue); text-decoration:none; text-underline-offset:3px; }}
    a:hover {{ color:var(--blue-active); }}
    :focus-visible {{ outline:2px solid var(--focus); outline-offset:3px; }}
    main {{ width:min(920px,100%); min-height:100vh; margin:0 auto; padding:44px 28px 56px; }}
    .topline {{ display:flex; align-items:center; justify-content:space-between; gap:18px; margin-bottom:44px; }}
    .brand {{ display:flex; align-items:center; gap:10px; color:var(--ink); }}
    .brand-mark {{
      display:inline-flex;
      width:34px;
      height:34px;
      align-items:center;
      justify-content:center;
      border-radius:6px;
      background:var(--ink-soft);
      color:#fff;
      font-size:14px;
      font-weight:700;
      letter-spacing:.04em;
    }}
    .brand-name {{ font-size:17px; font-weight:700; letter-spacing:-.01em; }}
    .brand-tag {{ padding:4px 10px; border-radius:999px; background:#f1f0ef; color:var(--muted); font-size:12px; font-weight:600; }}
    .utility {{ display:flex; flex-wrap:wrap; gap:16px; font-size:13px; }}
    h1 {{ margin:0; font-size:clamp(32px,5vw,44px); line-height:1.15; letter-spacing:-.025em; }}
    .intro {{ margin:9px 0 30px; color:var(--muted); }}
    .archive-summary {{ margin-bottom:18px; padding:18px 20px; border-radius:8px; background:var(--warm); color:var(--ink-soft); }}
    .archive-summary strong {{ color:var(--ink); }}
    ul {{ margin:0; padding:0; overflow:hidden; border:1px solid var(--line); border-radius:12px; list-style:none; }}
    li + li {{ border-top:1px solid var(--line); }}
    .report-link {{ display:flex; align-items:center; justify-content:space-between; gap:24px; padding:20px 22px; color:var(--ink); }}
    .report-link:hover {{ background:#faf9f8; text-decoration:none; }}
    .report-copy {{ display:flex; min-width:0; flex-direction:column; gap:4px; }}
    .report-kicker {{ display:flex; align-items:center; gap:8px; color:var(--faint); font-size:12px; font-weight:600; }}
    .report-copy strong {{ color:var(--ink); font-size:17px; line-height:1.4; }}
    .report-meta {{ color:var(--muted); font-size:12.5px; }}
    .latest {{ padding:2px 8px; border-radius:999px; background:var(--green-soft); color:var(--green); font-size:11px; font-weight:700; }}
    .arrow {{ flex:0 0 auto; color:var(--faint); font-size:20px; transition:transform .15s ease; }}
    .report-link:hover .arrow {{ transform:translateX(3px); color:var(--ink); }}
    .back {{ display:inline-block; margin-top:24px; font-size:13px; font-weight:600; }}
    @media (max-width:600px) {{
      main {{ padding:28px 18px 40px; }}
      .topline {{ align-items:flex-start; flex-direction:column; gap:12px; margin-bottom:34px; }}
      .report-link {{ align-items:flex-start; padding:18px; }}
      .report-copy strong {{ font-size:15px; }}
    }}
    @media (prefers-reduced-motion:reduce) {{ .arrow {{ transition:none; }} }}
  </style>
</head>
<body>
  <main>
    <div class="topline">
      <div class="brand">
        <span class="brand-mark" aria-hidden="true">SH</span>
        <span class="brand-name">SkillHub</span>
        <span class="brand-tag">开源周报</span>
      </div>
      <nav class="utility" aria-label="站点链接">
        <a href="https://iflytek.github.io/skillhub/">项目文档</a>
        <a href="https://github.com/iflytek/skillhub">GitHub 仓库</a>
      </nav>
    </div>
    <h1>SkillHub 开源周报归档</h1>
    <p class="intro">按统计周期倒序查看历期开源周报。</p>
    <div class="archive-summary">当前共收录 <strong>{len(reports)} 期</strong>，最新一期为 <strong>{escape(latest)}</strong>。</div>
    <ul>
{rows}
    </ul>
    <a class="back" href="./">返回最新周报</a>
  </main>
</body>
</html>
"""


def build(source: Path, output: Path) -> None:
    latest, reports = load_manifest(source)
    latest_report = next(report for report in reports if report["week"] == latest)
    latest_source = source / latest_report["path"] / "index.html"
    if not latest_source.is_file():
        raise FileNotFoundError(f"latest report not found: {latest_source}")

    for report in reports:
        report_file = source / report["path"] / "index.html"
        if not report_file.is_file():
            raise FileNotFoundError(f"report not found: {report_file}")

    if output.exists():
        shutil.rmtree(output)
    shutil.copytree(source, output)

    latest_html = latest_source.read_text(encoding="utf-8")
    latest_html = latest_html.replace('href="../../archive.html"', 'href="./archive.html"')
    (output / "index.html").write_text(latest_html, encoding="utf-8")
    (output / "archive.html").write_text(
        render_archive(latest, reports),
        encoding="utf-8",
    )
    (output / ".nojekyll").write_text("", encoding="utf-8")
    print(f"Built {len(reports)} report(s); latest={latest}; output={output}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=Path("site"))
    parser.add_argument("--output", type=Path, default=Path("_site"))
    args = parser.parse_args()
    build(args.source.resolve(), args.output.resolve())


if __name__ == "__main__":
    main()
