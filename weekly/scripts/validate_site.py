#!/usr/bin/env python3
"""Validate built routes and basic accessibility hooks for the weekly site."""

from __future__ import annotations

import argparse
import json
import sys
from html.parser import HTMLParser
from pathlib import Path


VOID_ELEMENTS = {
    "area",
    "base",
    "br",
    "col",
    "embed",
    "hr",
    "img",
    "input",
    "link",
    "meta",
    "param",
    "source",
    "track",
    "wbr",
}
NON_CONTENT_ELEMENTS = {"caption", "h1", "h2", "h3", "h4", "h5", "h6", "th"}
ALLOWED_PANELS = {"panel-overview", "panel-health", "panel-flow", "panel-method"}


class PageParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.h1_count = 0
        self.tabs = 0
        self.panels = 0
        self.tab_controls: set[str] = set()
        self.panel_ids: set[str] = set()
        self.panel_modules: dict[str, int] = {}
        self.current_panel: str | None = None
        self.panel_depth = 0
        self.module_stack: list[dict[str, object]] = []
        self.module_counts: dict[str, int] = {}
        self.module_panels: dict[str, set[str]] = {}
        self.module_names: set[str] = set()
        self.empty_modules: set[str] = set()
        self.table_stack: list[dict[str, int]] = []
        self.empty_table_count = 0
        self.ids: set[str] = set()
        self.duplicate_ids: set[str] = set()
        self.external_assets: list[str] = []
        self.non_content_depth = 0

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        is_void = tag in VOID_ELEMENTS
        if self.current_panel is not None and not is_void:
            self.panel_depth += 1
        if not is_void:
            for module in self.module_stack:
                module["depth"] = int(module["depth"]) + 1
            for table in self.table_stack:
                table["depth"] += 1
        if tag in NON_CONTENT_ELEMENTS:
            self.non_content_depth += 1
        values = dict(attrs)
        element_id = values.get("id")
        if element_id:
            if element_id in self.ids:
                self.duplicate_ids.add(element_id)
            self.ids.add(element_id)
        if tag == "h1":
            self.h1_count += 1
        if values.get("role") == "tab":
            self.tabs += 1
            controls = values.get("aria-controls")
            if controls:
                self.tab_controls.add(controls)
        if values.get("role") == "tabpanel":
            self.panels += 1
            panel_id = values.get("id") or ""
            if panel_id:
                self.panel_ids.add(panel_id)
                self.panel_modules.setdefault(panel_id, 0)
            self.current_panel = panel_id
            self.panel_depth = 1
        if tag == "table":
            self.table_stack.append({"depth": 1, "data_cells": 0})
        elif tag == "td":
            for table in self.table_stack:
                table["data_cells"] += 1
        module_name = values.get("data-module")
        if module_name:
            self.module_names.add(module_name)
            self.module_counts[module_name] = self.module_counts.get(module_name, 0) + 1
            if self.current_panel:
                self.module_panels.setdefault(module_name, set()).add(self.current_panel)
            if is_void:
                self.empty_modules.add(module_name)
            else:
                self.module_stack.append(
                    {"depth": 1, "name": module_name, "has_meaningful_content": False}
                )
            if self.current_panel:
                self.panel_modules[self.current_panel] = self.panel_modules.get(self.current_panel, 0) + 1
        if tag == "script" and values.get("src"):
            self.external_assets.append(values["src"] or "")
        if tag == "link" and "stylesheet" in (values.get("rel") or ""):
            self.external_assets.append(values.get("href") or "")

    def handle_startendtag(
        self, tag: str, attrs: list[tuple[str, str | None]]
    ) -> None:
        self.handle_starttag(tag, attrs)

    def handle_data(self, data: str) -> None:
        if data.strip() and not self.non_content_depth:
            for module in self.module_stack:
                module["has_meaningful_content"] = True

    def handle_endtag(self, tag: str) -> None:
        if tag in NON_CONTENT_ELEMENTS and self.non_content_depth:
            self.non_content_depth -= 1
        for module in self.module_stack:
            module["depth"] = int(module["depth"]) - 1
        while self.module_stack and int(self.module_stack[-1]["depth"]) == 0:
            module = self.module_stack.pop()
            if not module["has_meaningful_content"]:
                self.empty_modules.add(str(module["name"]))
        for table in self.table_stack:
            table["depth"] -= 1
        while self.table_stack and self.table_stack[-1]["depth"] == 0:
            table = self.table_stack.pop()
            if table["data_cells"] == 0:
                self.empty_table_count += 1
        if self.current_panel is not None:
            self.panel_depth -= 1
            if self.panel_depth == 0:
                self.current_panel = None


