#!/usr/bin/env python3
"""Inline the canonical Notion-light theme into self-contained weekly reports."""

from __future__ import annotations

import argparse
import re
from pathlib import Path


THEME_PATTERN = re.compile(
    r'(?P<open><style data-report-theme="notion-light">\n)'
    r".*?"
    r"(?P<close>\n[ \t]*</style>)",
    flags=re.DOTALL,
)


def sync_theme(theme_path: Path, report_paths: list[Path]) -> None:
    theme = theme_path.read_text(encoding="utf-8").rstrip()
    for report_path in report_paths:
        source = report_path.read_text(encoding="utf-8")
        updated, replacements = THEME_PATTERN.subn(
            lambda match: f"{match.group('open')}{theme}{match.group('close')}",
            source,
        )
        if replacements != 1:
            raise ValueError(
                f"{report_path}: expected one notion-light theme block, "
                f"found {replacements}"
            )
        report_path.write_text(updated, encoding="utf-8")
        print(f"Synced theme: {report_path}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "reports",
        type=Path,
        nargs="+",
        help="HTML report files containing a notion-light theme block",
    )
    parser.add_argument(
        "--theme",
        type=Path,
        default=Path("assets/notion-light.css"),
        help="canonical CSS file",
    )
    args = parser.parse_args()
    sync_theme(args.theme.resolve(), [path.resolve() for path in args.reports])


if __name__ == "__main__":
    main()