def validate(root: Path) -> list[str]:
    errors: list[str] = []
    required = (root / "index.html", root / "archive.html", root / ".nojekyll")
    for path in required:
        if not path.exists():
            errors.append(f"missing built route: {path}")

    manifest_path = root / "reports.json"
    if not manifest_path.is_file():
        errors.append(f"missing manifest: {manifest_path}")
        return errors

    payload = json.loads(manifest_path.read_text(encoding="utf-8"))
    latest = payload.get("latest")
    report_paths: list[Path] = []
    for report in payload.get("reports", []):
        report_file = root / str(report.get("path", "")) / "index.html"
        if not report_file.is_file():
            errors.append(f"missing report: {report_file}")
        else:
            report_paths.append(report_file)
    if not latest:
        errors.append("manifest latest is empty")

    archive_path = root / "archive.html"
    if archive_path.is_file():
        archive_source = archive_path.read_text(encoding="utf-8")
        archive_parser = PageParser()
        archive_parser.feed(archive_source)
        if archive_parser.h1_count != 1:
            errors.append(
                f"{archive_path}: expected one h1, found {archive_parser.h1_count}"
            )
        if 'data-site-theme="notion-light"' not in archive_source:
            errors.append(f"{archive_path}: missing notion-light theme marker")
        report_link_count = archive_source.count('class="report-link"')
        if report_link_count != len(payload.get("reports", [])):
            errors.append(
                f"{archive_path}: expected one archive link per report, "
                f"found {report_link_count}"
            )
        if archive_parser.external_assets:
            errors.append(
                f"{archive_path}: external assets are not allowed: "
                f"{archive_parser.external_assets}"
            )

    pages_to_validate = [root / "index.html", *report_paths]
    for index_path in pages_to_validate:
        if not index_path.is_file():
            continue
        parser = PageParser()
        parser.feed(index_path.read_text(encoding="utf-8"))
        if parser.h1_count != 1:
            errors.append(f"{index_path}: expected one h1, found {parser.h1_count}")
        if not 2 <= parser.tabs <= 4 or parser.tabs != parser.panels:
            errors.append(
                f"{index_path}: expected two to four matching report tabs/panels, "
                f"found {parser.tabs}/{parser.panels}"
            )
        if parser.tab_controls != parser.panel_ids:
            errors.append(f"{index_path}: tab aria-controls values do not match panel ids")
        if not {"panel-overview", "panel-method"}.issubset(parser.panel_ids):
            errors.append(f"{index_path}: overview and data panels are required")
        unexpected_panels = sorted(parser.panel_ids - ALLOWED_PANELS)
        if unexpected_panels:
            errors.append(f"{index_path}: unexpected panels: {unexpected_panels}")
        if "repository-summary" not in parser.module_names:
            errors.append(f"{index_path}: missing required repository-summary module")
        elif parser.module_panels.get("repository-summary") != {"panel-overview"}:
            errors.append(
                f"{index_path}: repository-summary must appear in panel-overview"
            )
        duplicate_modules = sorted(
            name for name, count in parser.module_counts.items() if count > 1
        )
        if duplicate_modules:
            errors.append(f"{index_path}: duplicate module names: {duplicate_modules}")
        if parser.empty_modules:
            errors.append(
                f"{index_path}: modules without meaningful content: "
                f"{sorted(parser.empty_modules)}"
            )
        if parser.empty_table_count:
            errors.append(
                f"{index_path}: empty tables without data cells: "
                f"{parser.empty_table_count}"
            )
        if parser.module_stack:
            errors.append(f"{index_path}: unclosed data-module element")
        if parser.table_stack:
            errors.append(f"{index_path}: unclosed table element")
        empty_panels = sorted(
            panel_id for panel_id, module_count in parser.panel_modules.items() if module_count == 0
        )
        if empty_panels:
            errors.append(f"{index_path}: panels without modules: {empty_panels}")
        if parser.duplicate_ids:
            errors.append(f"{index_path}: duplicate ids: {sorted(parser.duplicate_ids)}")
        if parser.external_assets:
            errors.append(f"{index_path}: external assets are not allowed: {parser.external_assets}")
        if "{{" in index_path.read_text(encoding="utf-8"):
            errors.append(f"{index_path}: unresolved template placeholder")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("site", type=Path, nargs="?", default=Path("_site"))
    args = parser.parse_args()
    errors = validate(args.site.resolve())
    for error in errors:
        print(f"ERROR: {error}")
    if errors:
        print(f"FAIL: {len(errors)} error(s)")
        return 1
    print(f"PASS: {args.site.resolve()}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
